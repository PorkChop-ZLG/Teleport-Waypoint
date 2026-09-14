package com.zonlong.teleportwaypoint.client;

import java.util.UUID;

import com.zonlong.teleportwaypoint.util.Naming;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Client-side representation of a waypoint known to the server, activated or not.
 */
public record ClientWaypointInfo(
        UUID uid,
        ResourceKey<Level> dimension,
        BlockPos pos,
        boolean pocket,
        String name
) {
    /**
     * Returns the display name: pocket waypoints use their literal name, regular
     * waypoints use the translation key based on their raw id.
     */
    public Component displayName() {
        return pocket ? Component.literal(name) : Naming.displayName(name);
    }
}
