package com.zonlong.teleportwaypoint.client.xaero;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.zonlong.teleportwaypoint.client.ClientWaypointInfo;
import com.zonlong.teleportwaypoint.client.ClientWaypointState;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.resources.ResourceLocation;

import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.world.MinimapWorldManager;

/**
 * Injects teleport waypoints into Xaero's minimap custom waypoint store. The
 * minimap then renders them with names, colors and symbols using Xaero's own
 * waypoint rendering.
 */
public final class XaeroMinimapIntegration {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final Map<UUID, Integer> UID_TO_ID = new HashMap<>();
    private static final Map<ResourceLocation, Set<Integer>> OWNED = new HashMap<>();
    private static int lastRevision = -1;
    private static boolean lastShowWaypoints = true;
    private static boolean lastShowWaypointNames = true;
    private static boolean initialized;

    private XaeroMinimapIntegration() {
    }

    public static void init() {
        initialized = true;
        sync();
    }

    public static void sync() {
        if (!initialized) {
            return;
        }
        int revision = ClientWaypointState.getRevision();
        boolean showWaypoints = XaeroIntegration.showWaypoints();
        boolean showWaypointNames = XaeroIntegration.showWaypointNames();
        if (revision == lastRevision
                && showWaypoints == lastShowWaypoints
                && showWaypointNames == lastShowWaypointNames) {
            return;
        }

        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) {
            // Session not ready yet: do not consume the revision/config state.
            return;
        }
        MinimapWorldManager manager = session.getWorldManager();

        lastRevision = revision;
        lastShowWaypoints = showWaypoints;
        lastShowWaypointNames = showWaypointNames;

        Map<ResourceLocation, List<ClientWaypointInfo>> desired = new HashMap<>();
        if (showWaypoints) {
            for (ClientWaypointInfo info : ClientWaypointState.getWaypoints()) {
                desired.computeIfAbsent(info.dimension().location(), k -> new ArrayList<>()).add(info);
            }
        }

        removeStale(manager, desired);
        addOrUpdate(manager, desired);
    }

    private static void removeStale(MinimapWorldManager manager,
                                    Map<ResourceLocation, List<ClientWaypointInfo>> desired) {
        Iterator<Map.Entry<ResourceLocation, Set<Integer>>> it = OWNED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ResourceLocation, Set<Integer>> entry = it.next();
            ResourceLocation dimension = entry.getKey();
            Int2ObjectMap<Waypoint> map = manager.getCustomWaypoints(dimension);
            Set<Integer> owned = entry.getValue();

            if (!desired.containsKey(dimension)) {
                for (int id : owned) {
                    map.remove(id);
                }
                it.remove();
                continue;
            }

            Set<UUID> desiredUids = new HashSet<>();
            for (ClientWaypointInfo info : desired.get(dimension)) {
                desiredUids.add(info.uid());
            }

            owned.removeIf(id -> {
                UUID uid = findUid(id);
                if (uid == null || !desiredUids.contains(uid)) {
                    map.remove(id);
                    if (uid != null) {
                        UID_TO_ID.remove(uid);
                    }
                    return true;
                }
                return false;
            });
        }
    }

    private static void addOrUpdate(MinimapWorldManager manager,
                                    Map<ResourceLocation, List<ClientWaypointInfo>> desired) {
        for (Map.Entry<ResourceLocation, List<ClientWaypointInfo>> entry : desired.entrySet()) {
            ResourceLocation dimension = entry.getKey();
            Int2ObjectMap<Waypoint> map = manager.getCustomWaypoints(dimension);
            Set<Integer> owned = OWNED.computeIfAbsent(dimension, k -> new HashSet<>());

            for (ClientWaypointInfo info : entry.getValue()) {
                int id = UID_TO_ID.computeIfAbsent(info.uid(), k -> NEXT_ID.getAndIncrement());
                owned.add(id);

                boolean activated = ClientWaypointState.isActivated(info.uid());
                WaypointColor color = activated
                        ? (info.pocket() ? WaypointColor.GREEN : WaypointColor.AQUA)
                        : WaypointColor.GRAY;
                String symbol = info.pocket() ? "P" : "W";
                String displayName = XaeroIntegration.showWaypointNames()
                        ? info.displayName().getString()
                        : "";

                Waypoint waypoint = new Waypoint(
                        info.pos().getX(),
                        info.pos().getY(),
                        info.pos().getZ(),
                        displayName,
                        symbol,
                        color);
                waypoint.setTemporary(true);
                map.put(id, waypoint);
            }
        }
    }

    private static UUID findUid(int id) {
        for (Map.Entry<UUID, Integer> entry : UID_TO_ID.entrySet()) {
            if (entry.getValue() == id) {
                return entry.getKey();
            }
        }
        return null;
    }
}
