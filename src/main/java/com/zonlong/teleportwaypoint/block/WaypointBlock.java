package com.zonlong.teleportwaypoint.block;

import com.zonlong.teleportwaypoint.block.entity.WaypointBlockEntity;
import com.zonlong.teleportwaypoint.core.WaypointManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
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
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof WaypointBlockEntity waypointEntity)) {
            return;
        }
        WaypointManager.register(waypointEntity);
        if (placer instanceof ServerPlayer serverPlayer) {
            WaypointManager.activate(serverPlayer, waypointEntity);
            waypointEntity.openInitialScreen(serverPlayer);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof WaypointBlockEntity waypointEntity)) {
            return InteractionResult.FAIL;
        }
        return WaypointManager.onUse(level, pos, player, waypointEntity);
    }
}
