package com.zonlong.teleportwaypoint.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Xaero's World Map integration configuration.
 * Stored in {@code config/teleportwaypoint/xaero-worldmap.toml}.
 *
 * <p>There is intentionally no range option: the world map is a global view and
 * the custom provider does not apply distance filtering. Pocket waypoints are
 * only shown after the local player has activated them.
 */
public final class XaeroWorldMapConfig {
    public static final ModConfigSpec.BooleanValue SHOW_WAYPOINTS;
    public static final ModConfigSpec.BooleanValue SHOW_INACTIVE_WAYPOINTS;
    public static final ModConfigSpec.BooleanValue SHOW_ACTIVE_WAYPOINTS;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        SHOW_WAYPOINTS = builder
                .comment("Show teleport waypoints on Xaero's world map.")
                .translation("teleportwaypoint.configuration.xaeroworldmap.showWaypoints")
                .define("showWaypoints", true);

        builder.translation("teleportwaypoint.configuration.xaeroworldmap.waypoint");
        builder.push("waypoint");
        SHOW_INACTIVE_WAYPOINTS = builder
                .comment("Show inactive teleport waypoints.")
                .translation("teleportwaypoint.configuration.xaeroworldmap.waypoint.showInactive")
                .define("showInactive", true);
        SHOW_ACTIVE_WAYPOINTS = builder
                .comment("Show active teleport waypoints.")
                .translation("teleportwaypoint.configuration.xaeroworldmap.waypoint.showActive")
                .define("showActive", true);
        builder.pop();

        SPEC = builder.build();
    }

    private XaeroWorldMapConfig() {
    }
}
