package com.zonlong.teleportwaypoint.client;

import java.util.UUID;

import net.minecraft.core.BlockPos;
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
}
