package com.zonlong.teleportwaypoint.network;

import java.util.ArrayList;
import java.util.List;

import com.zonlong.teleportwaypoint.TeleportWaypoint;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S→C: one page of the full registered-waypoint snapshot. Sent on login/reconnect.
 * The client accumulates pages and treats the payload as complete when {@code done} is true.
 */
public record SyncAllWaypointsPayload(List<WaypointSyncInfo> waypoints, int page, boolean done) implements CustomPacketPayload {
    public static final int MAX_PAGE_SIZE = 500;

    public static final Type<SyncAllWaypointsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "sync_all_waypoints"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncAllWaypointsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(ArrayList::new, WaypointSyncInfo.STREAM_CODEC, MAX_PAGE_SIZE),
                    SyncAllWaypointsPayload::waypoints,
                    ByteBufCodecs.VAR_INT, SyncAllWaypointsPayload::page,
                    ByteBufCodecs.BOOL, SyncAllWaypointsPayload::done,
                    SyncAllWaypointsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
