package com.zonlong.teleportwaypoint.network;

import com.zonlong.teleportwaypoint.block.entity.WaypointBlockEntity;
import com.zonlong.teleportwaypoint.client.ClientWaypointState;
import com.zonlong.teleportwaypoint.core.WaypointTeleporter;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetwork {

    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                SyncActivatedWaypointsPayload.TYPE,
                SyncActivatedWaypointsPayload.STREAM_CODEC,
                ModNetwork::handleSyncActivated);

        registrar.playToServer(
                TeleportRequestPayload.TYPE,
                TeleportRequestPayload.STREAM_CODEC,
                ModNetwork::handleTeleportRequest);

        registrar.playToServer(
                RenameWaypointPayload.TYPE,
                RenameWaypointPayload.STREAM_CODEC,
                ModNetwork::handleRename);

        registrar.playToServer(
                OpenRenameScreenPayload.TYPE,
                OpenRenameScreenPayload.STREAM_CODEC,
                ModNetwork::handleOpenRename);
    }

    private static void handleSyncActivated(final SyncActivatedWaypointsPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> ClientWaypointState.setActivated(payload.waypoints()));
    }

    private static void handleTeleportRequest(final TeleportRequestPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                WaypointTeleporter.teleport(serverPlayer, payload.source(), payload.target());
            }
        });
    }

    private static void handleRename(final RenameWaypointPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }
            BlockEntity blockEntity = serverPlayer.level().getBlockEntity(payload.pos());
            if (!(blockEntity instanceof WaypointBlockEntity waypointEntity)) {
                return;
            }
            String text = payload.text();
            if (waypointEntity.isPocketWaypoint()) {
                // Only the owner may rename a pocket waypoint.
                if (waypointEntity.getOwner() != null && !waypointEntity.getOwner().equals(serverPlayer.getUUID())) {
                    return;
                }
                waypointEntity.setName(text);
            } else {
                // Structure waypoints: id only editable in creative mode and must match [a-z0-9]+.
                if (!serverPlayer.isCreative()) {
                    return;
                }
                String newId = text.isEmpty() ? "empty" : text;
                if (!WaypointBlockEntity.isValidId(newId)) {
                    return;
                }
                waypointEntity.setId(newId);
            }
            serverPlayer.level().sendBlockUpdated(payload.pos(), waypointEntity.getBlockState(), waypointEntity.getBlockState(), 3);
            // After renaming, open the waypoint list.
            waypointEntity.openListScreen(serverPlayer);
        });
    }

    private static void handleOpenRename(final OpenRenameScreenPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) {
                return;
            }
            BlockEntity blockEntity = serverPlayer.level().getBlockEntity(payload.pos());
            if (!(blockEntity instanceof WaypointBlockEntity waypointEntity)) {
                return;
            }
            // Permission check: pocket waypoint -> owner only; structure waypoint -> creative only.
            if (waypointEntity.isPocketWaypoint()) {
                if (waypointEntity.getOwner() == null || !waypointEntity.getOwner().equals(serverPlayer.getUUID())) {
                    return;
                }
            } else if (!serverPlayer.isCreative()) {
                return;
            }
            waypointEntity.openRenameScreen(serverPlayer);
        });
    }
}
