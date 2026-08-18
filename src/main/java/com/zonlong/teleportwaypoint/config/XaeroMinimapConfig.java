package com.zonlong.teleportwaypoint.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Xaero's Minimap integration configuration.
 * Stored in {@code config/teleportwaypoint/xaero-minimap.toml}.
 *
 * <p>Pocket waypoints are intentionally not configurable here: they are only
 * shown after the local player has activated them, to avoid leaking base
 * coordinates.
 */
public final class XaeroMinimapConfig {
    public static final ModConfigSpec.BooleanValue SHOW_WAYPOINTS;
    public static final ModConfigSpec.IntValue RANGE;
    public static final ModConfigSpec.BooleanValue SHOW_INACTIVE_WAYPOINTS;
    public static final ModConfigSpec.BooleanValue SHOW_ACTIVE_WAYPOINTS;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        SHOW_WAYPOINTS = builder
                .comment("Show teleport waypoints on Xaero's minimap.")
                .translation("teleportwaypoint.configuration.xaerominimap.showWaypoints")
                .define("showWaypoints", true);

        RANGE = builder
                .comment("Maximum distance in blocks for waypoints to appear on the minimap. 0 disables the limit.")
                .translation("teleportwaypoint.configuration.xaerominimap.range")
                .defineInRange("range", 256, 0, 100000);

        builder.translation("teleportwaypoint.configuration.xaerominimap.waypoint");
        builder.push("waypoint");
        SHOW_INACTIVE_WAYPOINTS = builder
                .comment("Show inactive teleport waypoints.")
                .translation("teleportwaypoint.configuration.xaerominimap.waypoint.showInactive")
                .define("showInactive", true);
        SHOW_ACTIVE_WAYPOINTS = builder
                .comment("Show active teleport waypoints.")
                .translation("teleportwaypoint.configuration.xaerominimap.waypoint.showActive")
                .define("showActive", true);
        builder.pop();

        SPEC = builder.build();
    }

    private XaeroMinimapConfig() {
    }
}
