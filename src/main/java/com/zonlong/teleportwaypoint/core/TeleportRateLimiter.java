package com.zonlong.teleportwaypoint.core;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.zonlong.teleportwaypoint.Config;

/**
 * Server-side per-player cooldown for teleport requests. Shared by the in-game
 * GUI teleport and the Xaero map teleport so neither path can bypass the other.
 */
public final class TeleportRateLimiter {
    private static final Map<UUID, Long> LAST_TELEPORT = new ConcurrentHashMap<>();

    private TeleportRateLimiter() {
    }

    /**
     * Returns true if the player may teleport now, false if they must wait.
     */
    public static boolean tryAcquire(UUID playerId) {
        long cooldownMs = Config.TELEPORT_COOLDOWN_MS.get();
        if (cooldownMs <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long previous = LAST_TELEPORT.putIfAbsent(playerId, now);
        if (previous == null) {
            return true;
        }
        if (now - previous >= cooldownMs) {
            LAST_TELEPORT.put(playerId, now);
            return true;
        }
        return false;
    }

    public static void remove(UUID playerId) {
        LAST_TELEPORT.remove(playerId);
    }
}
