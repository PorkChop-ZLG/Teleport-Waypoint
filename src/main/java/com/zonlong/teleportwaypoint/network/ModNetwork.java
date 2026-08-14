package com.zonlong.teleportwaypoint.network;

import com.zonlong.teleportwaypoint.block.entity.WaypointBlockEntity;
import com.zonlong.teleportwaypoint.client.ClientWaypointState;
import com.zonlong.teleportwaypoint.core.WaypointManager;
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

        registrar.playToServer(
                DeleteWaypointPayload.TYPE,
                DeleteWaypointPayload.STREAM_CODEC,
                ModNetwork::handleDelete);
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
            if (!WaypointManager.canRename(serverPlayer, waypointEntity)) {
                return;
            }
            String text = payload.text();
            if (waypointEntity.isPocketWaypoint()) {
                waypointEntity.setName(text);
            } else {
                String newId = text.isEmpty() ? "empty" : text;
                if (!WaypointBlockEntity.isValidId(newId)) {
                    return;
                }
                waypointEntity.setId(newId);
            }
            serverPlayer.level().sendBlockUpdated(payload.pos(), waypointEntity.getBlockState(), waypointEntity.getBlockState(), 3);
            // Update the global registry and the client list with the new name.
            WaypointManager.register(waypointEntity);
            WaypointManager.syncTo(serverPlayer);
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
            if (!WaypointManager.canRename(serverPlayer, waypointEntity)) {
                return;
            }
            waypointEntity.openRenameScreen(serverPlayer);
        });
    }

    private static void handleDelete(final DeleteWaypointPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                WaypointManager.deactivate(serverPlayer, payload.uid());
            }
        });
    }
}
