package com.zonlong.teleportwaypoint.network;

import com.zonlong.teleportwaypoint.TeleportWaypoint;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S→C: a single waypoint was added to the global registry.
 */
public record AddWaypointPayload(WaypointSyncInfo waypoint) implements CustomPacketPayload {
    public static final Type<AddWaypointPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "add_waypoint"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AddWaypointPayload> STREAM_CODEC =
            StreamCodec.composite(
                    WaypointSyncInfo.STREAM_CODEC, AddWaypointPayload::waypoint,
                    AddWaypointPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
