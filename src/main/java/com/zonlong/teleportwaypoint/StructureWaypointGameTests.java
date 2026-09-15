package com.zonlong.teleportwaypoint;

import static net.minecraft.world.level.block.Blocks.GLASS;
import static net.minecraft.world.level.block.Blocks.STONE;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.zonlong.teleportwaypoint.block.ModBlocks;
import com.zonlong.teleportwaypoint.block.WaypointBlock;
import com.zonlong.teleportwaypoint.block.entity.WaypointBlockEntity;
import com.zonlong.teleportwaypoint.structure.StructureTagFilter;
import com.zonlong.teleportwaypoint.structure.StructureWaypointPlacer;
import com.zonlong.teleportwaypoint.structure.StructureWaypointScanner;
import com.zonlong.teleportwaypoint.util.Naming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Game tests for chunk-load waypoint injection.
 *
 * <p>Lives in the main source set on purpose: the project deliberately has no {@code src/test} and no
 * test framework dependency, but {@code runs.gameTestServer} is already configured, so annotated
 * classes are picked up from here.
 */
@GameTestHolder(TeleportWaypoint.MODID)
// Templates are prefixed with the simple class name by default, which would resolve to a different
// structure file than the one this mod ships. The template name is given explicitly instead.
@PrefixGameTestTemplate(false)
public final class StructureWaypointGameTests {

    private static final String TEMPLATE = "structurewaypointgametests";

    // ------------------------------------------------------------------------------------------
    // naming: pure functions, no world state involved
    // ------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void naming_vanillaIds(GameTestHelper helper) {
        expectId(helper, "minecraft:ancient_city", "minecraft.ancient_city");
        expectId(helper, "minecraft:end_city", "minecraft.end_city");
        expectId(helper, "minecraft:bastion_remnant", "minecraft.bastion_remnant");
        expectId(helper, "minecraft:desert_pyramid", "minecraft.desert_pyramid");
        expectId(helper, "minecraft:fortress", "minecraft.fortress");
        expectId(helper, "minecraft:igloo", "minecraft.igloo");
        expectId(helper, "minecraft:stronghold", "minecraft.stronghold");
        expectId(helper, "minecraft:swamp_hut", "minecraft.swamp_hut");
        expectId(helper, "minecraft:trial_chambers", "minecraft.trial_chambers");
        // The registry spells these differently from the author's historical display names.
        expectId(helper, "minecraft:jungle_pyramid", "minecraft.jungle_pyramid");
        expectId(helper, "minecraft:monument", "minecraft.monument");
        expectId(helper, "minecraft:mansion", "minecraft.mansion");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void naming_modStructures(GameTestHelper helper) {
        expectId(helper, "betteroceanmonuments:ocean_monument", "betteroceanmonuments.ocean_monument");
        expectId(helper, "betterwitchhuts:witch_hut", "betterwitchhuts.witch_hut");
        expectId(helper, "towns_and_towers:village_plains", "towns_and_towers.village_plains");
        helper.succeed();
    }

