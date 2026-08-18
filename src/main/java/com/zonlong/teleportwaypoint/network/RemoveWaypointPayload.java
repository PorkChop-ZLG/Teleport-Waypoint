package com.zonlong.teleportwaypoint.network;

import java.util.UUID;

import com.zonlong.teleportwaypoint.TeleportWaypoint;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S→C: a waypoint was removed from the global registry.
 */
public record RemoveWaypointPayload(UUID uid) implements CustomPacketPayload {
    public static final Type<RemoveWaypointPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "remove_waypoint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoveWaypointPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, RemoveWaypointPayload::uid,
                    RemoveWaypointPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
