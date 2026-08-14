package com.zonlong.teleportwaypoint.block;

import com.zonlong.teleportwaypoint.block.entity.WaypointBlockEntity;
import com.zonlong.teleportwaypoint.core.WaypointManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.mojang.serialization.MapCodec;

/**
 * The structure/creative-placed "waypoint" block: unbreakable, named via a translation key,
 * and its id field is only editable in creative mode.
 */
public class WaypointBlock extends BaseEntityBlock {

    public static final MapCodec<WaypointBlock> CODEC = simpleCodec(WaypointBlock::new);

    public WaypointBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WaypointBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof WaypointBlockEntity waypointEntity)) {
            return;
        }
        WaypointManager.register(waypointEntity);
        if (placer instanceof ServerPlayer serverPlayer) {
            WaypointManager.activate(serverPlayer, waypointEntity);
            // Sync block entity data before opening the GUI so the client sees the correct owner/id.
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.getChunkSource().blockChanged(pos);
            }
            waypointEntity.openRenameScreen(serverPlayer, true);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof WaypointBlockEntity waypointEntity)) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide() && player.isShiftKeyDown() && player instanceof ServerPlayer serverPlayer
                && WaypointManager.canRename(serverPlayer, waypointEntity)) {
            waypointEntity.openRenameScreen(serverPlayer);
            return InteractionResult.SUCCESS;
        }
        return WaypointManager.onUse(level, pos, player, waypointEntity);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof WaypointBlockEntity waypointEntity) {
                WaypointManager.unregister(waypointEntity);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
