package com.zonlong.teleportwaypoint.network;

import java.util.ArrayList;
import java.util.List;

import com.zonlong.teleportwaypoint.TeleportWaypoint;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncActivatedWaypointsPayload(List<ActivatedWaypointInfo> waypoints) implements CustomPacketPayload {
    public static final int MAX_ACTIVATED_SYNC = 100_000;

    public static final Type<SyncActivatedWaypointsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "sync_activated_waypoints"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncActivatedWaypointsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(ArrayList::new, ActivatedWaypointInfo.STREAM_CODEC, MAX_ACTIVATED_SYNC),
                    SyncActivatedWaypointsPayload::waypoints,
                    SyncActivatedWaypointsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
