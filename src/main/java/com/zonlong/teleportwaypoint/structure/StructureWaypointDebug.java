package com.zonlong.teleportwaypoint.structure;

import com.zonlong.teleportwaypoint.TeleportWaypoint;
import com.zonlong.teleportwaypoint.config.CommonConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/**
 * Debug output for chunk-load waypoint injection.
 *
 * <p>Every DEBUG line is gated by the {@code structureWaypoints.debugMode} config option and is
 * written in English only, so log files never contain mojibake. The two WARN helpers are safety-net
 * signals and are intentionally not gated.
 */
public final class StructureWaypointDebug {

    private StructureWaypointDebug() {
    }

    /** Writes a DEBUG line only when debug logging is enabled. */
    public static void debug(String format, Object... args) {
        if (Boolean.TRUE.equals(CommonConfig.DEBUG_MODE.get())) {
            TeleportWaypoint.LOGGER.debug(format, args);
        }
    }

    public static void noPlacement(ResourceLocation structureId, ChunkPos chunkPos) {
        debug("structure waypoint: no valid placement structure={} chunk={}", structureId, chunkPos);
    }

    public static void budgetExhausted(ResourceLocation structureId, ChunkPos chunkPos, int calls) {
        debug("structure waypoint: probe budget exhausted structure={} chunk={} calls={}", structureId, chunkPos, calls);
    }

    public static void placed(ResourceLocation structureId, BlockPos pos, boolean waterlogged) {
        debug("structure waypoint: placed structure={} pos={} waterlogged={}", structureId, pos, waterlogged);
    }

    public static void skippedByTag(ResourceLocation structureId, boolean whitelistMode) {
        debug("structure waypoint: skipped by tag structure={} mode={}", structureId, whitelistMode ? "WHITELIST" : "BLACKLIST");
    }

    public static void limitReached(ChunkPos chunkPos, int limit) {
        debug("structure waypoint: per-chunk structure limit reached chunk={} limit={}", chunkPos, limit);
    }

    /**
     * Reports a re-entrant chunk load. This is a safety net, not a fix for known behaviour: if it ever
     * fires, something inside the handler forced another chunk to load, and the skipped chunk will never
     * get a second chance because the handler only runs for newly generated chunks.
     */
    public static void reentered(ChunkPos chunkPos) {
        TeleportWaypoint.LOGGER.warn(
                "structure waypoint: re-entrant chunk load detected, skipping chunk={}. "
                        + "A forced chunk load was introduced in the handler; see the forbidden API list.",
                chunkPos);
    }

    /** Reports a Throwable escaping the handler body. Never rethrown: an escaping exception crashes the server. */
    public static void failed(ChunkPos chunkPos, Throwable throwable) {
        TeleportWaypoint.LOGGER.warn("structure waypoint: handler failed, chunk left untouched chunk={}", chunkPos, throwable);
    }
}
