package com.zonlong.teleportwaypoint.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side configuration. Stored in {@code config/teleportwaypoint/server.toml}.
 */
public final class ServerConfig {
    public static final ModConfigSpec.IntValue TELEPORT_COOLDOWN_TICKS;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        TELEPORT_COOLDOWN_TICKS = builder
                .comment("Minimum delay in ticks between two teleport requests per player. 0 disables the cooldown.")
                .translation("teleportwaypoint.configuration.server.teleportCooldownTicks")
                .defineInRange("teleportCooldownTicks", 20, 0, 72000);

        SPEC = builder.build();
    }

    private ServerConfig() {
    }
}
