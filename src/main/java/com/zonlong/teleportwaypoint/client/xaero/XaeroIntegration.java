package com.zonlong.teleportwaypoint.client.xaero;

import com.zonlong.teleportwaypoint.Config;

import net.neoforged.fml.ModList;

/**
 * Entry point for Xaero integrations. This class is only loaded (via reflection)
 * when at least one Xaero map mod is present. It dispatches to the World Map and
 * Minimap specific integration classes and provides shared config helpers that do
 * not directly reference Xaero classes.
 */
public final class XaeroIntegration {

    private XaeroIntegration() {
    }

    public static void tick() {
        if (ModList.get().isLoaded("xaeroworldmap")) {
            XaeroWorldMapIntegration.tick();
        }
        if (ModList.get().isLoaded("xaerominimap")) {
            XaeroMinimapIntegration.tick();
            XaeroMinimapIntegration.sync();
        }
    }

    public static boolean showWaypoints() {
        if (ModList.get().isLoaded("xaeroworldmap")) {
            return XaeroWorldMapIntegration.showWaypoints();
        }
        return Config.SHOW_WAYPOINTS.get();
    }

    public static boolean showWaypointNames() {
        if (ModList.get().isLoaded("xaeroworldmap")) {
            return XaeroWorldMapIntegration.showWaypointNames();
        }
        // The NeoForge showWaypointNames option has been removed; names default to shown
        // unless Xaero's own config is available.
        return true;
    }

    /**
     * Returns whether a waypoint of the given type/state should be shown at all,
     * based on the master showWaypoints switch and the per-type active/inactive toggles.
     */
    public static boolean shouldShow(boolean pocket, boolean activated) {
        if (!showWaypoints()) {
            return false;
        }
        if (pocket) {
            return activated ? Config.SHOW_ACTIVE_POCKET_WAYPOINTS.get() : Config.SHOW_INACTIVE_POCKET_WAYPOINTS.get();
        }
        return activated ? Config.SHOW_ACTIVE_WAYPOINTS.get() : Config.SHOW_INACTIVE_WAYPOINTS.get();
    }

    /**
     * Returns the configured display range in blocks for the given waypoint type.
     * A value of 0 means no distance limit.
     */
    public static int getDisplayRange(boolean pocket) {
        return pocket ? Config.POCKET_WAYPOINT_RANGE.get() : Config.WAYPOINT_RANGE.get();
    }
}
