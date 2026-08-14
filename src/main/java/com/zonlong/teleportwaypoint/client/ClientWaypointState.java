package com.zonlong.teleportwaypoint.client;

import java.util.List;
import java.util.UUID;

import com.zonlong.teleportwaypoint.network.ActivatedWaypointInfo;

/**
 * Client-side copy of the local player's activated waypoints, used for red/blue rendering and GUI
 * target lists. Kept in sync by {@code SyncActivatedWaypointsPayload}.
 */
public class ClientWaypointState {
    private static List<ActivatedWaypointInfo> activated = List.of();

    public static boolean isActivated(UUID uid) {
        return activated.stream().anyMatch(info -> info.uid().equals(uid));
    }

    public static List<ActivatedWaypointInfo> getActivated() {
        return activated;
    }

    public static void setActivated(List<ActivatedWaypointInfo> infos) {
        activated = List.copyOf(infos);
    }

    public static void removeActivated(UUID uid) {
        activated = activated.stream().filter(info -> !info.uid().equals(uid)).toList();
    }
}
