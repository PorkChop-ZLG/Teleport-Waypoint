package com.zonlong.teleportwaypoint.network;

import com.zonlong.teleportwaypoint.TeleportWaypoint;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S→C: the receiving player activated a waypoint.
 */
public record ActivatedWaypointAddPayload(ActivatedWaypointInfo info) implements CustomPacketPayload {
    public static final Type<ActivatedWaypointAddPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "activated_waypoint_add"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ActivatedWaypointAddPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ActivatedWaypointInfo.STREAM_CODEC, ActivatedWaypointAddPayload::info,
                    ActivatedWaypointAddPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
