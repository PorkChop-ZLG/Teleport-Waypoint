package com.zonlong.teleportwaypoint;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组配置。配置界面由 NeoForge 自动生成。
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Xaero 地图联动
    public static final ModConfigSpec.BooleanValue SHOW_WAYPOINTS;
    public static final ModConfigSpec.BooleanValue SHOW_INACTIVE_WAYPOINTS;
    public static final ModConfigSpec.BooleanValue SHOW_ACTIVE_WAYPOINTS;
    public static final ModConfigSpec.IntValue WAYPOINT_RANGE;
    public static final ModConfigSpec.BooleanValue SHOW_INACTIVE_POCKET_WAYPOINTS;
    public static final ModConfigSpec.BooleanValue SHOW_ACTIVE_POCKET_WAYPOINTS;
    public static final ModConfigSpec.IntValue POCKET_WAYPOINT_RANGE;

    static {
        BUILDER.push("xaeroMapIntegration");

        SHOW_WAYPOINTS = BUILDER
                .comment("Show teleport waypoints on Xaero maps.")
                .define("showWaypoints", true);

        BUILDER.push("waypoint");
        SHOW_INACTIVE_WAYPOINTS = BUILDER
                .comment("Show inactive teleport waypoints.")
                .define("showInactive", true);
        SHOW_ACTIVE_WAYPOINTS = BUILDER
                .comment("Show active teleport waypoints.")
                .define("showActive", true);
        WAYPOINT_RANGE = BUILDER
                .comment("Maximum distance in blocks for teleport waypoints to appear. 0 disables the limit.")
                .defineInRange("range", 256, 0, 100000);
        BUILDER.pop();

        BUILDER.push("pocketWaypoint");
        SHOW_INACTIVE_POCKET_WAYPOINTS = BUILDER
                .comment("Show inactive pocket waypoints.")
                .define("showInactive", true);
        SHOW_ACTIVE_POCKET_WAYPOINTS = BUILDER
                .comment("Show active pocket waypoints.")
                .define("showActive", true);
        POCKET_WAYPOINT_RANGE = BUILDER
                .comment("Maximum distance in blocks for pocket waypoints to appear. 0 disables the limit.")
                .defineInRange("range", 256, 0, 100000);
        BUILDER.pop();

        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();
}
