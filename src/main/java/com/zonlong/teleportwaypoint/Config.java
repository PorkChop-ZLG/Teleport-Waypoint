package com.zonlong.teleportwaypoint;

import net.neoforged.neoforge.common.ModConfigSpec;

// Config class for the mod. Options can be added here as needed.
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // No config options are required for the initial implementation.
    // Add ModConfigSpec values here (e.g. BooleanValue, IntValue, ConfigValue<String>) as features demand.

    static final ModConfigSpec SPEC = BUILDER.build();
}
