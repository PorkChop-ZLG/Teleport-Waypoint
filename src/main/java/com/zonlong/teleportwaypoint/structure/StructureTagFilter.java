package com.zonlong.teleportwaypoint.structure;

import com.zonlong.teleportwaypoint.TeleportWaypoint;
import com.zonlong.teleportwaypoint.config.CommonConfig;
import com.zonlong.teleportwaypoint.config.StructureWaypointMode;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Decides whether a structure is allowed to receive an injected waypoint.
 *
 * <p>The two lists are data pack tags, not configuration, and their names are hard coded so there is
 * one less thing to get wrong:
 * <ul>
 *   <li>{@code teleportwaypoint:waypoint_whitelist} — used in WHITELIST mode</li>
 *   <li>{@code teleportwaypoint:waypoint_blacklist} — used in BLACKLIST mode</li>
 * </ul>
 * Both ship with {@code "replace": false} so other data packs can add entries without overwriting ours.
 *
 * <p>An <em>absent</em> tag and an <em>empty</em> tag mean the same thing for filtering, but they are
 * very different operationally, so they are reported differently: absent warns once (the data pack was
 * probably disabled), empty logs nothing at all because it is a deliberate, legal configuration.
 */
public final class StructureTagFilter {

    private static final TagKey<Structure> WHITELIST = TagKey.create(
            Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "waypoint_whitelist"));

    private static final TagKey<Structure> BLACKLIST = TagKey.create(
            Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "waypoint_blacklist"));

    // Log de-duplication only. These may only ever go false -> true and never take part in a decision.
    private static volatile boolean whitelistMissingWarned = false;
    private static volatile boolean blacklistMissingWarned = false;

    private StructureTagFilter() {
    }

    /**
     * Returns whether the configured list allows a waypoint in {@code structure}.
     *
     * <p>In WHITELIST mode the structure must be in the whitelist; in BLACKLIST mode it must not be in
     * the blacklist. Either way an absent or empty list yields "nobody" for the whitelist and
     * "everybody" for the blacklist.
     */
    public static boolean allows(Registry<Structure> structures, Structure structure) {
        StructureWaypointMode mode = CommonConfig.STRUCTURE_WAYPOINT_MODE.get();
        TagKey<Structure> target = mode == StructureWaypointMode.WHITELIST ? WHITELIST : BLACKLIST;

        warnIfMissing(structures, mode, target);

        Holder<Structure> holder = structures.wrapAsHolder(structure);
        boolean listed = holder.is(target);
        return mode == StructureWaypointMode.WHITELIST ? listed : !listed;
    }

    /**
     * Warns once when the active tag is not loaded at all. Deliberately not using
     * {@code getOrCreateTag}: that would invent an empty tag for a missing one and mask the warning
     * forever.
     */
    private static void warnIfMissing(Registry<Structure> structures, StructureWaypointMode mode, TagKey<Structure> target) {
        if (structures.getTag(target).isPresent()) {
            return;
        }
        if (mode == StructureWaypointMode.WHITELIST) {
            if (!whitelistMissingWarned) {
                whitelistMissingWarned = true;
                TeleportWaypoint.LOGGER.warn(
                        "structure waypoint: tag {} is missing, so no structure will receive a waypoint. "
                                + "Check that this mod's data pack is enabled.",
                        target.location());
            }
        } else {
            if (!blacklistMissingWarned) {
                blacklistMissingWarned = true;
                TeleportWaypoint.LOGGER.warn(
                        "structure waypoint: tag {} is missing, so every structure will receive a waypoint.",
                        target.location());
            }
        }
    }
}
