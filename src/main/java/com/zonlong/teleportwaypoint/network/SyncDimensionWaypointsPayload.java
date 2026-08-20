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
 * S→C: a full snapshot of all waypoint data the client should hold for one
 * dimension. For the current dimension this contains every normal waypoint plus
 * every pocket waypoint the receiving player has activated.
 */
public record SyncDimensionWaypointsPayload(ResourceLocation dimension, List<WaypointSyncInfo> waypoints) implements CustomPacketPayload {
    public static final int MAX_DIMENSION_SYNC = 100_000;

    public static final Type<SyncDimensionWaypointsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "sync_dimension_waypoints"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncDimensionWaypointsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, SyncDimensionWaypointsPayload::dimension,
                    ByteBufCodecs.collection(ArrayList::new, WaypointSyncInfo.STREAM_CODEC, MAX_DIMENSION_SYNC),
                    SyncDimensionWaypointsPayload::waypoints,
                    SyncDimensionWaypointsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
