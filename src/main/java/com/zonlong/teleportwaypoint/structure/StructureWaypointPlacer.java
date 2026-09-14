package com.zonlong.teleportwaypoint.structure;

import com.zonlong.teleportwaypoint.block.ModBlocks;
import com.zonlong.teleportwaypoint.block.WaypointBlock;
import com.zonlong.teleportwaypoint.block.entity.WaypointBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Writes an injected waypoint into the world.
 *
 * <p>Reached only from {@code ChunkEvent.Load}, on the server main thread. The block write goes
 * through {@link ServerLevel#setBlock}, which resolves the chunk via {@code getChunkAt}. That first
 * lookup is safe here because the event fires while the chunk holder's {@code currentlyLoading} field
 * still points at the very chunk being loaded, so {@code ServerChunkCache#getChunk} short-circuits
 * before it can block on the main thread's own mailbox.
 *
 * <p>The update flags are the dangerous part, not the write itself. See the comment on the
 * {@code setBlock} call: neighbour updates would read blocks in adjacent chunks that are not
 * guaranteed to be loaded, which deadlocks the main thread against itself.
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

        BlockState state = ModBlocks.WAYPOINT.get().defaultBlockState();

        // The block state defaults to waterlogged=false, which would leave an air pocket underwater.
        // Read the fluid from the chunk, never from the level. Only water is accepted: lava was already
        // excluded during scanning, and a waypoint placed in lava would just burn away.
        FluidState fluid = chunk.getBlockState(pos).getFluidState();
        if (fluid.getType() == Fluids.WATER) {
            state = state.setValue(WaypointBlock.WATERLOGGED, true);
        }

        // UPDATE_CLIENTS only -- deliberately NOT Block.UPDATE_ALL.
        //
        // UPDATE_ALL includes UPDATE_NEIGHBORS, which makes the engine walk the six neighbours of this
        // position and read each of their block states through Level#getBlockState. Blocks at a chunk
        // edge reach into the adjacent chunk, and a chunk that is still generating only guarantees its
        // horizontal neighbours are at INITIALIZE_LIGHT (ChunkPyramid.GENERATION_PYRAMID: LIGHT
        // requires INITIALIZE_LIGHT at radius 1; the FULL step adds no requirement of its own). For a
        // neighbour that is not yet FULL, Level#getBlockState ends up in
        // ServerChunkCache#getChunk -> mainThreadProcessor.managedBlock, which waits on work that only
        // the main thread can do -- and the main thread is this call. That is a self-deadlock: the
        // server freezes with no exception and no log line.
        //
        // Dropping the neighbour updates is safe for this block: the waypoint has no redstone or shape
        // dependent behaviour, and SimpleWaterloggedBlock derives its fluid state from the block state
        // rather than from a neighbour notification. Light and client updates are unaffected, because
        // LevelChunk#setBlockState queues its own light check and heightmap updates independently of the
        // update flags.
        if (!level.setBlock(pos, state, Block.UPDATE_CLIENTS)) {
            return false;
        }

        // Safe to look up: the position was checked to hold no block entity before placement, so it
        // cannot be sitting in pendingBlockEntities and no lazy NBT deserialization can happen.
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
