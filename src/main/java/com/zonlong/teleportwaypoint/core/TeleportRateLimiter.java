package com.zonlong.teleportwaypoint.core;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.zonlong.teleportwaypoint.config.CommonConfig;

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
        long cooldownMs = CommonConfig.TELEPORT_COOLDOWN_TICKS.get() * 50L;
        if (cooldownMs <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        while (true) {
            Long previous = LAST_TELEPORT.get(playerId);
            if (previous == null) {
                if (LAST_TELEPORT.putIfAbsent(playerId, now) == null) {
                    return true;
                }
            } else if (now - previous >= cooldownMs) {
                if (LAST_TELEPORT.replace(playerId, previous, now)) {
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    public static void remove(UUID playerId) {
        LAST_TELEPORT.remove(playerId);
    }
}
