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
 * S→C: full list of registered waypoints in the world. Used to keep Xaero map
 * overlays up to date for both activated and unactivated waypoints.
 */
public record SyncAllWaypointsPayload(List<WaypointSyncInfo> waypoints) implements CustomPacketPayload {
    public static final Type<SyncAllWaypointsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "sync_all_waypoints"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncAllWaypointsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(ArrayList::new, WaypointSyncInfo.STREAM_CODEC),
                    SyncAllWaypointsPayload::waypoints,
                    SyncAllWaypointsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
