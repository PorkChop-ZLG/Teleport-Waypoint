package com.zonlong.teleportwaypoint.client.xaero;

import java.util.ArrayList;

import com.zonlong.teleportwaypoint.TeleportWaypoint;
import com.zonlong.teleportwaypoint.config.XaeroWorldMapConfig;

import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import xaero.lib.common.config.option.BooleanConfigOption;
import xaero.lib.common.config.option.ConfigOption;
import xaero.lib.common.config.option.ConfigOptionManager;
import xaero.map.WorldMap;

/**
 * World Map specific Xaero integration. This class is only loaded when Xaero's
 * World Map mod is present, so it may safely reference World Map classes.
 */
public final class XaeroWorldMapIntegration {
    private static boolean worldMapRegistered;
    private static boolean configAttempted;

    private static ConfigOption<Boolean> xaeroShowWaypoints;

    private XaeroWorldMapIntegration() {
    }

    public static void tick() {
        tryRegisterXaeroConfig();
        syncConfigToXaero();
        registerWorldMap();
    }

    public static boolean showWaypoints() {
        return XaeroWorldMapConfig.SHOW_WAYPOINTS.get();
    }

    private static void tryRegisterXaeroConfig() {
        if (configAttempted) {
            return;
        }
        if (!ModList.get().isLoaded("xaeroworldmap") || WorldMap.INSTANCE == null) {
            // World Map not ready yet; keep trying on later ticks.
            return;
        }
        configAttempted = true;
        try {
            ConfigOptionManager manager = WorldMap.INSTANCE.getConfigs().getConfigOptionManager();
            xaeroShowWaypoints = createBooleanOption(
                    "teleportwaypoint.show_waypoints",
                    "Show Teleport Waypoints",
                    true);
            manager.register(xaeroShowWaypoints);
            TeleportWaypoint.LOGGER.info("[TeleportWaypoint] Registered Xaero config options");
        } catch (Exception e) {
            TeleportWaypoint.LOGGER.info("[TeleportWaypoint] Xaero config options unavailable, using mod config fallback: {}", e.toString());
            xaeroShowWaypoints = null;
        }
    }

    private static void syncConfigToXaero() {
        if (xaeroShowWaypoints == null || WorldMap.INSTANCE == null) {
            return;
        }
        try {
            var configManager = WorldMap.INSTANCE.getConfigs().getClientConfigManager();
            var profile = configManager.getCurrentProfile();
            if (profile != null) {
                profile.set(xaeroShowWaypoints, XaeroWorldMapConfig.SHOW_WAYPOINTS.get());
            }
        } catch (Exception e) {
            TeleportWaypoint.LOGGER.debug("[TeleportWaypoint] Failed to mirror config into Xaero World Map", e);
        }
    }

    private static ConfigOption<Boolean> createBooleanOption(String id, String displayName, boolean defaultValue) {
        return BooleanConfigOption.Builder.begin()
                .setDefault()
                .setId(id)
                .setDefaultValue(defaultValue)
                .setDisplayName(Component.literal(displayName))
                .setShouldSaveDefaultValue(true)
                .setOverridable(true)
                .build(new ArrayList<>());
    }

    private static void registerWorldMap() {
        if (worldMapRegistered) {
            return;
        }
        if (!ModList.get().isLoaded("xaeroworldmap")) {
            return;
        }
        if (WorldMap.INSTANCE == null || WorldMap.mapElementRenderHandler == null) {
            return;
        }

        TeleportWaypointContext context = new TeleportWaypointContext();
        TeleportWaypointWorldProvider provider = new TeleportWaypointWorldProvider();
        TeleportWaypointWorldReader reader = new TeleportWaypointWorldReader();
        TeleportWaypointWorldRenderer renderer = new TeleportWaypointWorldRenderer(context, provider, reader);
        WorldMap.mapElementRenderHandler.add(renderer);
        worldMapRegistered = true;
        TeleportWaypoint.LOGGER.info("[TeleportWaypoint] Xaero World Map integration registered");
    }
}
