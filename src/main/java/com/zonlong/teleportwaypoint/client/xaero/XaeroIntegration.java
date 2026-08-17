package com.zonlong.teleportwaypoint.client.xaero;

import java.util.ArrayList;

import com.zonlong.teleportwaypoint.Config;
import com.zonlong.teleportwaypoint.TeleportWaypoint;

import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import xaero.lib.common.config.option.BooleanConfigOption;
import xaero.lib.common.config.option.ConfigOption;
import xaero.lib.common.config.option.ConfigOptionManager;
import xaero.map.WorldMap;

/**
 * Entry point for Xaero integrations. This class is only loaded (via reflection)
 * when at least one Xaero map mod is present, so it may safely reference Xaero
 * types.
 */
public final class XaeroIntegration {
    private static boolean worldMapRegistered;
    private static boolean minimapRegistered;
    private static boolean configAttempted;

    private static ConfigOption<Boolean> xaeroShowWaypoints;
    private static ConfigOption<Boolean> xaeroShowWaypointNames;

    private XaeroIntegration() {
    }

    public static void tick() {
        tryRegisterXaeroConfig();
        registerWorldMap();
        registerMinimap();
        XaeroMinimapIntegration.sync();
    }

    public static boolean showWaypoints() {
        if (xaeroShowWaypoints != null && WorldMap.INSTANCE != null) {
            return WorldMap.INSTANCE.getConfigs().getClientConfigManager().getEffective(xaeroShowWaypoints);
        }
        return Config.SHOW_WAYPOINTS.get();
    }

    public static boolean showWaypointNames() {
        if (xaeroShowWaypointNames != null && WorldMap.INSTANCE != null) {
            return WorldMap.INSTANCE.getConfigs().getClientConfigManager().getEffective(xaeroShowWaypointNames);
        }
        return Config.SHOW_WAYPOINT_NAMES.get();
    }

    private static void tryRegisterXaeroConfig() {
        if (configAttempted) {
            return;
        }
        configAttempted = true;
        if (!ModList.get().isLoaded("xaeroworldmap") || WorldMap.INSTANCE == null) {
            return;
        }
        try {
            ConfigOptionManager manager = WorldMap.INSTANCE.getConfigs().getConfigOptionManager();
            xaeroShowWaypoints = createBooleanOption(
                    "teleportwaypoint.show_waypoints",
                    "Show Teleport Waypoints",
                    true);
            xaeroShowWaypointNames = createBooleanOption(
                    "teleportwaypoint.show_waypoint_names",
                    "Show Teleport Waypoint Names",
                    true);
            manager.register(xaeroShowWaypoints);
            manager.register(xaeroShowWaypointNames);
            TeleportWaypoint.LOGGER.info("[TeleportWaypoint] Registered Xaero config options");
        } catch (Exception e) {
            TeleportWaypoint.LOGGER.info("[TeleportWaypoint] Xaero config options unavailable, using mod config fallback: {}", e.toString());
            xaeroShowWaypoints = null;
            xaeroShowWaypointNames = null;
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

    private static void registerMinimap() {
        if (minimapRegistered) {
            return;
        }
        if (!ModList.get().isLoaded("xaerominimap")) {
            return;
        }
        if (xaero.common.HudMod.INSTANCE == null
                || xaero.common.HudMod.INSTANCE.getMinimap() == null) {
            return;
        }
        XaeroMinimapIntegration.init();
        minimapRegistered = true;
        TeleportWaypoint.LOGGER.info("[TeleportWaypoint] Xaero Minimap integration registered");
    }
}
