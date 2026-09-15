package com.zonlong.teleportwaypoint.structure;

import java.util.Map;
import java.util.Optional;

import com.zonlong.teleportwaypoint.config.CommonConfig;
import com.zonlong.teleportwaypoint.config.StructureWaypointMode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Injects a waypoint into a chunk the first time that chunk is generated.
 *
 * <h2>Trigger</h2>
 * A single gate decides everything: {@link ChunkEvent.Load#isNewChunk()}. Chunks read back from disk
 * (world restart, a player walking back) are never new, so they are skipped. Because a structure has
 * exactly one origin chunk and that chunk is only ever generated once, this also removes the need for
 * any persisted de-duplication list, any "is there already a waypoint here" neighbour check, and any
 * migration for existing saves. Tearing a waypoint down cannot bring it back, because breaking a block
 * does not change how the chunk was obtained.
 *
 * <h2>Threading and deadlock rules</h2>
 * The event fires on the server main thread while the chunk holder's {@code currentlyLoading} field
 * points at this chunk, so writing into <em>this</em> chunk is safe. Reading or loading any
 * <em>other</em> chunk is not: {@code Level#getBlockState(BlockPos)}, {@code getChunkAt},
 * {@code getChunk} and every {@code StructureManager} query would end up in
 * {@code ServerChunkCache#getChunk}'s {@code managedBlock}, which waits on work the main thread itself
 * has to perform. All block reads therefore go through the {@link LevelChunk} handed to us.
 *
 * <h2>Safety net</h2>
 * The whole body is wrapped in {@code try/catch (Throwable)}. An exception escaping a chunk load
 * listener completes the chunk future exceptionally and takes the server down, so nothing may escape.
 * The re-entrancy guard is not a fix for known behaviour: it exists so that a future change which
 * forces another chunk to load is noticed immediately, since a skipped chunk never gets a second
 * chance.
 *
 * <p>Stateless apart from the re-entrancy flag.
 */
public final class StructureWaypointHandler {

    /** Upper bound on structures handled per chunk. */
    public static final int MAX_STRUCTURES_PER_CHUNK = 2;

    private static volatile boolean inHandler = false;

    private StructureWaypointHandler() {
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        // Gate 0: the master switch, deliberately the very first check so that turning the feature off
        // leaves essentially nothing on the chunk-load path -- no instanceof, no chunk structure lookup,
        // no logging. ModConfigSpec.ConfigValue#get() is a cached field read, so this is nanoseconds.
        //
        // Because it sits ahead of every other gate, toggling it only takes effect after a server
        // restart. That is the documented trade-off for making the off state this cheap.
        if (!Boolean.TRUE.equals(CommonConfig.ENABLE_STRUCTURE_WAYPOINTS.get())) {
            return;
        }
        // Gate 1: server side only. isNewChunk() is documented to be true only on the logical server.
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // Gate 2: the event hands out a ChunkAccess; only a full LevelChunk has block entities.
        if (!(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        // Gate 3: the one and only trigger gate. Everything read back from disk exits here.
        if (!event.isNewChunk()) {
            return;
        }
        // Gate 4: the cheapest rejection there is. Most chunks have no structure at all.
        if (chunk.getAllStarts().isEmpty() && chunk.getAllReferences().isEmpty()) {
            return;
        }

        if (inHandler) {
            StructureWaypointDebug.reentered(chunk.getPos());
            return;
        }

        inHandler = true;
        try {
            handle(level, chunk);
        } catch (Throwable throwable) {
            StructureWaypointDebug.failed(chunk.getPos(), throwable);
        } finally {
            inHandler = false;
        }
    }

    /**
     * The gated body, split out so tests can drive it directly: the engine's "new chunk" flag cannot be
     * faked, but everything after gates 1 and 2 can be exercised through this method.
     */
    static void handle(ServerLevel level, LevelChunk chunk) {
        Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        int handled = 0;

        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
            if (handled >= MAX_STRUCTURES_PER_CHUNK) {
                StructureWaypointDebug.limitReached(chunk.getPos(), MAX_STRUCTURES_PER_CHUNK);
                break;
            }

            StructureStart start = entry.getValue();
            if (start == null || !start.isValid()) {
                continue;
            }

            Optional<ResourceLocation> structureId = StructureWaypointNaming.structureId(structures, entry.getKey());
            if (structureId.isEmpty()) {
                continue;
            }

            if (!StructureTagFilter.allows(structures, entry.getKey())) {
                StructureWaypointDebug.skippedByTag(
                        structureId.get(),
                        CommonConfig.STRUCTURE_WAYPOINT_MODE.get() == StructureWaypointMode.WHITELIST);
                continue;
            }
            // Counted after the filter on purpose: a structure excluded by the list must not consume the
            // per-chunk budget that a listed structure could have used.
            handled++;

            Optional<String> waypointId = StructureWaypointNaming.derive(structures, start);
            if (waypointId.isEmpty()) {
                continue;
            }

            Optional<BlockPos> placement = StructureWaypointScanner.findPlacement(chunk, start, structureId.get());
            if (placement.isEmpty()) {
                StructureWaypointDebug.noPlacement(structureId.get(), chunk.getPos());
                continue;
            }

            StructureWaypointPlacer.place(level, chunk, placement.get(), waypointId.get(), structureId.get());
        }
    }
}
