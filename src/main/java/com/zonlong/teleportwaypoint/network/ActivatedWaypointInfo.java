package com.zonlong.teleportwaypoint.network;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Client-facing snapshot of an activated waypoint: uid + enough to render its display name.
 */
public record ActivatedWaypointInfo(UUID uid, boolean pocket, String name) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ActivatedWaypointInfo> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, ActivatedWaypointInfo::uid,
                    ByteBufCodecs.BOOL, ActivatedWaypointInfo::pocket,
                    ByteBufCodecs.STRING_UTF8, ActivatedWaypointInfo::name,
                    ActivatedWaypointInfo::new);

    public Component toComponent() {
        return pocket ? Component.literal(name) : Component.translatable("teleportwaypoint.waypoint." + name);
    }
}
