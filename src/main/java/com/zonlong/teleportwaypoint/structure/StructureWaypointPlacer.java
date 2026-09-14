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
 * through {@link ServerLevel#setBlock}, which resolves the chunk via {@code getChunkAt}; that is safe
 * here because the event fires while the chunk holder's {@code currentlyLoading} field still points
 * at the very chunk being loaded, so {@code ServerChunkCache#getChunk} short-circuits before it can
 * block on the main thread's own mailbox.
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

        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
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
