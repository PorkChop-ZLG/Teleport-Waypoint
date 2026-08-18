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
 * Kept in sync by the snapshot and incremental payloads. Used by the in-game GUI,
 * the BER state colors, and the optional Xaero map integrations.
 */
public final class ClientWaypointState {
    private static Map<UUID, ClientWaypointInfo> waypoints = Map.of();
    private static List<ActivatedWaypointInfo> activated = List.of();
    private static Set<UUID> activatedUids = Set.of();
    private static int revision;
    private static boolean initialized;
    private static final List<Runnable> pending = new ArrayList<>();

    private ClientWaypointState() {
    }

    public static int getRevision() {
        return revision;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /** Applies one page of the full snapshot; resets on page 0 and flushes queued increments when done. */
    public static void applySnapshot(List<WaypointSyncInfo> infos, int page, boolean done) {
        if (page == 0) {
            // A new snapshot invalidates any previous partial state and queued increments.
            initialized = false;
            pending.clear();
            waypoints = Map.of();
        }
        Map<UUID, ClientWaypointInfo> map = new HashMap<>(waypoints);
        for (WaypointSyncInfo info : infos) {
            map.put(info.uid(), toClientInfo(info));
        }
        waypoints = Map.copyOf(map);
        if (done) {
            initialized = true;
            revision++;
            flushPending();
        }
    }

    public static void applyAdd(WaypointSyncInfo info) {
        if (!initialized) {
            pending.add(() -> applyAdd(info));
            return;
        }
        Map<UUID, ClientWaypointInfo> map = new HashMap<>(waypoints);
        map.put(info.uid(), toClientInfo(info));
        waypoints = Map.copyOf(map);
        revision++;
    }

    public static void applyUpdate(WaypointSyncInfo info) {
        if (!initialized) {
            pending.add(() -> applyUpdate(info));
            return;
        }
        Map<UUID, ClientWaypointInfo> map = new HashMap<>(waypoints);
        map.put(info.uid(), toClientInfo(info));
        waypoints = Map.copyOf(map);

        // Keep activated-list display names in sync for every player who has this waypoint activated.
        List<ActivatedWaypointInfo> list = new ArrayList<>(activated);
        boolean activatedChanged = false;
        for (int i = 0; i < list.size(); i++) {
            ActivatedWaypointInfo activatedInfo = list.get(i);
            if (activatedInfo.uid().equals(info.uid())) {
                list.set(i, new ActivatedWaypointInfo(activatedInfo.uid(), activatedInfo.pocket(), info.name()));
                activatedChanged = true;
            }
        }
        if (activatedChanged) {
            activated = List.copyOf(list);
        }
        revision++;
    }

    public static void applyRemove(UUID uid) {
        if (!initialized) {
            pending.add(() -> applyRemove(uid));
            return;
        }
        if (!waypoints.containsKey(uid)) {
            return;
        }
        Map<UUID, ClientWaypointInfo> map = new HashMap<>(waypoints);
        map.remove(uid);
        waypoints = Map.copyOf(map);
        revision++;
    }

    public static void applyActivatedAdd(ActivatedWaypointInfo info) {
        if (!initialized) {
            pending.add(() -> applyActivatedAdd(info));
            return;
        }
        List<ActivatedWaypointInfo> list = new ArrayList<>(activated);
        if (activatedUids.contains(info.uid())) {
            list.replaceAll(existing -> existing.uid().equals(info.uid()) ? info : existing);
        } else {
            list.add(info);
        }
        activated = List.copyOf(list);
        Set<UUID> set = new HashSet<>(activatedUids);
        set.add(info.uid());
        activatedUids = Set.copyOf(set);
        revision++;
    }

    public static void applyActivatedRemove(UUID uid) {
        if (!initialized) {
            pending.add(() -> applyActivatedRemove(uid));
            return;
        }
        if (!activatedUids.contains(uid)) {
            return;
        }
        activated = activated.stream().filter(info -> !info.uid().equals(uid)).toList();
        Set<UUID> set = new HashSet<>(activatedUids);
        set.remove(uid);
        activatedUids = Set.copyOf(set);
        revision++;
    }

    public static void setAllWaypoints(List<WaypointSyncInfo> infos) {
        Map<UUID, ClientWaypointInfo> map = new HashMap<>();
        for (WaypointSyncInfo info : infos) {
            map.put(info.uid(), toClientInfo(info));
        }
        waypoints = Map.copyOf(map);
        initialized = true;
        flushPending();
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

    private static ClientWaypointInfo toClientInfo(WaypointSyncInfo info) {
        return new ClientWaypointInfo(
                info.uid(),
                ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, info.dimension()),
                info.pos(),
                info.pocket(),
                info.name());
    }

    private static void flushPending() {
        List<Runnable> copy = new ArrayList<>(pending);
        pending.clear();
        for (Runnable runnable : copy) {
            runnable.run();
        }
    }
}
