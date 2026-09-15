package com.zonlong.teleportwaypoint.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common configuration shared by client and server.
 * Stored in {@code config/teleportwaypoint/common.toml}.
 */
public final class CommonConfig {
    public static final ModConfigSpec.IntValue TELEPORT_COOLDOWN_TICKS;
    public static final ModConfigSpec.BooleanValue DEFAULT_ENABLE_STRUCTURE_WAYPOINTS;
    public static final ModConfigSpec.BooleanValue DEFAULT_ENABLE_YUNG_STRUCTURE_WAYPOINTS;
    public static final ModConfigSpec.EnumValue<StructureWaypointMode> STRUCTURE_WAYPOINT_MODE;
    public static final ModConfigSpec.BooleanValue ENABLE_STRUCTURE_WAYPOINTS;
    public static final ModConfigSpec.BooleanValue DEBUG_MODE;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        TELEPORT_COOLDOWN_TICKS = builder
                .comment("Minimum delay in ticks between two teleport requests per player. 0 disables the cooldown.")
                .translation("teleportwaypoint.configuration.common.teleportCooldownTicks")
                .defineInRange("teleportCooldownTicks", 20, 0, 72000);

        builder.translation("teleportwaypoint.configuration.common.optionalDataPacks");
        builder.push("optionalDataPacks");

        DEFAULT_ENABLE_STRUCTURE_WAYPOINTS = builder
                .comment("Whether new worlds enable the vanilla structure override datapack by default.",
                        "The chunk-load injection feature replaces these datapacks, so this defaults to false.",
                        "Existing worlds keep whatever they already selected: turn the datapack off manually,",
                        "otherwise newly generated chunks will contain two waypoints per structure.")
                .translation("teleportwaypoint.configuration.common.defaultEnableStructureWaypoints")
                .define("defaultEnableStructureWaypoints", false);

        DEFAULT_ENABLE_YUNG_STRUCTURE_WAYPOINTS = builder
                .comment("Whether new worlds enable the YUNG structure compatibility datapack by default.",
                        "See defaultEnableStructureWaypoints for why this defaults to false.")
                .translation("teleportwaypoint.configuration.common.defaultEnableYungStructureWaypoints")
                .define("defaultEnableYungStructureWaypoints", false);

        builder.pop();

        builder.translation("teleportwaypoint.configuration.common.structureWaypoints");
        builder.push("structureWaypoints");

        ENABLE_STRUCTURE_WAYPOINTS = builder
                .comment("Master switch for automatic waypoint placement in structures.",
                        "When false, chunk loading does no structure waypoint work at all.",
                        "This is read as the very first gate of the chunk load handler, so changing it",
                        "requires a server restart to take effect.")
                .translation("teleportwaypoint.configuration.common.structureWaypoints.enabled")
                .define("enabled", true);

        STRUCTURE_WAYPOINT_MODE = builder
                .comment("\"WHITELIST\" = only place in structures listed by teleportwaypoint:waypoint_whitelist",
                        "\"BLACKLIST\" = place in every structure EXCEPT those listed by teleportwaypoint:waypoint_blacklist",
                        "The lists themselves are data pack tags under data/teleportwaypoint/tags/worldgen/structure/.")
                .translation("teleportwaypoint.configuration.common.structureWaypoints.mode")
                .defineEnum("mode", StructureWaypointMode.WHITELIST);

        DEBUG_MODE = builder
                .comment("When true, this mod writes DEBUG-level log lines. All debug output is gated by this",
                        "option and is written in English only, so log files never contain mojibake.")
                .translation("teleportwaypoint.configuration.common.structureWaypoints.debugMode")
                .define("debugMode", false);

        builder.pop();

        SPEC = builder.build();
    }

    private CommonConfig() {
    }
}
