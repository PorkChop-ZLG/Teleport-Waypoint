package com.zonlong.teleportwaypoint.network;

import com.zonlong.teleportwaypoint.TeleportWaypoint;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S→C: an existing waypoint was updated (rename, position, type, etc).
 */
public record UpdateWaypointPayload(WaypointSyncInfo waypoint) implements CustomPacketPayload {
    public static final Type<UpdateWaypointPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "update_waypoint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateWaypointPayload> STREAM_CODEC =
            StreamCodec.composite(
                    WaypointSyncInfo.STREAM_CODEC, UpdateWaypointPayload::waypoint,
                    UpdateWaypointPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
