package com.zonlong.teleportwaypoint.network;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-facing snapshot of every known waypoint (activated or not). Sent by
 * {@link SyncAllWaypointsPayload} so Xaero map integrations can render waypoints
 * the player has not yet activated.
 */
public record WaypointSyncInfo(UUID uid, ResourceLocation dimension, BlockPos pos, boolean pocket, String name) {
    public static final StreamCodec<RegistryFriendlyByteBuf, WaypointSyncInfo> STREAM_CODEC =
            StreamCodec.composite(
                    net.minecraft.core.UUIDUtil.STREAM_CODEC, WaypointSyncInfo::uid,
                    ResourceLocation.STREAM_CODEC, WaypointSyncInfo::dimension,
                    BlockPos.STREAM_CODEC, WaypointSyncInfo::pos,
                    ByteBufCodecs.BOOL, WaypointSyncInfo::pocket,
                    ByteBufCodecs.STRING_UTF8, WaypointSyncInfo::name,
                    WaypointSyncInfo::new);
}
