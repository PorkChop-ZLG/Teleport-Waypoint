package com.zonlong.teleportwaypoint.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.zonlong.teleportwaypoint.network.ActivatedWaypointInfo;
import com.zonlong.teleportwaypoint.network.WaypointSyncInfo;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Client-side copy of all known waypoints plus the local player's activated set.
 * Kept in sync by {@code SyncAllWaypointsPayload} and
 * {@code SyncActivatedWaypointsPayload}. Used by the in-game GUI, the BER state
 * colors, and the optional Xaero map integrations.
 */
public final class ClientWaypointState {
    private static Map<UUID, ClientWaypointInfo> waypoints = Map.of();
    private static List<ActivatedWaypointInfo> activated = List.of();
    private static Set<UUID> activatedUids = Set.of();
    private static int revision;

    private ClientWaypointState() {
    }

    public static int getRevision() {
        return revision;
    }

    public static void setAllWaypoints(List<WaypointSyncInfo> infos) {
        Map<UUID, ClientWaypointInfo> map = new HashMap<>();
        for (WaypointSyncInfo info : infos) {
            map.put(info.uid(), new ClientWaypointInfo(
                    info.uid(),
                    ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, info.dimension()),
                    info.pos(),
                    info.pocket(),
                    info.name()));
        }
        waypoints = Map.copyOf(map);
        revision++;
    }

    public static void upsertWaypoint(WaypointSyncInfo info) {
        Map<UUID, ClientWaypointInfo> map = new HashMap<>(waypoints);
        map.put(info.uid(), new ClientWaypointInfo(
                info.uid(),
                ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, info.dimension()),
                info.pos(),
                info.pocket(),
                info.name()));
        waypoints = Map.copyOf(map);
        revision++;
    }

    public static void removeWaypoint(UUID uid) {
        if (!waypoints.containsKey(uid)) {
            return;
        }
        Map<UUID, ClientWaypointInfo> map = new HashMap<>(waypoints);
        map.remove(uid);
        waypoints = Map.copyOf(map);
        removeActivated(uid);
        revision++;
    }

    public static List<ClientWaypointInfo> getWaypoints() {
        return List.copyOf(waypoints.values());
    }

    public static List<ClientWaypointInfo> getWaypointsIn(ResourceKey<Level> dimension) {
        List<ClientWaypointInfo> result = new ArrayList<>();
        for (ClientWaypointInfo info : waypoints.values()) {
            if (info.dimension().equals(dimension)) {
                result.add(info);
            }
        }
        return result;
    }

    public static ClientWaypointInfo getWaypoint(UUID uid) {
        return waypoints.get(uid);
    }

    public static boolean isActivated(UUID uid) {
        return uid != null && activatedUids.contains(uid);
    }

    public static List<ActivatedWaypointInfo> getActivated() {
        return activated;
    }

    public static void setActivated(List<ActivatedWaypointInfo> infos) {
        activated = List.copyOf(infos);
        Set<UUID> set = new HashSet<>();
        for (ActivatedWaypointInfo info : infos) {
            set.add(info.uid());
        }
        activatedUids = Set.copyOf(set);
        revision++;
    }

    public static void removeActivated(UUID uid) {
        if (uid == null) {
            return;
        }
        activated = activated.stream().filter(info -> !info.uid().equals(uid)).toList();
        if (activatedUids.contains(uid)) {
            Set<UUID> set = new HashSet<>(activatedUids);
            set.remove(uid);
            activatedUids = Set.copyOf(set);
        }
        revision++;
    }
}
