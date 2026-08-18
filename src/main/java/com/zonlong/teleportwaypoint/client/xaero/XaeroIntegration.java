package com.zonlong.teleportwaypoint.client.xaero;

import net.neoforged.fml.ModList;

/**
 * Entry point for Xaero integrations. This class is only loaded (via reflection)
 * when at least one Xaero map mod is present. It dispatches to the World Map and
 * Minimap specific integration classes. Configuration is read directly by each
 * integration from its own config class.
 */
public final class XaeroIntegration {

    private XaeroIntegration() {
    }

    public static void tick() {
        if (ModList.get().isLoaded("xaeroworldmap")) {
            XaeroWorldMapIntegration.tick();
        }
        if (ModList.get().isLoaded("xaerominimap")) {
            XaeroMinimapIntegration.tick();
            XaeroMinimapIntegration.sync();
        }
    }
}
