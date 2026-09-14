package com.zonlong.teleportwaypoint.structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Pure geometry: picks where inside a chunk an injected waypoint should go.
 *
 * <p>The acceptance parameter is deliberately narrowed to {@link LevelChunk} rather than
 * {@code Level}. Block reads therefore go through {@code LevelChunk.getBlockState}, which is a plain
 * array read; the APIs that would deadlock the server from inside {@code ChunkEvent.Load}
 * ({@code Level#getBlockState}, {@code getChunkAt}, {@code getChunk}, every {@code StructureManager}
 * query) are simply not in scope here.
 *
 * <p>Traversal order is strictly layer &rarr; pass &rarr; piece &rarr; column &rarr; y, matching the
 * design document. The ordering keys form a total order, so the same structure always lands on the
 * same block.
 *
 * <p>Stateless: no instance or mutable static fields, and the probe budget counter is a local.
 */
public final class StructureWaypointScanner {

    /** How far up to look for a solid roof before a candidate counts as "indoors". */
    public static final int ROOF_SCAN_DEPTH = 8;

    /** Hard cap on downward y steps per column in the chunk-wide fallback layer. */
    public static final int L2_SCAN_LIMIT = 64;

    /** Cross-layer cap on {@code isValidPlacement} calls for a single structure. */
    public static final int PROBE_BUDGET = 2000;



    private StructureWaypointScanner() {
    }

    /**
     * Returns the chosen position, or empty when every candidate fails or the probe budget runs out.
     * The returned position is always inside {@code chunk}'s own 16x16 column.
     *
     * @param structureId used for debug output only; may be null
     */
    public static Optional<BlockPos> findPlacement(LevelChunk chunk, StructureStart start, ResourceLocation structureId) {
        if (start == null || !start.isValid()) {
            return Optional.empty();
        }
        List<StructurePiece> allPieces = start.getPieces();
        if (allPieces.isEmpty()) {
            return Optional.empty();
        }

        int chunkMinX = chunk.getPos().getMinBlockX();
        int chunkMinZ = chunk.getPos().getMinBlockZ();
        int chunkMaxX = chunkMinX + 15;
        int chunkMaxZ = chunkMinZ + 15;

        // The structure anchor column is the centre of the first piece's box, exactly as
        // StructureStart.placeInChunk computes its `pos` argument. StructureStart.getBoundingBox()
        // must NOT be used: it inflates the box by 12 when terrainAdaptation != NONE.
        BlockPos anchor = allPieces.get(0).getBoundingBox().getCenter();

        List<StructurePiece> pieces = orderPieces(allPieces, chunkMinX, chunkMinZ, chunkMaxX, chunkMaxZ);
        int[] probeCalls = {0};

        // ---- L1: inside the structure -------------------------------------------------------
        for (boolean requireRoof : new boolean[] {true, false}) {
            for (StructurePiece piece : pieces) {
                BoundingBox box = piece.getBoundingBox();
                int yTop = Math.min(box.maxY(), chunk.getMaxBuildHeight() - 1);
                int yBottom = Math.max(box.minY(), chunk.getMinBuildHeight() + 1);
                if (yTop < yBottom) {
                    continue;
                }

                List<Column> columns = orderColumnsIntersecting(
                        box, chunkMinX, chunkMinZ, chunkMaxX, chunkMaxZ, box.getCenter(), anchor);

                for (Column column : columns) {
                    for (int y = yTop; y >= yBottom; y--) {
                        if (probeCalls[0] >= PROBE_BUDGET) {
                            StructureWaypointDebug.budgetExhausted(structureId, chunk.getPos(), probeCalls[0]);
                            return Optional.empty();
                        }
                        probeCalls[0]++;
                        BlockPos candidate = new BlockPos(column.x(), y, column.z());
                        if (isValidPlacement(chunk, candidate)
                                && (!requireRoof || roofed(chunk, candidate, ROOF_SCAN_DEPTH))) {
                            return insideChunk(chunk, candidate);
                        }
                    }
                }
            }
        }

        // ---- L2: whole-chunk fallback -------------------------------------------------------
        // The fallback scans downward from just above the structure rather than from the world ceiling.
        // Starting at the ceiling would waste the whole L2_SCAN_LIMIT budget on empty sky and make the
        // layer useless for anything below y = maxBuildHeight - L2_SCAN_LIMIT (that is, for every
        // overworld and Nether structure). Anchoring the start to the structure keeps the layer's
        // purpose -- an anchor close to the structure -- while staying a bounded amount of work.
        int startY = chunk.getMinBuildHeight() + 1;
        for (StructurePiece candidatePiece : allPieces) {
            startY = Math.max(startY, candidatePiece.getBoundingBox().maxY() + ROOF_SCAN_DEPTH);
        }
        int topY = Math.min(startY, chunk.getMaxBuildHeight() - 1);

        BlockPos chunkCenter = new BlockPos(chunkMinX + 8, chunk.getMinBuildHeight(), chunkMinZ + 8);
        List<Column> columns = orderColumnsInChunk(chunkMinX, chunkMinZ, chunkMaxX, chunkMaxZ, chunkCenter, anchor);

        for (boolean requireRoof : new boolean[] {true, false}) {
            for (Column column : columns) {
                int scanned = 0;
                for (int y = topY; y >= chunk.getMinBuildHeight() + 1; y--) {
                    if (++scanned > L2_SCAN_LIMIT) {
                        break;
                    }
                    if (probeCalls[0] >= PROBE_BUDGET) {
                        StructureWaypointDebug.budgetExhausted(structureId, chunk.getPos(), probeCalls[0]);
                        return Optional.empty();
                    }
                    probeCalls[0]++;
                    BlockPos candidate = new BlockPos(column.x(), y, column.z());
                    if (isValidPlacement(chunk, candidate)
                            && (!requireRoof || roofed(chunk, candidate, ROOF_SCAN_DEPTH))) {
                        return insideChunk(chunk, candidate);
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Returns whether a waypoint may be placed at {@code pos}: solid floor below, room for the
     * roughly 1.15-block-tall model above, no block entities involved, and water or air only.
     * Lava anywhere in the three checked blocks rejects the position outright.
     */
    public static boolean isValidPlacement(LevelChunk chunk, BlockPos pos) {
        int y = pos.getY();
        if (y <= chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight()) {
            return false;
        }

        BlockPos belowPos = pos.below();
        var below = chunk.getBlockState(belowPos);
        if (below.isAir() || !below.getFluidState().isEmpty()) {
            return false;
        }
        if (!below.isFaceSturdy(chunk, belowPos, Direction.UP)) {
            return false;
        }
        // Check the block state first: getBlockEntity would promote a pending block entity and
        // deserialize its NBT, which is the easiest performance trap in this feature.
        if (below.hasBlockEntity()) {
            return false;
        }

        for (int k = 0; k < 3; k++) {
            int blockY = y + k;
            if (blockY >= chunk.getMaxBuildHeight()) {
                return false;
            }
            var state = chunk.getBlockState(pos.above(k));
            // Lava is excluded outright: the waypoint block only supports waterlogging, so a waypoint
            // placed in lava would simply burn away and look like it was never placed.
            if (state.getFluidState().is(Fluids.LAVA)) {
                return false;
            }
            // Water is a legal target, not an obstacle. The waypoint block is waterloggable and only
            // occupies the lower half of its block (1.15 blocks of visible height), so it fits under
            // water; the placer turns the water into a waterlogged block instead of leaving a cavity.
            if (!state.isAir() && !state.canBeReplaced() && !state.getFluidState().is(Fluids.WATER)) {
                return false;
            }
            if (state.hasBlockEntity()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether the first non-air, non-fluid block above {@code pos} within {@code n} blocks is
     * a solid ceiling. Air and fluids do not count as a roof, they are skipped over. Used as a
     * per-layer pass preference, never as a hard constraint.
     */
    public static boolean roofed(LevelChunk chunk, BlockPos pos, int n) {
        for (int k = 1; k <= n; k++) {
            if (pos.getY() + k >= chunk.getMaxBuildHeight()) {
                return false;
            }
            BlockPos above = pos.above(k);
            var state = chunk.getBlockState(above);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                continue;
            }
            return state.isFaceSturdy(chunk, above, Direction.DOWN);
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------
    // ordering helpers
    // ------------------------------------------------------------------------------------------

    /** Keeps pieces that intersect the chunk column and sorts them by descending box volume. */
    private static List<StructurePiece> orderPieces(
            List<StructurePiece> allPieces, int chunkMinX, int chunkMinZ, int chunkMaxX, int chunkMaxZ) {
        List<StructurePiece> result = new ArrayList<>(allPieces.size());
        for (StructurePiece piece : allPieces) {
            BoundingBox box = piece.getBoundingBox();
            if (isDegenerate(box)) {
                // Rotated or mirrored pieces can collapse to zero volume; they are not searchable.
                continue;
            }
            if (box.intersects(chunkMinX, chunkMinZ, chunkMaxX, chunkMaxZ)) {
                result.add(piece);
            }
        }
        result.sort(Comparator
                .comparingLong((StructurePiece piece) -> -volume(piece.getBoundingBox()))
                .thenComparingInt(piece -> piece.getBoundingBox().minX())
                .thenComparingInt(piece -> piece.getBoundingBox().minZ()));
        return result;
    }

    /**
     * L1 column order: nearest to the piece centre, then nearest to the structure anchor column,
     * then x, then z. Together with the descending piece loop this forms the design's total order.
     */
    private static List<Column> orderColumnsIntersecting(
            BoundingBox box, int chunkMinX, int chunkMinZ, int chunkMaxX, int chunkMaxZ, BlockPos pieceCenter, BlockPos anchor) {
        int minX = Math.max(box.minX(), chunkMinX);
        int maxX = Math.min(box.maxX(), chunkMaxX);
        int minZ = Math.max(box.minZ(), chunkMinZ);
        int maxZ = Math.min(box.maxZ(), chunkMaxZ);
        if (minX > maxX || minZ > maxZ) {
            return List.of();
        }
        return orderColumns(minX, maxX, minZ, maxZ,
                column -> chebyshev(column.x(), column.z(), pieceCenter.getX(), pieceCenter.getZ()),
                anchor);
    }

    /**
     * L2 column order (this layer has no piece): nearest to the chunk centre first, so the fallback
     * waypoint stays away from unloaded neighbours, then nearest to the structure anchor column,
     * then x, then z.
     */
    private static List<Column> orderColumnsInChunk(
            int chunkMinX, int chunkMinZ, int chunkMaxX, int chunkMaxZ, BlockPos chunkCenter, BlockPos anchor) {
        return orderColumns(chunkMinX, chunkMaxX, chunkMinZ, chunkMaxZ,
                column -> chebyshev(column.x(), column.z(), chunkCenter.getX(), chunkCenter.getZ()),
                anchor);
    }

    private static List<Column> orderColumns(
            int minX, int maxX, int minZ, int maxZ, java.util.function.ToIntFunction<Column> primary, BlockPos anchor) {
        List<Column> columns = new ArrayList<>((maxX - minX + 1) * (maxZ - minZ + 1));
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                columns.add(new Column(x, z));
            }
        }
        columns.sort(Comparator
                .comparingInt(primary::applyAsInt)
                .thenComparingInt(column -> chebyshev(column.x(), column.z(), anchor.getX(), anchor.getZ()))
                .thenComparingInt(Column::x)
                .thenComparingInt(Column::z));
        return columns;
    }

    private static int chebyshev(int x1, int z1, int x2, int z2) {
        return Math.max(Math.abs(x1 - x2), Math.abs(z1 - z2));
    }

    private static boolean isDegenerate(BoundingBox box) {
        return box.maxX() < box.minX() || box.maxY() < box.minY() || box.maxZ() < box.minZ();
    }

    private static long volume(BoundingBox box) {
        long x = (long) box.maxX() - box.minX() + 1L;
        long y = (long) box.maxY() - box.minY() + 1L;
        long z = (long) box.maxZ() - box.minZ() + 1L;
        return x * y * z;
    }

    /**
     * Final guard: the placement must stay inside the event chunk's own 16x16 column. A scanner
     * result outside it means a piece box or a y range was mis-clamped, so drop it rather than
     * write outside the chunk.
     */
    private static Optional<BlockPos> insideChunk(LevelChunk chunk, BlockPos pos) {
        if ((pos.getX() >> 4) != chunk.getPos().x || (pos.getZ() >> 4) != chunk.getPos().z) {
            StructureWaypointDebug.debug(
                    "structure waypoint: scanner produced out-of-chunk pos {} for chunk {}", pos, chunk.getPos());
            return Optional.empty();
        }
        return Optional.of(pos);
    }


    /** An x/z column inside the chunk being scanned. */
    private record Column(int x, int z) {
    }
}