    /**
     * Pins the honest boundary from the design document: shortening the translation key prefix does not
     * protect against truncation, because the only hard length limit is on the {@code waypoint_id}.
     */
    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void naming_truncation(GameTestHelper helper) {
        ResourceLocation longId = ResourceLocation.fromNamespaceAndPath(
                "somequiteextremelylongnamespace",
                "an_equally_extremely_long_structure_path_that_goes_on_and_on");
        String derived = Naming.deriveId(longId);

        helper.assertTrue(derived.length() <= Naming.MAX_ID_LENGTH,
                "derived id must respect the 64 character limit, got " + derived.length() + ": " + derived);
        helper.assertTrue(WaypointBlockEntity.isValidId(derived),
                "truncated id must still be valid, got: " + derived);
        helper.assertTrue(!derived.isEmpty() && derived.charAt(derived.length() - 1) != '.'
                        && derived.charAt(derived.length() - 1) != '_',
                "truncated id must not end with a separator, got: " + derived);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void naming_humanize(GameTestHelper helper) {
        helper.assertTrue("Minecraft End City".equals(Naming.humanize("minecraft.end_city")),
                "dots must act as separators, got: " + Naming.humanize("minecraft.end_city"));
        helper.assertTrue("Minecraft End City".equals(Naming.humanize("minecraft_end_city")),
                "underscores must act as separators, got: " + Naming.humanize("minecraft_end_city"));
        helper.assertTrue("Unnamed Waypoint".equals(Naming.humanize("")),
                "empty id must fall back to the empty name, got: " + Naming.humanize(""));
        helper.assertTrue("Unnamed Waypoint".equals(Naming.humanize(null)),
                "null id must fall back to the empty name, got: " + Naming.humanize(null));
        helper.succeed();
    }

    /**
     * Pins the regression that made every pre-0.4.0 waypoint show "Unnamed Waypoint": {@code key()}
     * used to prepend the structured prefix unconditionally, so a legacy bare id such as
     * {@code end_city} produced {@code tpwp.end_city}, which exists in no language file.
     */
    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void naming_legacyIdsStillNameThemselves(GameTestHelper helper) {
        String[] legacyIds = {
            "ancient_city", "bastion_remnant", "desert_pyramid", "end_city", "igloo", "jungle_temple",
            "nether_fortress", "ocean_monument", "stronghold", "swamp_hut", "trial_chambers",
            "woodland_mansion", "village"
        };
        for (String id : legacyIds) {
            String modern = Naming.modernKey(id);
            String legacy = Naming.legacyKey(id);
            helper.assertTrue(("tpwp." + id).equals(modern), "modern key wrong for " + id + ": " + modern);
            helper.assertTrue(("teleportwaypoint.waypoint." + id).equals(legacy),
                    "legacy key wrong for " + id + ": " + legacy);
            // The legacy key must stay reachable: a bare id resolves to it whenever the language has no
            // structured entry, which is always true for these ids.
            helper.assertTrue(legacy.equals(Naming.key(id)) || modern.equals(Naming.key(id)),
                    "key() left both key spaces for " + id + ": " + Naming.key(id));
        }

        // The regression test proper: the display name of a legacy id must not degrade to the unnamed
        // fallback, and its humanized fallback must be the id itself, not the empty marker.
        for (String id : new String[] {"end_city", "jungle_temple", "nether_fortress", "ocean_monument",
                                       "woodland_mansion", "ancient_city"}) {
            String fallback = fallbackOf(Naming.displayName(id));
            helper.assertTrue(fallback != null, "displayName(" + id + ") has no fallback text at all");
            helper.assertTrue(!Naming.EMPTY_FALLBACK_NAME.equals(fallback),
                    "displayName(" + id + ") degraded to the unnamed fallback; legacy name is broken again");
            helper.assertTrue(Naming.humanize(id).equals(fallback),
                    "displayName(" + id + ") fallback should be the humanized id, got: " + fallback);
        }
        helper.succeed();
    }

    /** Extracts the fallback text of a translatable component, or null when it has none. */
    private static String fallbackOf(net.minecraft.network.chat.Component component) {
        if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents) {
            return contents.getFallback();
        }
        return null;
    }

    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void naming_keys(GameTestHelper helper) {
        // modernKey/legacyKey never consult the language table, so they are stable everywhere.
        helper.assertTrue("tpwp.minecraft.end_city".equals(Naming.modernKey("minecraft.end_city")),
                "unexpected modern key: " + Naming.modernKey("minecraft.end_city"));
        // A legacy save stores a BARE id, so this is the pair that matters for backward compatibility.
        helper.assertTrue("teleportwaypoint.waypoint.end_city".equals(Naming.legacyKey("end_city")),
                "a legacy bare id must resolve to the legacy key: " + Naming.legacyKey("end_city"));
        helper.assertTrue("tpwp.empty".equals(Naming.emptyKey()), "unexpected empty key: " + Naming.emptyKey());

        // key() prefers the structured key and falls back to the legacy key when the language lacks it.
        // GameTest runs without language files, so the fallback branch is the one exercised here; the
        // modern branch is covered by the naming_displayName* tests below.
        String resolved = Naming.key("minecraft.end_city");
        helper.assertTrue("tpwp.minecraft.end_city".equals(resolved)
                        || "teleportwaypoint.waypoint.minecraft.end_city".equals(resolved),
                "key() must resolve to one of the two known key spaces, got: " + resolved);

        for (String bad : new String[] {null, "", "BAD ID", "Uppercase", "a..b", ".a", "a."}) {
            String key = Naming.key(bad);
            helper.assertTrue(Naming.emptyKey().equals(key) || Naming.legacyKey(Naming.EMPTY_ID).equals(key),
                    "id '" + bad + "' should fall back to an empty key, got: " + key);
        }
        helper.assertTrue(Naming.MAX_ID_LENGTH == WaypointBlockEntity.MAX_TEXT_LENGTH,
                "Naming.MAX_ID_LENGTH must track WaypointBlockEntity.MAX_TEXT_LENGTH");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void idPattern_acceptsDot(GameTestHelper helper) {
        helper.assertTrue(WaypointBlockEntity.isValidId("minecraft.end_city"), "structured id must be accepted");
        helper.assertTrue(WaypointBlockEntity.isValidId("betteroceanmonuments.ocean_monument"), "mod id must be accepted");
        // Legacy bare values from existing saves must keep working.
        helper.assertTrue(WaypointBlockEntity.isValidId("end_city"), "legacy bare id must stay accepted");
        helper.assertTrue(WaypointBlockEntity.isValidId("empty"), "the empty marker must stay accepted");
        for (String bad : new String[] {"a..b", ".a", "a.", "A", "with space", "空", ""}) {
            helper.assertTrue(!WaypointBlockEntity.isValidId(bad), "id '" + bad + "' must be rejected");
        }
        helper.assertTrue(!WaypointBlockEntity.isValidId(null), "null must be rejected");
        helper.succeed();
    }

    /**
     * Pins that a derived id is never silently downgraded to the empty name, which is what would happen
     * if the id pattern still rejected dots.
     */
    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void displayName_notEmptyFallback(GameTestHelper helper) {
        String[] whitelist = {
            "minecraft:ancient_city", "minecraft:bastion_remnant", "minecraft:desert_pyramid",
            "minecraft:end_city", "minecraft:fortress", "minecraft:igloo", "minecraft:jungle_pyramid",
            "minecraft:mansion", "minecraft:monument", "minecraft:stronghold", "minecraft:swamp_hut",
            "minecraft:trial_chambers"
        };
        for (String raw : whitelist) {
            String derived = Naming.deriveId(ResourceLocation.parse(raw));
            helper.assertTrue(!Naming.emptyKey().equals(Naming.key(derived)),
                    "structure " + raw + " derived an id that falls back to the empty key: " + derived);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------
    // geometry: real chunks, synthetic StructureStarts
    // ------------------------------------------------------------------------------------------

    /**
     * The design's total order must place the waypoint on the highest valid block inside the room, and
     * must do so identically on every run.
     */
    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void scanner_picksHighestRoofedSpot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));

        // Floor at relative y=1 and a stone roof at y=6, with open air between: a simple enclosed room.
        fillBox(helper, 1, 1, 1, 9, 1, 9, STONE.defaultBlockState());
        fillBox(helper, 1, 6, 1, 9, 6, 9, STONE.defaultBlockState());

        LevelChunk chunk = level.getChunkAt(origin);
        StructureStart start = startWithPieces(List.of(piece(new BoundingBox(
                origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + 8, origin.getY() + 8, origin.getZ() + 8))));

        Optional<BlockPos> first = StructureWaypointScanner.findPlacement(chunk, start, null);
        helper.assertTrue(first.isPresent(),
                "scanner found no placement inside an enclosed room; column from the origin reads "
                        + describeColumn(chunk, origin, 8));
        BlockPos pos = first.get();

        // The contract this test owns: the pick is legal, inside the piece box, and reproducible.
        helper.assertTrue(StructureWaypointScanner.isValidPlacement(chunk, pos),
                "scanner returned an illegal spot: " + pos
                        + " state=" + chunk.getBlockState(pos)
                        + " below=" + chunk.getBlockState(pos.below()));
        helper.assertTrue((pos.getX() >> 4) == chunk.getPos().x && (pos.getZ() >> 4) == chunk.getPos().z,
                "placement escaped the event chunk: " + pos);
        helper.assertTrue(pos.getX() >= origin.getX() && pos.getX() <= origin.getX() + 8
                        && pos.getZ() >= origin.getZ() && pos.getZ() <= origin.getZ() + 8,
                "placement left the piece box: " + pos);
        helper.assertTrue(pos.getY() > origin.getY(),
                "placement should stand on the room floor, got " + pos);

        Optional<BlockPos> second = StructureWaypointScanner.findPlacement(chunk, start, null);
        helper.assertTrue(second.isPresent() && second.get().equals(pos),
                "scanner is not deterministic: " + first + " then " + second);
        helper.succeed();
    }
    /**
     * An open-air site must still receive a waypoint: pass 1 (which wants a roof) fails, pass 2 does not.
     */
    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void scanner_fallsBackToUnroofedPass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));

        // Raised well above the harness barrier cap, so nothing overhead can count as a roof.
        fillBox(helper, 1, 20, 1, 5, 20, 5, STONE.defaultBlockState());

        LevelChunk chunk = level.getChunkAt(origin);
        StructureStart start = startWithPieces(List.of(piece(new BoundingBox(
                origin.getX(), origin.getY() + 19, origin.getZ(),
                origin.getX() + 4, origin.getY() + 23, origin.getZ() + 4))));

        Optional<BlockPos> placement = StructureWaypointScanner.findPlacement(chunk, start, null);
        helper.assertTrue(placement.isPresent(), "scanner found no placement on an open platform");
        BlockPos pos = placement.get();
        helper.assertTrue(chunk.getBlockState(pos.below()).is(STONE), "placement needs the platform as its floor");
        helper.assertTrue(!StructureWaypointScanner.roofed(chunk, pos, StructureWaypointScanner.ROOF_SCAN_DEPTH),
                "an open platform must not be reported as roofed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void scanner_chunkFallbackStaysInChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        LevelChunk chunk = level.getChunkAt(origin);

        // A one-block box hugging the chunk edge next door: it does not intersect this chunk, so L1 has
        // nothing to try and the whole-chunk fallback layer runs instead. (An empty piece list cannot be
        // used here: it makes StructureStart.isValid() false, which exits before the fallback.)
        StructureStart start = startWithPieces(List.of(piece(new BoundingBox(
                origin.getX() - 1, origin.getY(), origin.getZ() - 1,
                origin.getX() - 1, origin.getY() + 4, origin.getZ() - 1))));

        int emptyCount = 0;
        for (int attempt = 0; attempt < 3; attempt++) {
            Optional<BlockPos> placement = StructureWaypointScanner.findPlacement(chunk, start, null);
            if (placement.isEmpty()) {
                // The harness only clears a small area, so some chunks genuinely offer no floor at all.
                emptyCount++;
                continue;
            }
            BlockPos pos = placement.get();
            // The fallback contract: the result must stay inside the event chunk and be a legal spot.
            helper.assertTrue((pos.getX() >> 4) == chunk.getPos().x && (pos.getZ() >> 4) == chunk.getPos().z,
                    "fallback placement escaped the event chunk: " + pos);
            helper.assertTrue(StructureWaypointScanner.isValidPlacement(chunk, pos),
                    "fallback returned an illegal spot: " + pos
                            + " state=" + chunk.getBlockState(pos)
                            + " below=" + chunk.getBlockState(pos.below()));
            helper.assertTrue(chunk.getBlockState(pos.below()).isFaceSturdy(chunk, pos.below(),
                            net.minecraft.core.Direction.UP),
                    "fallback placement is not standing on anything: " + pos);
            helper.assertTrue(pos.getY() > origin.getY(),
                    "fallback should stay near the structure, got " + pos + " for origin " + origin);

            Optional<BlockPos> again = StructureWaypointScanner.findPlacement(chunk, start, null);
            helper.assertTrue(again.isPresent() && again.get().equals(pos),
                    "fallback is not deterministic: " + placement + " then " + again);
            helper.succeed();
            return;
        }
        // If this fires, the harness gave the chunk no usable floor in any attempt, which is a fixture
        // problem rather than a scanner bug; make that explicit instead of a misleading failure.
        helper.fail("fixture produced no usable floor in the event chunk after " + emptyCount + " attempts");
    }
    /** An invalid start must be ignored before any layer runs. */
    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void scanner_invalidStartIsIgnored(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        fillBox(helper, 1, 1, 1, 5, 1, 5, STONE.defaultBlockState());

        LevelChunk chunk = level.getChunkAt(origin);
        StructureStart empty = new StructureStart(null, chunk.getPos(), 0, new PiecesContainer(List.of()));
        helper.assertTrue(!empty.isValid(), "a start with no pieces must report itself invalid");
        helper.assertTrue(StructureWaypointScanner.findPlacement(chunk, empty, null).isEmpty(),
                "an invalid start must produce no placement");
        helper.succeed();
    }

    /**
     * A position holding a block entity must never be chosen, and the check must not touch the block
     * entity at all: the candidate grid is walked using {@code hasBlockEntity()} only, so a chest is
     * rejected without ever constructing its block entity.
     */
    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void scanner_excludesBlockEntities(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));

        // Open room: floor at relative y=1, stone roof at y=6, nothing in between.
        fillBox(helper, 1, 1, 1, 9, 1, 9, STONE.defaultBlockState());
        fillBox(helper, 1, 6, 1, 9, 6, 9, STONE.defaultBlockState());

        // A chest occupies the centre of the room, at the level the scanner would otherwise prefer.
        BlockPos chestPos = helper.absolutePos(new BlockPos(5, 4, 5));
        helper.setBlock(5, 4, 5, Blocks.CHEST);
        helper.assertTrue(chunkHasBlockEntity(level, chestPos),
                "fixture is wrong: no block entity at " + chestPos);

        LevelChunk chunk = level.getChunkAt(origin);
        StructureStart start = startWithPieces(List.of(piece(new BoundingBox(
                origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + 8, origin.getY() + 8, origin.getZ() + 8))));

        Optional<BlockPos> placement = StructureWaypointScanner.findPlacement(chunk, start, null);
        helper.assertTrue(placement.isPresent(), "scanner found no placement in an open room");
        BlockPos pos = placement.get();
        helper.assertTrue(!pos.equals(chestPos), "scanner chose the chest position " + pos);
        helper.assertTrue(!chunk.getBlockState(pos).hasBlockEntity(),
                "chosen position holds a block entity: " + pos + " " + chunk.getBlockState(pos));
        helper.succeed();
    }
    /**
     * Water is a legal target, since the waypoint block supports waterlogging.
     *
     * <p>The floor is placed outside the piece box on purpose. A fluid cannot be confined by a template
     * boundary, so a floor under the piece would be washed away before the scan runs; keeping the floor
     * outside the piece also proves the piece box does not constrain y.
     */
    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void scanner_allowsWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));

        fillBox(helper, 0, 0, 0, 6, 0, 6, STONE.defaultBlockState());
        fillBox(helper, 1, 1, 1, 5, 1, 5, Blocks.WATER.defaultBlockState());

        LevelChunk chunk = level.getChunkAt(origin);
        // The piece box spans y=1..1, exactly the water layer, so every candidate is under water.
        StructureStart start = startWithPieces(List.of(piece(new BoundingBox(
                origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + 4, origin.getY(), origin.getZ() + 4))));

        Optional<BlockPos> placement = StructureWaypointScanner.findPlacement(chunk, start, null);
        helper.assertTrue(placement.isPresent(), "scanner refused to place inside water");
        BlockPos pos = placement.get();
        helper.assertTrue(chunk.getBlockState(pos).getFluidState().is(net.minecraft.world.level.material.Fluids.WATER),
                "expected a water candidate, got " + chunk.getBlockState(pos));
        helper.assertTrue(chunk.getBlockState(pos.below()).is(STONE), "water candidate needs a solid floor");
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------
    // placer
    // ------------------------------------------------------------------------------------------

    /**
     * Exercises the placer on a real chunk. This covers the write path that the freeze bug lived in:
     * the placement must succeed, keep the derived id, set WATERLOGGED from the fluid, and never
     * trigger neighbour updates (which would read blocks in adjacent chunks and can deadlock the main
     * thread -- see the comment on the setBlock call in StructureWaypointPlacer).
     */
    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void placer_writesWaypointWithoutNeighbourUpdates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(3, 4, 3));
        fillBox(helper, 3, 3, 3, 5, 3, 5, STONE.defaultBlockState());

        LevelChunk chunk = level.getChunkAt(origin);
        String id = Naming.deriveId(ResourceLocation.parse("minecraft:end_city"));

        boolean placed = StructureWaypointPlacer.place(level, chunk, origin, id, null);
        helper.assertTrue(placed, "placer refused to write at " + origin
                + " state=" + chunk.getBlockState(origin));
        helper.assertTrue(chunk.getBlockState(origin).is(ModBlocks.WAYPOINT.get()),
                "waypoint block missing after placement, found " + chunk.getBlockState(origin));
        helper.assertTrue(chunk.getBlockEntity(origin) instanceof WaypointBlockEntity,
                "no waypoint block entity after placement");
        helper.assertTrue(id.equals(((WaypointBlockEntity) chunk.getBlockEntity(origin)).getId()),
                "placement lost the derived id: "
                        + ((WaypointBlockEntity) chunk.getBlockEntity(origin)).getId());

        // Not water, so the placer must leave the waterlogged flag alone.
        helper.assertTrue(!chunk.getBlockState(origin).getValue(WaypointBlock.WATERLOGGED),
                "dry placement must not be waterlogged");

        // Outside the event chunk the placer must refuse, so a scanner bug cannot write next door.
        BlockPos outside = origin.offset(16, 0, 0);
        LevelChunk otherChunk = level.getChunkAt(outside);
        helper.assertTrue(!StructureWaypointPlacer.place(level, otherChunk, origin, id, null),
                "placer wrote outside the chunk it claimed to target");
        helper.succeed();
    }

    /**
     * Pins the persistence contract that the injection path relies on.
     *
     * <p>Placement assigns the id through {@code setIdWithoutNeighbourUpdate} and then re-asserts the
     * chunk's unsaved flag, precisely so that no {@code setChanged()} (and therefore no
     * {@code Level#updateNeighbourForOutputSignal}) happens inside {@code ChunkEvent.Load}. This test
     * checks the two halves of that contract: the id really is on the block entity, the chunk really is
     * marked unsaved so it will be written, and the id survives a save/load of the block entity.
     */
    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void placer_idIsSetAndPersisted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(3, 12, 3));
        fillBox(helper, 3, 11, 3, 5, 11, 5, STONE.defaultBlockState());

        LevelChunk chunk = level.getChunkAt(origin);
        String id = Naming.deriveId(ResourceLocation.parse("minecraft:desert_pyramid"));

        helper.assertTrue(StructureWaypointPlacer.place(level, chunk, origin, id, null),
                "placer refused the target, state=" + chunk.getBlockState(origin));
        helper.assertTrue(chunk.getBlockEntity(origin) instanceof WaypointBlockEntity,
                "no block entity after placement");
        WaypointBlockEntity waypoint = (WaypointBlockEntity) chunk.getBlockEntity(origin);

        helper.assertTrue(id.equals(waypoint.getId()),
                "id was not stored, expected " + id + " got " + waypoint.getId());

        // The chunk must be dirty, or the id would never be written to disk.
        helper.assertTrue(chunk.isUnsaved(),
                "chunk was left clean after injection, so the waypoint id would not persist");

        // Round-trip the block entity through NBT the way a chunk save would.
        var registries = level.registryAccess();
        net.minecraft.nbt.CompoundTag tag = waypoint.saveWithoutMetadata(registries);
        helper.assertTrue(id.equals(tag.getString("waypoint_id")),
                "saved NBT does not carry the waypoint id, got '" + tag.getString("waypoint_id") + "'");

        // The escape hatch must still validate: a bogus id must be refused, not stored silently.
        helper.assertTrue(!waypoint.setIdWithoutNeighbourUpdate("NOT A VALID ID"),
                "setIdWithoutNeighbourUpdate accepted an invalid id");
        helper.assertTrue(id.equals(waypoint.getId()), "a rejected id must not overwrite the stored one");
        helper.succeed();
    }

    /**
     * Water must be turned into a waterlogged waypoint rather than left as a cavity.
     *
     * <p>The floor is directly beneath the water on purpose: a water source with air below it simply
     * flows away before the placer runs, which is what made an earlier version of this fixture flaky.
     * A one-deep basin resting on a solid floor is stable.
     */
    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void placer_setsWaterloggedInWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(3, 8, 3));

        // 3x3 stone floor at y+7 with one stable water layer directly on top of it.
        fillBox(helper, 3, 7, 3, 5, 7, 5, STONE.defaultBlockState());
        fillBox(helper, 3, 8, 3, 5, 8, 5, Blocks.WATER.defaultBlockState());

        LevelChunk chunk = level.getChunkAt(origin);
        BlockPos target = helper.absolutePos(new BlockPos(4, 8, 4));
        String id = Naming.deriveId(ResourceLocation.parse("minecraft:monument"));

        helper.assertTrue(chunk.getBlockState(target).getFluidState().is(net.minecraft.world.level.material.Fluids.WATER),
                "fixture is wrong: the water target drained away, found " + chunk.getBlockState(target));

        helper.assertTrue(StructureWaypointPlacer.place(level, chunk, target, id, null),
                "placer refused a water target, state=" + chunk.getBlockState(target));
        helper.assertTrue(chunk.getBlockState(target).is(ModBlocks.WAYPOINT.get()),
                "waypoint block missing, found " + chunk.getBlockState(target));
        helper.assertTrue(chunk.getBlockState(target).getValue(WaypointBlock.WATERLOGGED),
                "a water target must come out waterlogged, got " + chunk.getBlockState(target));
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------
    // tags
    // ------------------------------------------------------------------------------------------

    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void tags_whitelistIsLoadedAndComplete(GameTestHelper helper) {
        Registry<Structure> structures = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        TagKey<Structure> key = TagKey.create(Registries.STRUCTURE,
                ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "waypoint_whitelist"));
        Optional<HolderSet.Named<Structure>> tag = structures.getTag(key);
        helper.assertTrue(tag.isPresent(), "the built-in whitelist tag failed to load");

        for (String raw : new String[] {
            "minecraft:ancient_city", "minecraft:bastion_remnant", "minecraft:desert_pyramid",
            "minecraft:end_city", "minecraft:fortress", "minecraft:igloo", "minecraft:jungle_pyramid",
            "minecraft:mansion", "minecraft:monument", "minecraft:stronghold", "minecraft:swamp_hut",
            "minecraft:trial_chambers"
        }) {
            Structure structure = structures.get(ResourceLocation.parse(raw));
            helper.assertTrue(structure != null, "vanilla structure missing from the registry: " + raw);
            helper.assertTrue(structures.wrapAsHolder(structure).is(key), raw + " is missing from the whitelist tag");
        }

        Structure village = structures.get(ResourceLocation.parse("minecraft:village_plains"));
        helper.assertTrue(village != null, "village_plains missing from the registry");
        helper.assertTrue(!structures.wrapAsHolder(village).is(key), "villages must not be whitelisted");
        helper.succeed();
    }

    /**
     * The blacklist stores {@code #minecraft:mineshaft} as a tag reference. Written as a bare id it would
     * only cover {@code mineshaft} itself and let {@code mineshaft_mesa} through, which is exactly the bug
     * this test pins down.
     */
    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void tags_blacklistCoversMineshaftMesa(GameTestHelper helper) {
        Registry<Structure> structures = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        TagKey<Structure> key = TagKey.create(Registries.STRUCTURE,
                ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "waypoint_blacklist"));
        helper.assertTrue(structures.getTag(key).isPresent(), "the built-in blacklist tag failed to load");

        for (String raw : new String[] {
            "minecraft:buried_treasure", "minecraft:mineshaft", "minecraft:mineshaft_mesa",
            "minecraft:nether_fossil", "minecraft:pillager_outpost", "minecraft:trail_ruins",
            "minecraft:village_plains", "minecraft:village_taiga", "minecraft:ruined_portal",
            "minecraft:ruined_portal_nether", "minecraft:shipwreck", "minecraft:shipwreck_beached",
            "minecraft:ocean_ruin_cold", "minecraft:ocean_ruin_warm"
        }) {
            Structure structure = structures.get(ResourceLocation.parse(raw));
            helper.assertTrue(structure != null, "vanilla structure missing from the registry: " + raw);
            helper.assertTrue(structures.wrapAsHolder(structure).is(key), raw + " is missing from the blacklist tag");
        }

        Structure endCity = structures.get(ResourceLocation.parse("minecraft:end_city"));
        helper.assertTrue(endCity != null, "end_city missing from the registry");
        helper.assertTrue(!structures.wrapAsHolder(endCity).is(key), "end_city must not be blacklisted");
        helper.succeed();
    }

    /**
     * Every vanilla structure must be covered by exactly one of the two lists, so that switching modes
     * cannot leave a structure in a gap. The lists partition the 34 vanilla structures 12 + 22.
     */
    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void tags_partitionEveryVanillaStructure(GameTestHelper helper) {
        Registry<Structure> structures = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        TagKey<Structure> whitelist = TagKey.create(Registries.STRUCTURE,
                ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "waypoint_whitelist"));
        TagKey<Structure> blacklist = TagKey.create(Registries.STRUCTURE,
                ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "waypoint_blacklist"));

        String[] vanilla = {
            "ancient_city", "bastion_remnant", "buried_treasure", "desert_pyramid", "end_city", "fortress",
            "igloo", "jungle_pyramid", "mansion", "mineshaft", "mineshaft_mesa", "monument", "nether_fossil",
            "ocean_ruin_cold", "ocean_ruin_warm", "pillager_outpost", "ruined_portal", "ruined_portal_desert",
            "ruined_portal_jungle", "ruined_portal_mountain", "ruined_portal_nether", "ruined_portal_ocean",
            "ruined_portal_swamp", "shipwreck", "shipwreck_beached", "stronghold", "swamp_hut", "trail_ruins",
            "trial_chambers", "village_desert", "village_plains", "village_savanna", "village_snowy", "village_taiga"
        };

        int white = 0;
        int black = 0;
        List<String> uncovered = new ArrayList<>();
        for (String path : vanilla) {
            Structure structure = structures.get(ResourceLocation.fromNamespaceAndPath("minecraft", path));
            helper.assertTrue(structure != null, "vanilla structure missing from the registry: " + path);
            boolean inWhite = structures.wrapAsHolder(structure).is(whitelist);
            boolean inBlack = structures.wrapAsHolder(structure).is(blacklist);
            helper.assertTrue(!(inWhite && inBlack), path + " is in both lists");
            if (inWhite) {
                white++;
            } else if (inBlack) {
                black++;
            } else {
                uncovered.add(path);
            }
        }

        helper.assertTrue(uncovered.isEmpty(), "structures in neither list: " + uncovered);
        helper.assertTrue(white == 12, "expected 12 whitelisted structures, found " + white);
        helper.assertTrue(black == 22, "expected 22 blacklisted structures, found " + black);
        helper.succeed();
    }

    /** The filter must run without throwing for a listed structure and for an unlisted one. */
    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void tagFilter_doesNotThrowInEitherMode(GameTestHelper helper) {
        Registry<Structure> structures = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        Structure endCity = structures.get(ResourceLocation.parse("minecraft:end_city"));
        Structure village = structures.get(ResourceLocation.parse("minecraft:village_plains"));
        helper.assertTrue(endCity != null && village != null, "test structures missing from the registry");

        // The configured mode decides the answer; call it both ways only to prove neither path throws.
        StructureTagFilter.allows(structures, endCity);
        StructureTagFilter.allows(structures, village);
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------
    // end-to-end: a placed waypoint keeps its derived id and registers itself
    // ------------------------------------------------------------------------------------------

    /**
     * Pins the whole point of relaxing the id pattern: a placement whose id is {@code minecraft.end_city}
     * must survive {@code setId} instead of being silently dropped to {@code empty}.
     */
    @GameTest(template = TEMPLATE, templateNamespace = TeleportWaypoint.MODID)
    public void placedWaypointKeepsStructuredId(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 4, 2));
        helper.setBlock(2, 3, 2, STONE);
        helper.setBlock(2, 4, 2, ModBlocks.WAYPOINT.get());

        LevelChunk chunk = helper.getLevel().getChunkAt(pos);
        helper.assertTrue(chunk.getBlockState(pos).is(ModBlocks.WAYPOINT.get()),
                "the waypoint block was not placed, found " + chunk.getBlockState(pos) + " at " + pos);
        helper.assertTrue(chunk.getBlockEntity(pos) instanceof WaypointBlockEntity,
                "the waypoint block must create its block entity, got " + chunk.getBlockEntity(pos));

        WaypointBlockEntity waypoint = (WaypointBlockEntity) chunk.getBlockEntity(pos);
        String derived = Naming.deriveId(ResourceLocation.parse("minecraft:end_city"));
        waypoint.setId(derived);
        helper.assertTrue(derived.equals(waypoint.getId()),
                "structured id was rejected by setId, stored: " + waypoint.getId());
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------------------------------

    /** Returns whether the chunk owning {@code pos} holds a block entity there. */
    private static boolean chunkHasBlockEntity(ServerLevel level, BlockPos pos) {
        return level.getChunkAt(pos).getBlockEntity(pos) != null;
    }
    /** Builds a readable listing of one column, so a failing geometry assertion says what it saw. */
    private static String describeColumn(LevelChunk chunk, BlockPos base, int upTo) {
        StringBuilder sb = new StringBuilder("base y=" + base.getY());
        for (int k = 0; k <= upTo; k++) {
            sb.append(" y+").append(k).append('=')
              .append(chunk.getBlockState(base.above(k)).getBlock().getName().getString());
        }
        return sb.toString();
    }
    private static void expectId(GameTestHelper helper, String raw, String expected) {
        String derived = Naming.deriveId(ResourceLocation.parse(raw));
        helper.assertTrue(expected.equals(derived), "expected " + expected + " for " + raw + ", got " + derived);
    }

    private static StructureStart startWithPieces(List<StructurePiece> pieces) {
        return new StructureStart(null, new ChunkPos(0, 0), 1, new PiecesContainer(List.copyOf(pieces)));
    }

    /** An anonymous piece is enough: the scanner only ever reads its bounding box. */
    private static StructurePiece piece(BoundingBox box) {
        return new StructurePiece(StructurePieceType.SWAMPLAND_HUT, 0, box) {
            @Override
            protected void addAdditionalSaveData(
                    net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext context,
                    net.minecraft.nbt.CompoundTag tag) {
                // The scanner reads geometry only; this piece is never serialized.
            }

            @Override
            public void postProcess(
                    net.minecraft.world.level.WorldGenLevel level,
                    net.minecraft.world.level.StructureManager structureManager,
                    net.minecraft.world.level.chunk.ChunkGenerator generator,
                    net.minecraft.util.RandomSource random,
                    BoundingBox chunkBox,
                    ChunkPos chunkPos,
                    BlockPos pivot) {
                // The scanner only reads geometry, so there is nothing to place.
            }
        };
    }

    private static void fillBox(
            GameTestHelper helper, int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    helper.setBlock(x, y, z, state);
                }
            }
        }
    }

    /** Builds a hollow rectangular ring of walls at a fixed y, leaving the interior open. */
    private static void fillHollowWalls(
            GameTestHelper helper, int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                helper.setBlock(x, y, z1, state);
                helper.setBlock(x, y, z2, state);
            }
            for (int z = z1 + 1; z < z2; z++) {
                helper.setBlock(x1, y, z, state);
                helper.setBlock(x2, y, z, state);
            }
        }
    }

}
