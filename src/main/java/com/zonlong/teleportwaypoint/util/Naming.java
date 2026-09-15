package com.zonlong.teleportwaypoint.util;

import com.zonlong.teleportwaypoint.block.entity.WaypointBlockEntity;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Single source of truth for how a waypoint id turns into a display name.
 *
 * <p>Display names resolve through a three-level fallback so that both new and pre-0.4.0 saves name
 * correctly:
 * <ol>
 *   <li>the structured key, for example {@code tpwp.minecraft.end_city};</li>
 *   <li>otherwise the legacy key, for example {@code teleportwaypoint.waypoint.end_city}, which is what
 *       a bare id stored by an older version resolves against;</li>
 *   <li>otherwise a humanized name, for example {@code End City}.</li>
 * </ol>
 * The first two levels are keys, so the client localizes them; the third is plain text used only when
 * nothing in the language files matches. Note that the choice between levels 1 and 2 is made from the
 * <em>client</em> language table when a name is rendered, while anything built on the server simply
 * carries the key (and the humanized fallback text) across the wire for the client to resolve.
 *
 * <p>Every other translation key in this mod keeps its {@code teleportwaypoint.*} form. The
 * {@code waypoint_id} itself never contains a prefix, so the network wire format is unchanged.
 *
 * <p>Note on key length: shortening the prefix does <em>not</em> protect against truncation. The only
 * hard length limit in this mod is {@link #MAX_ID_LENGTH} on the {@code waypoint_id}; translation keys
 * have no explicit limit. Truncation is therefore handled by {@link #deriveId(ResourceLocation)}, not by
 * the key prefix.
 */
public final class Naming {

    /** Translation key prefix for structured waypoint display names, for example {@code tpwp.minecraft.end_city}. */
    public static final String KEY_PREFIX = "tpwp.";

    /** Translation key prefix used before 0.4.0. Kept as an alias so existing saves keep their names. */
    public static final String LEGACY_KEY_PREFIX = "teleportwaypoint.waypoint.";

    /** Id used when a waypoint has no usable id. Keeps the legacy bare value so old saves still resolve. */
    public static final String EMPTY_ID = "empty";

    /** Must stay equal to {@link WaypointBlockEntity#MAX_TEXT_LENGTH}. */
    public static final int MAX_ID_LENGTH = WaypointBlockEntity.MAX_TEXT_LENGTH;

    /** Human readable fallback shown when a waypoint has no usable name. */
    public static final String EMPTY_FALLBACK_NAME = "Unnamed Waypoint";

    private Naming() {
    }

    /** Returns the structured translation key for an id, without consulting the language files. */
    public static String modernKey(String id) {
        return KEY_PREFIX + (WaypointBlockEntity.isValidId(id) ? id : EMPTY_ID);
    }

    /** Returns the pre-0.4.0 translation key for an id, without consulting the language files. */
    public static String legacyKey(String id) {
        return LEGACY_KEY_PREFIX + (WaypointBlockEntity.isValidId(id) ? id : EMPTY_ID);
    }

    /**
     * Returns the translation key for a waypoint id, preferring the structured key and falling back to
     * the legacy key when the current language has no entry for it.
     */
    public static String key(String id) {
        String modern = modernKey(id);
        return hasTranslation(modern) ? modern : legacyKey(id);
    }

    /** Returns whether the current language table has an entry for {@code key}. */
    public static boolean hasTranslation(String key) {
        return key != null && Language.getInstance().has(key);
    }

    /** Returns the translation key used when a waypoint has no usable name. */
    public static String emptyKey() {
        return KEY_PREFIX + EMPTY_ID;
    }

    /**
     * Returns the display name for a waypoint id, carrying a humanized name as the fallback so a
     * missing translation shows something readable instead of a raw key.
     */
    public static Component displayName(String id) {
        return translatableWithHumanizedFallback(key(id), id);
    }

    /**
     * Builds a translatable component whose fallback is the humanized form of {@code id}. Prefer this
     * over {@link Component#translatable(String)} wherever the id is known: it degrades to
     * {@code End City} rather than to the literal key {@code tpwp.minecraft.end_city}.
     */
    private static Component translatableWithHumanizedFallback(String key, String id) {
        return Component.translatableWithFallback(key, humanize(id));
    }

    /**
     * Turns a translation key back into a readable fallback name, treating both {@code .} and
     * {@code _} as word separators: {@code minecraft.end_city} becomes {@code Minecraft End City}.
     */
    public static String humanize(String id) {
        if (id == null || id.isEmpty()) {
            return EMPTY_FALLBACK_NAME;
        }
        StringBuilder out = new StringBuilder(id.length());
        boolean startOfWord = true;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '.' || c == '_') {
                if (out.length() > 0 && out.charAt(out.length() - 1) != ' ') {
                    out.append(' ');
                }
                startOfWord = true;
                continue;
            }
            out.append(startOfWord ? Character.toUpperCase(c) : Character.toLowerCase(c));
            startOfWord = false;
        }
        String result = out.toString().trim();
        return result.isEmpty() ? EMPTY_FALLBACK_NAME : result;
    }

    /**
     * Derives the stored {@code waypoint_id} from a structure registry id: {@code namespace.path}.
     *
     * <p>This is where truncation is handled. A derived id longer than {@link #MAX_ID_LENGTH} is
     * cut back to the limit and then trimmed of any dangling separator so the result still passes
     * {@link WaypointBlockEntity#isValidId(String)}.
     */
    public static String deriveId(ResourceLocation structureId) {
        if (structureId == null) {
            return EMPTY_ID;
        }
        String candidate = structureId.getNamespace() + "." + structureId.getPath();
        if (candidate.length() <= MAX_ID_LENGTH && WaypointBlockEntity.isValidId(candidate)) {
            return candidate;
        }

        String truncated = candidate.substring(0, Math.min(candidate.length(), MAX_ID_LENGTH));
        int end = truncated.length();
        while (end > 0) {
            char c = truncated.charAt(end - 1);
            if (c != '.' && c != '_') {
                break;
            }
            end--;
        }
        truncated = truncated.substring(0, end);
        return WaypointBlockEntity.isValidId(truncated) ? truncated : EMPTY_ID;
    }
}
