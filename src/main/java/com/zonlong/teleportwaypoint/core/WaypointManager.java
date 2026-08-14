package com.zonlong.teleportwaypoint.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.zonlong.teleportwaypoint.block.entity.WaypointBlockEntity;
import com.zonlong.teleportwaypoint.network.ActivatedWaypointInfo;
import com.zonlong.teleportwaypoint.network.SyncActivatedWaypointsPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Facade over the two SavedData stores, plus the shared activation / use logic.
 */
public class WaypointManager {

    public static void register(WaypointBlockEntity be) {
        UUID uid = be.getUid();
        if (uid == null || be.getLevel() == null || be.getLevel().isClientSide()) {
            return;
        }
        if (!(be.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        String name = be.isPocketWaypoint() ? be.getName() : be.getId();
        WaypointRegistryData.get(serverLevel.getServer())
                .put(new WaypointRecord(uid, serverLevel.dimension(), be.getBlockPos(), be.isPocketWaypoint(), name));
    }

    public static void unregister(WaypointBlockEntity be) {
        UUID uid = be.getExistingUid();
        if (uid == null || be.getLevel() == null || be.getLevel().isClientSide()) {
            return;
        }
        if (!(be.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        WaypointRegistryData.get(serverLevel.getServer()).remove(uid);
    }

    public static boolean isActivated(ServerPlayer player, UUID uid) {
        return PlayerWaypointData.get(player.getServer()).isActivated(player.getUUID(), uid);
    }

    public static Set<UUID> getActivated(ServerPlayer player) {
        return PlayerWaypointData.get(player.getServer()).getActivated(player.getUUID());
    }

    public static void activate(ServerPlayer player, WaypointBlockEntity be) {
        UUID uid = be.getUid();
        if (uid == null) {
            return;
        }
        register(be);
        PlayerWaypointData.get(player.getServer()).activate(player.getUUID(), uid);

        syncTo(player);

        player.sendSystemMessage(Component.translatable("chat.teleportwaypoint.activated", be.getDisplayName()));
    }

    public static void deactivate(ServerPlayer player, UUID uid) {
        PlayerWaypointData.get(player.getServer()).deactivate(player.getUUID(), uid);
        syncTo(player);
    }

    /**
     * Sends the player's current activated-waypoint list (with names) to the client.
     */
    public static void syncTo(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        List<ActivatedWaypointInfo> infos = new ArrayList<>();
        WaypointRegistryData registry = WaypointRegistryData.get(server);
        for (UUID uid : getActivated(player)) {
            registry.get(uid).ifPresent(record -> infos.add(new ActivatedWaypointInfo(uid, record.pocket(), record.name())));
        }
        PacketDistributor.sendToPlayer(player, new SyncActivatedWaypointsPayload(infos));
    }

    public static boolean canRename(Player player, WaypointBlockEntity be) {
        if (player.isCreative()) {
            return true;
        }
        return be.isPocketWaypoint() && be.getOwner() != null && be.getOwner().equals(player.getUUID());
    }

    public static InteractionResult onUse(Level level, BlockPos pos, Player player, WaypointBlockEntity be) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.FAIL;
        }
        UUID uid = be.getUid();
        if (uid == null) {
            return InteractionResult.FAIL;
        }
        if (!isActivated(serverPlayer, uid)) {
            activate(serverPlayer, be);
        }
        be.openListScreen(serverPlayer);
        return InteractionResult.SUCCESS;
    }
}
