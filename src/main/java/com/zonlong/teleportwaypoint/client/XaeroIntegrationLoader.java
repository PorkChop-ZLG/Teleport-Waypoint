package com.zonlong.teleportwaypoint.client;

import java.lang.reflect.Method;

import com.zonlong.teleportwaypoint.TeleportWaypoint;

import net.neoforged.fml.ModList;

/**
 * Loads {@code client.xaero.XaeroIntegration} reflectively so the rest of the mod
 * never has a hard dependency on Xaero classes. If Xaero is not installed, this
 * class simply does nothing.
 */
public final class XaeroIntegrationLoader {
    private static final String INTEGRATION_CLASS = "com.zonlong.teleportwaypoint.client.xaero.XaeroIntegration";
    private static Method tickMethod;
    private static boolean attempted;

    private XaeroIntegrationLoader() {
    }

    public static void tick() {
        if (attempted) {
            return;
        }
        boolean worldMap = ModList.get().isLoaded("xaeroworldmap");
        boolean minimap = ModList.get().isLoaded("xaerominimap");
        if (!worldMap && !minimap) {
            attempted = true;
            return;
        }
        if (tickMethod == null) {
            try {
                tickMethod = Class.forName(INTEGRATION_CLASS).getMethod("tick");
            } catch (ReflectiveOperationException e) {
                TeleportWaypoint.LOGGER.warn("[TeleportWaypoint] Failed to resolve Xaero integration class", e);
                attempted = true;
                return;
            }
        }
        try {
            tickMethod.invoke(null);
        } catch (ReflectiveOperationException e) {
            TeleportWaypoint.LOGGER.warn("[TeleportWaypoint] Failed to invoke Xaero integration", e);
            attempted = true;
        }
    }
}
