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
 * S→C: one page of a full waypoint snapshot for one dimension. For the current
 * dimension this contains every normal waypoint plus every pocket waypoint the
 * receiving player has activated. The client accumulates pages until {@code done}
 * is true.
 */
public record SyncDimensionWaypointsPayload(ResourceLocation dimension, List<WaypointSyncInfo> waypoints, int page, boolean done) implements CustomPacketPayload {
    public static final int MAX_PAGE_SIZE = 500;

    public static final Type<SyncDimensionWaypointsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "sync_dimension_waypoints"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncDimensionWaypointsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, SyncDimensionWaypointsPayload::dimension,
                    ByteBufCodecs.collection(ArrayList::new, WaypointSyncInfo.STREAM_CODEC, MAX_PAGE_SIZE),
                    SyncDimensionWaypointsPayload::waypoints,
                    ByteBufCodecs.VAR_INT, SyncDimensionWaypointsPayload::page,
                    ByteBufCodecs.BOOL, SyncDimensionWaypointsPayload::done,
                    SyncDimensionWaypointsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
