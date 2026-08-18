package com.zonlong.teleportwaypoint.network;

import java.util.UUID;

import com.zonlong.teleportwaypoint.TeleportWaypoint;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S→C: the receiving player deactivated/removed a waypoint from their list.
 */
public record ActivatedWaypointRemovePayload(UUID uid) implements CustomPacketPayload {
    public static final Type<ActivatedWaypointRemovePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "activated_waypoint_remove"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ActivatedWaypointRemovePayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, ActivatedWaypointRemovePayload::uid,
                    ActivatedWaypointRemovePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
