package com.zonlong.teleportwaypoint.client.xaero;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Shared per-frame context for the world map element renderer. The renderer fills
 * the current map dimension before the provider iterates elements.
 */
public class TeleportWaypointContext {
    public ResourceKey<Level> mapDimension;
    public boolean showWaypoints = true;
    public boolean showNames = true;
}
