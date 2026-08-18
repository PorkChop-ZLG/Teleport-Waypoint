package com.zonlong.teleportwaypoint.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side general configuration. Currently only a placeholder so that
 * {@code config/teleportwaypoint/client.toml} is generated for future options.
 */
public final class ClientConfig {
    public static final ModConfigSpec.BooleanValue PLACEHOLDER;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        PLACEHOLDER = builder
                .comment("Reserved for future client-side options.")
                .translation("teleportwaypoint.configuration.client.placeholder")
                .define("placeholder", true);

        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}
