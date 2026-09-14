package com.zonlong.teleportwaypoint.structure;

import java.util.Optional;

import com.zonlong.teleportwaypoint.util.Naming;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Maps a structure to the {@code waypoint_id} stored on its injected waypoint block.
 *
 * <p>The id is derived straight from the structure registry id as {@code <namespace>.<path>} (for
 * example {@code minecraft.end_city}), so no mapping table is needed. Existing saves keep their own
 * bare ids and stay valid because the old translation keys are retained as aliases.
 */
public final class StructureWaypointNaming {

    private StructureWaypointNaming() {
    }

    /**
     * Returns the derived waypoint id, or empty when the structure is not present in the registry.
     */
    public static Optional<String> derive(Registry<Structure> structures, StructureStart start) {
        ResourceLocation id = structures.getKey(start.getStructure());
        if (id == null) {
            return Optional.empty();
        }
        return Optional.of(Naming.deriveId(id));
    }

    /**
     * Returns the structure registry id, or empty when the structure is not present in the registry.
     */
    public static Optional<ResourceLocation> structureId(Registry<Structure> structures, Structure structure) {
        return Optional.ofNullable(structures.getKey(structure));
    }
}
