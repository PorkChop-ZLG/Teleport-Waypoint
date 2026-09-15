package com.zonlong.teleportwaypoint.structure;

import com.zonlong.teleportwaypoint.block.ModBlocks;
import com.zonlong.teleportwaypoint.block.WaypointBlock;
import com.zonlong.teleportwaypoint.block.entity.WaypointBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Writes an injected waypoint into the world.
 *
 * <p>Reached only from {@code ChunkEvent.Load}, on the server main thread.
 *
 * <h2>Why this writes through {@link LevelChunk}, not through {@code Level#setBlock}</h2>
 * {@code Level#setBlock} does not just write a block: it derives neighbour work, and that neighbour
 * work synchronously reads (and sometimes writes) blocks in <em>adjacent</em> chunks. There are two
 * such derived paths and they are gated by <em>different</em> flag bits, which is why picking update
 * flags cannot close this off:
 * <ul>
 *   <li>the redstone path, gated on {@code flags & 1} ({@code UPDATE_NEIGHBORS});</li>
 *   <li>the shape cascade, gated on {@code (flags & 16) == 0} ({@code UPDATE_KNOWN_SHAPE}).</li>
 * </ul>
 * Both {@code UPDATE_ALL} and {@code UPDATE_CLIENTS} lack bit 16, and {@code Level#markAndNotifyBlock}
 * masks the flags with {@code & -34}, so the shape cascade runs for either value. A block on a chunk
 * edge reaches into the neighbour chunk, and a chunk that is still generating only guarantees its
 * horizontal neighbours are at {@code INITIALIZE_LIGHT} (the {@code FULL} step of
 * {@code ChunkPyramid.GENERATION_PYRAMID} adds no requirement of its own and inherits the
 * {@code LIGHT} step's {@code addRequirement(INITIALIZE_LIGHT, 1)}). For a neighbour that is not yet
 * FULL the read lands in {@code ServerChunkCache#getChunk}'s {@code mainThreadProcessor.managedBlock},
 * which waits for work that only the main thread can do -- and the main thread is this call. The
 * server then freezes silently: no exception (the check that would throw sits after the blocking
 * wait), no log line, and even the watchdog is frozen with it.
 *
 * <p>{@code LevelChunk#setBlockState} is the engine's own world-generation write path (see
 * {@code WorldGenRegion#setBlock}) and performs the section write, the four heightmaps, light
 * queueing and block entity creation and registration itself, while deriving <em>no</em> neighbour
 * work at all. Its only {@code Level} interaction is {@code onBlockStateChange} for the same position.
 * Taking the {@code Level} write API out of scope is what makes this structural rather than a matter
 * of choosing flags carefully.
 *
 * <p>Registering the block entity is deliberately left to the existing {@code onLoad()} path: the new
 * block entity is queued into {@code Level#addFreshBlockEntities} and picked up on the next tick by
 * {@code Level#tickBlockEntities}, exactly like any other block entity. That is what lets the existing
 * registration, sync, rendering and map integration work untouched.
 *
 * <p>Stateless.
 */
public final class StructureWaypointPlacer {

    private StructureWaypointPlacer() {
    }

    /**
     * Places the waypoint block and assigns its {@code waypoint_id}.
     *
     * <p>The {@link ServerLevel} argument exists only to forward the single
     * {@code onBlockStateChange} notification that {@code Level#setBlock} would have issued. It is
     * deliberately narrowed to {@code ServerLevel} and is never used for block access: the whole point
     * of this class is that the {@code Level} write and read APIs ({@code setBlock},
     * {@code getBlockState}, {@code getChunkAt}, {@code getChunk}, {@code getFluidState},
     * {@code getBlockEntity}) stay out of scope here.
     *
     * @param structureId used for debug output only; may be null
     * @return true when the block was placed and its id assigned
     */
    public static boolean place(ServerLevel level, LevelChunk chunk, BlockPos pos, String waypointId, ResourceLocation structureId) {
        // Last-metre guard: this is the only place that actually mutates the world, so re-check that
        // the target column belongs to the chunk the event handed us.
        if ((pos.getX() >> 4) != chunk.getPos().x || (pos.getZ() >> 4) != chunk.getPos().z) {
            StructureWaypointDebug.debug(
                    "structure waypoint: refusing out-of-chunk placement pos={} chunk={}", pos, chunk.getPos());
            return false;
        }

        // Re-assert the scanner's preconditions immediately before the write. Cheap, and it keeps this
        // method safe on its own rather than relying on the caller.
        BlockState existing = chunk.getBlockState(pos);
        if (existing.hasBlockEntity()
                || !(existing.isAir() || existing.canBeReplaced()
                        || existing.getFluidState().is(Fluids.WATER))) {
            StructureWaypointDebug.debug(
                    "structure waypoint: refusing placement, target not replaceable pos={} state={}", pos, existing);
            return false;
        }

        BlockState state = ModBlocks.WAYPOINT.get().defaultBlockState();

        // The block state defaults to waterlogged=false, which would leave an air pocket underwater.
        // Read the fluid from the chunk, never from the level. Only water is accepted: lava was already
        // excluded during scanning, and a waypoint placed in lava would just burn away.
        FluidState fluid = existing.getFluidState();
        if (fluid.getType() == Fluids.WATER) {
            state = state.setValue(WaypointBlock.WATERLOGGED, true);
        }

        // Write straight into the chunk we already hold. Note the `false` argument is `isMoving`, not a
        // flag set: LevelChunk#setBlockState takes no update flags, which is exactly why it derives no
        // neighbour work. WorldGenRegion#setBlock calls it the same way.
        BlockState previous = chunk.setBlockState(pos, state, false);
        if (previous == null) {
            // Null means the section was left unchanged, so nothing was placed.
            return false;
        }
        // Mirror the one worthwhile side effect of Level#setBlock. Safe: ServerLevel#onBlockStateChange
        // only compares POI types and defers any work through getServer().execute(...).
        level.onBlockStateChange(pos, previous, state);

        // Safe to look up: setBlockState just created and registered the block entity into
        // chunk.blockEntities, so this lookup hits that map before it can reach the pending-NBT
        // promotion path. (The pending map is populated from the ProtoChunk and is not cleared until
        // postProcessGeneration, so it is NOT empty at this point -- the hit order is the real reason.)
        if (!(chunk.getBlockEntity(pos) instanceof WaypointBlockEntity waypoint)) {
            StructureWaypointDebug.debug("structure waypoint: no block entity after placement pos={}", pos);
            return false;
        }

        // Writes only the id, never the uid: getUid() lazily generates a unique value later.
        waypoint.setId(waypointId);
        StructureWaypointDebug.placed(structureId, pos, state.getValue(WaypointBlock.WATERLOGGED));
        return true;
    }
}
