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
                if (!serverPlayer.isCreative() || !WaypointBlockEntity.isValidId(text)) {
                    return;
                }
                waypointEntity.setId(text);
            }
            serverPlayer.level().sendBlockUpdated(payload.pos(), waypointEntity.getBlockState(), waypointEntity.getBlockState(), 3);
        });
    }
}
