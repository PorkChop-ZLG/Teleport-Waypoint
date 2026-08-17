package com.zonlong.teleportwaypoint;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组配置。当前无实际可调选项，预留 ModConfigSpec 入口
 * （配置界面由 NeoForge 自动生成）。
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SHOW_WAYPOINTS = BUILDER
            .comment("Show teleport waypoints on Xaero maps.")
            .define("showWaypoints", true);

    public static final ModConfigSpec.BooleanValue SHOW_WAYPOINT_NAMES = BUILDER
            .comment("Show teleport waypoint names on Xaero maps.")
            .define("showWaypointNames", true);

    static final ModConfigSpec SPEC = BUILDER.build();
}
