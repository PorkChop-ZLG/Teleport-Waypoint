package com.zonlong.teleportwaypoint.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Xaero's Minimap integration configuration.
 * Stored in {@code config/teleportwaypoint/xaero-minimap.toml}.
 */
public final class XaeroMinimapConfig {
    public static final ModConfigSpec.BooleanValue SHOW_WAYPOINTS;
    public static final ModConfigSpec.BooleanValue SHOW_WAYPOINT_NAMES;
    public static final ModConfigSpec.BooleanValue SHOW_INACTIVE_WAYPOINTS;
    public static final ModConfigSpec.BooleanValue SHOW_ACTIVE_WAYPOINTS;
    public static final ModConfigSpec.IntValue WAYPOINT_RANGE;
    public static final ModConfigSpec.BooleanValue SHOW_INACTIVE_POCKET_WAYPOINTS;
    public static final ModConfigSpec.BooleanValue SHOW_ACTIVE_POCKET_WAYPOINTS;
    public static final ModConfigSpec.IntValue POCKET_WAYPOINT_RANGE;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        SHOW_WAYPOINTS = builder
                .comment("Show teleport waypoints on Xaero's minimap.")
                .translation("teleportwaypoint.configuration.xaerominimap.showWaypoints")
                .define("showWaypoints", true);

        SHOW_WAYPOINT_NAMES = builder
                .comment("Show waypoint names on Xaero's minimap.")
                .translation("teleportwaypoint.configuration.xaerominimap.showWaypointNames")
                .define("showWaypointNames", true);

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
        WAYPOINT_RANGE = builder
                .comment("Maximum distance in blocks for teleport waypoints to appear. 0 disables the limit.")
                .translation("teleportwaypoint.configuration.xaerominimap.waypoint.range")
                .defineInRange("range", 128, 0, 100000);
        builder.pop();

        builder.translation("teleportwaypoint.configuration.xaerominimap.pocketWaypoint");
        builder.push("pocketWaypoint");
        SHOW_INACTIVE_POCKET_WAYPOINTS = builder
                .comment("Show inactive pocket waypoints.")
                .translation("teleportwaypoint.configuration.xaerominimap.pocketWaypoint.showInactive")
                .define("showInactive", true);
        SHOW_ACTIVE_POCKET_WAYPOINTS = builder
                .comment("Show active pocket waypoints.")
                .translation("teleportwaypoint.configuration.xaerominimap.pocketWaypoint.showActive")
                .define("showActive", true);
        POCKET_WAYPOINT_RANGE = builder
                .comment("Maximum distance in blocks for pocket waypoints to appear. 0 disables the limit.")
                .translation("teleportwaypoint.configuration.xaerominimap.pocketWaypoint.range")
                .defineInRange("range", 128, 0, 100000);
        builder.pop();

        SPEC = builder.build();
    }

    private XaeroMinimapConfig() {
    }
}
