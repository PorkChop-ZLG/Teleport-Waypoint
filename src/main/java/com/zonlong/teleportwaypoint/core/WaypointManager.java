package com.zonlong.teleportwaypoint.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.zonlong.teleportwaypoint.block.entity.WaypointBlockEntity;
import com.zonlong.teleportwaypoint.network.ActivatedWaypointInfo;
import com.zonlong.teleportwaypoint.network.SyncActivatedWaypointsPayload;
import com.zonlong.teleportwaypoint.network.SyncAllWaypointsPayload;
import com.zonlong.teleportwaypoint.network.WaypointSyncInfo;

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
        if (be.getLevel() == null || be.getLevel().isClientSide()) {
            return;
        }
        if (!(be.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        WaypointRegistryData registry = WaypointRegistryData.get(serverLevel.getServer());
        UUID uid = ensureUniqueUid(be, registry, serverLevel);
        if (uid == null) {
            return;
        }
        String name;
        if (be.isPocketWaypoint()) {
            name = be.getName();
        } else {
            name = WaypointBlockEntity.isValidId(be.getId()) ? be.getId() : "empty";
        }
        boolean existed = registry.get(uid)
                .filter(record -> record.dimension().equals(serverLevel.dimension())
                        && record.pos().equals(be.getBlockPos()))
                .isPresent();
        registry.put(new WaypointRecord(uid, serverLevel.dimension(), be.getBlockPos(), be.isPocketWaypoint(), name));
        if (!existed) {
            broadcastAll(serverLevel.getServer());
        }
    }

    public static void unregister(WaypointBlockEntity be) {
        UUID uid = be.getExistingUid();
        if (uid == null || be.getLevel() == null || be.getLevel().isClientSide()) {
            return;
        }
        if (!(be.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        WaypointRegistryData registry = WaypointRegistryData.get(serverLevel.getServer());
        if (registry.removeIfAt(uid, serverLevel.dimension(), be.getBlockPos())) {
            removeWaypoint(serverLevel.getServer(), uid);
        }
    }

    public static boolean isActivated(ServerPlayer player, UUID uid) {
        return PlayerWaypointData.get(player.getServer()).isActivated(player.getUUID(), uid);
    }

    public static Set<UUID> getActivated(ServerPlayer player) {
        return PlayerWaypointData.get(player.getServer()).getActivated(player.getUUID());
    }

    public static void activate(ServerPlayer player, WaypointBlockEntity be, boolean showMessage) {
        UUID uid = be.getUid();
        if (uid == null) {
            return;
        }
        register(be);
        PlayerWaypointData.get(player.getServer()).activate(player.getUUID(), uid);

        syncTo(player);

        // 激活音效：经验球拾取声，在方块位置播放（附近玩家可闻）
        if (be.getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, be.getBlockPos(),
                    net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        if (showMessage) {
            String key = be.isPocketWaypoint() ? "chat.teleportwaypoint.pocket_waypoint_activated" : "chat.teleportwaypoint.waypoint_activated";
            player.sendSystemMessage(Component.translatable(key, be.getDisplayName()));
        }
    }

    public static void deactivate(ServerPlayer player, UUID uid) {
        PlayerWaypointData.get(player.getServer()).deactivate(player.getUUID(), uid);
        syncTo(player);
    }

    public static void removeWaypoint(MinecraftServer server, UUID uid) {
        boolean removed = WaypointRegistryData.get(server).remove(uid);
        Set<UUID> affectedPlayers = PlayerWaypointData.get(server).deactivateAll(uid);
        if (removed) {
            broadcastAll(server);
        }
        for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
            if (affectedPlayers.contains(onlinePlayer.getUUID())) {
                syncTo(onlinePlayer);
            }
        }
    }

    public static boolean isValidTeleportRequest(ServerPlayer player, UUID sourceUid, UUID targetUid) {
        if (sourceUid == null || targetUid == null || sourceUid.equals(targetUid)
                || !isActivated(player, sourceUid) || !isActivated(player, targetUid)) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel sourceLevel)) {
            return false;
        }

        WaypointRegistryData registry = WaypointRegistryData.get(sourceLevel.getServer());
        WaypointRecord source = registry.get(sourceUid).orElse(null);
        if (source == null || !source.dimension().equals(sourceLevel.dimension())
                || player.distanceToSqr(source.pos().getX() + 0.5, source.pos().getY() + 0.5, source.pos().getZ() + 0.5) > 64.0) {
            return false;
        }
        if (!(sourceLevel.getBlockEntity(source.pos()) instanceof WaypointBlockEntity sourceEntity)
                || !sourceUid.equals(sourceEntity.getExistingUid())) {
            return false;
        }
        return true;
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

    /**
     * Sends every registered waypoint to the player. Used on login and by map
     * integrations so unactivated waypoints are also visible.
     */
    public static void syncAllTo(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        List<WaypointSyncInfo> infos = new ArrayList<>();
        WaypointRegistryData registry = WaypointRegistryData.get(server);
        for (WaypointRecord record : registry.getAll()) {
            infos.add(new WaypointSyncInfo(
                    record.uid(),
                    record.dimension().location(),
                    record.pos(),
                    record.pocket(),
                    record.name()));
        }
        PacketDistributor.sendToPlayer(player, new SyncAllWaypointsPayload(infos));
    }

    /**
     * Sends the full waypoint list to every online player.
     */
    public static void broadcastAll(MinecraftServer server) {
        for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
            syncAllTo(onlinePlayer);
        }
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
            // 未解锁：仅激活（提示 + 音效），不弹 GUI；再次右键（已解锁）才打开列表
            activate(serverPlayer, be, true);
            return InteractionResult.SUCCESS;
        }
        be.openListScreen(serverPlayer);
        return InteractionResult.SUCCESS;
    }

    private static UUID ensureUniqueUid(WaypointBlockEntity be, WaypointRegistryData registry, ServerLevel level) {
        UUID uid = be.getUid();
        while (uid != null && registry.get(uid).filter(record -> !isRegisteredAt(record, level, be.getBlockPos())).isPresent()) {
            be.regenerateUid();
            uid = be.getUid();
        }
        return uid;
    }

    private static boolean isRegisteredAt(WaypointRecord record, ServerLevel level, BlockPos pos) {
        return record.dimension().equals(level.dimension()) && record.pos().equals(pos);
    }
}
