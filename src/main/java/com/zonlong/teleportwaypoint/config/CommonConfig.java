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
                .comment("Whether new worlds enable the vanilla structure override datapack by default.")
                .translation("teleportwaypoint.configuration.common.defaultEnableStructureWaypoints")
                .define("defaultEnableStructureWaypoints", true);

        DEFAULT_ENABLE_YUNG_STRUCTURE_WAYPOINTS = builder
                .comment("Whether new worlds enable the YUNG structure compatibility datapack by default.")
                .translation("teleportwaypoint.configuration.common.defaultEnableYungStructureWaypoints")
                .define("defaultEnableYungStructureWaypoints", true);

        builder.pop();

        SPEC = builder.build();
    }

    private CommonConfig() {
    }
}
