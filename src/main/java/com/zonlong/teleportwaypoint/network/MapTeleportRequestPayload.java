package com.zonlong.teleportwaypoint.network;

import java.util.UUID;

import com.zonlong.teleportwaypoint.TeleportWaypoint;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C→S: teleport request originating from a map overlay. Only the target waypoint
 * uid is sent; all validation happens server-side.
 */
public record MapTeleportRequestPayload(UUID target) implements CustomPacketPayload {
    public static final Type<MapTeleportRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "map_teleport_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MapTeleportRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, MapTeleportRequestPayload::target,
                    MapTeleportRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
