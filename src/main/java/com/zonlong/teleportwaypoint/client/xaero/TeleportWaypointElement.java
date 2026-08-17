package com.zonlong.teleportwaypoint.client.xaero;

import java.util.UUID;

import com.zonlong.teleportwaypoint.client.ClientWaypointInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Element rendered on Xaero's world map. It is a snapshot of a waypoint plus the
 * local player's activation state at the time the provider built the list.
 */
public record TeleportWaypointElement(ClientWaypointInfo info, boolean activated) {
    public UUID uid() {
        return info.uid();
    }

    public ResourceKey<Level> dimension() {
        return info.dimension();
    }

    public BlockPos pos() {
        return info.pos();
    }

    public int getX() {
        return info.pos().getX();
    }

    public int getY() {
        return info.pos().getY();
    }

    public int getZ() {
        return info.pos().getZ();
    }

    public String name() {
        return info.name();
    }

    public boolean pocket() {
        return info.pocket();
    }
}
