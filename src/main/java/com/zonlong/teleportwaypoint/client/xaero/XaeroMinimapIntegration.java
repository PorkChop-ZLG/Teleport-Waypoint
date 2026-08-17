package com.zonlong.teleportwaypoint.client.xaero;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.zonlong.teleportwaypoint.Config;
import com.zonlong.teleportwaypoint.TeleportWaypoint;
import com.zonlong.teleportwaypoint.client.ClientWaypointInfo;
import com.zonlong.teleportwaypoint.client.ClientWaypointState;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.world.MinimapWorldManager;

/**
 * Injects teleport waypoints into Xaero's minimap custom waypoint store. The
 * minimap then renders them with names, colors and symbols using Xaero's own
 * waypoint rendering.
 */
public final class XaeroMinimapIntegration {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final Map<UUID, Integer> UID_TO_ID = new HashMap<>();
    private static final Map<Integer, UUID> ID_TO_UID = new HashMap<>();
    private static final Map<ResourceLocation, Set<Integer>> OWNED = new HashMap<>();
    private static int lastRevision = -1;
    private static boolean lastShowWaypoints = true;
    private static boolean lastShowWaypointNames = true;
    private static boolean lastShowInactiveWaypoints = true;
    private static boolean lastShowActiveWaypoints = true;
    private static int lastWaypointRange = 128;
    private static boolean lastShowInactivePocketWaypoints = true;
    private static boolean lastShowActivePocketWaypoints = true;
    private static int lastPocketWaypointRange = 128;
    private static ResourceKey<Level> lastPlayerDimension;
    private static BlockPos lastPlayerPos;
    private static boolean initialized;
    private static boolean minimapRegistered;

    private XaeroMinimapIntegration() {
    }

    public static void tick() {
        if (minimapRegistered) {
            return;
        }
        if (!ModList.get().isLoaded("xaerominimap")) {
            return;
        }
        if (xaero.common.HudMod.INSTANCE == null
                || xaero.common.HudMod.INSTANCE.getMinimap() == null) {
            return;
        }
        init();
        minimapRegistered = true;
        TeleportWaypoint.LOGGER.info("[TeleportWaypoint] Xaero Minimap integration registered");
    }

    public static void init() {
        initialized = true;
        sync();
    }

    public static void sync() {
        if (!initialized) {
            return;
        }
        int revision = ClientWaypointState.getRevision();
        boolean showWaypoints = XaeroIntegration.showWaypoints();
        boolean showWaypointNames = XaeroIntegration.showWaypointNames();
        boolean showInactiveWaypoints = Config.SHOW_INACTIVE_WAYPOINTS.get();
        boolean showActiveWaypoints = Config.SHOW_ACTIVE_WAYPOINTS.get();
        int waypointRange = Config.WAYPOINT_RANGE.get();
        boolean showInactivePocketWaypoints = Config.SHOW_INACTIVE_POCKET_WAYPOINTS.get();
        boolean showActivePocketWaypoints = Config.SHOW_ACTIVE_POCKET_WAYPOINTS.get();
        int pocketWaypointRange = Config.POCKET_WAYPOINT_RANGE.get();
        boolean configChanged = showWaypoints != lastShowWaypoints
                || showWaypointNames != lastShowWaypointNames
                || showInactiveWaypoints != lastShowInactiveWaypoints
                || showActiveWaypoints != lastShowActiveWaypoints
                || waypointRange != lastWaypointRange
                || showInactivePocketWaypoints != lastShowInactivePocketWaypoints
                || showActivePocketWaypoints != lastShowActivePocketWaypoints
                || pocketWaypointRange != lastPocketWaypointRange;
        boolean rangeRefresh = shouldRefreshForRange();
        if (revision == lastRevision && !configChanged && !rangeRefresh) {
            return;
        }

        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) {
            // Session not ready yet: do not consume the revision/config state.
            return;
        }
        MinimapWorldManager manager = session.getWorldManager();

        lastRevision = revision;
        lastShowWaypoints = showWaypoints;
        lastShowWaypointNames = showWaypointNames;
        lastShowInactiveWaypoints = showInactiveWaypoints;
        lastShowActiveWaypoints = showActiveWaypoints;
        lastWaypointRange = waypointRange;
        lastShowInactivePocketWaypoints = showInactivePocketWaypoints;
        lastShowActivePocketWaypoints = showActivePocketWaypoints;
        lastPocketWaypointRange = pocketWaypointRange;
        updateLastPlayerState();

        Map<ResourceLocation, List<ClientWaypointInfo>> desired = new HashMap<>();
        if (showWaypoints) {
            Player player = Minecraft.getInstance().player;
            for (ClientWaypointInfo info : ClientWaypointState.getWaypoints()) {
                if (!XaeroIntegration.shouldShow(info.pocket(), ClientWaypointState.isActivated(info.uid()))) {
                    continue;
                }
                if (player != null && info.dimension().equals(player.level().dimension())) {
                    int range = XaeroIntegration.getDisplayRange(info.pocket());
                    if (range > 0 && distanceSq(player.blockPosition(), info.pos()) > (long) range * range) {
                        continue;
                    }
                }
                desired.computeIfAbsent(info.dimension().location(), k -> new ArrayList<>()).add(info);
            }
        }

        removeStale(manager, desired);
        addOrUpdate(manager, desired);
    }

    private static boolean shouldRefreshForRange() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        ResourceKey<Level> dimension = player.level().dimension();
        if (!dimension.equals(lastPlayerDimension)) {
            return true;
        }
        if (lastPlayerPos == null) {
            return true;
        }
        boolean anyRangeEnabled = XaeroIntegration.getDisplayRange(false) > 0
                || XaeroIntegration.getDisplayRange(true) > 0;
        if (!anyRangeEnabled) {
            return false;
        }
        return distanceSq(lastPlayerPos, player.blockPosition()) > 16L * 16L;
    }

    private static void updateLastPlayerState() {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            lastPlayerDimension = player.level().dimension();
            lastPlayerPos = player.blockPosition();
        } else {
            lastPlayerDimension = null;
            lastPlayerPos = null;
        }
    }

    private static long distanceSq(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX();
        long dy = a.getY() - b.getY();
        long dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static void removeStale(MinimapWorldManager manager,
                                    Map<ResourceLocation, List<ClientWaypointInfo>> desired) {
        Iterator<Map.Entry<ResourceLocation, Set<Integer>>> it = OWNED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ResourceLocation, Set<Integer>> entry = it.next();
            ResourceLocation dimension = entry.getKey();
            Int2ObjectMap<Waypoint> map = manager.getCustomWaypoints(dimension);
            Set<Integer> owned = entry.getValue();

            if (!desired.containsKey(dimension)) {
                for (int id : owned) {
                    map.remove(id);
                    UUID uid = ID_TO_UID.remove(id);
                    if (uid != null) {
                        UID_TO_ID.remove(uid);
                    }
                }
                it.remove();
                continue;
            }

            Set<UUID> desiredUids = new HashSet<>();
            for (ClientWaypointInfo info : desired.get(dimension)) {
                desiredUids.add(info.uid());
            }

            owned.removeIf(id -> {
                UUID uid = findUid(id);
                if (uid == null || !desiredUids.contains(uid)) {
                    map.remove(id);
                    ID_TO_UID.remove(id);
                    if (uid != null) {
                        UID_TO_ID.remove(uid);
                    }
                    return true;
                }
                return false;
            });
        }
    }

    private static void addOrUpdate(MinimapWorldManager manager,
                                    Map<ResourceLocation, List<ClientWaypointInfo>> desired) {
        for (Map.Entry<ResourceLocation, List<ClientWaypointInfo>> entry : desired.entrySet()) {
            ResourceLocation dimension = entry.getKey();
            Int2ObjectMap<Waypoint> map = manager.getCustomWaypoints(dimension);
            Set<Integer> owned = OWNED.computeIfAbsent(dimension, k -> new HashSet<>());

            for (ClientWaypointInfo info : entry.getValue()) {
                int id = UID_TO_ID.computeIfAbsent(info.uid(), k -> NEXT_ID.getAndIncrement());
                ID_TO_UID.put(id, info.uid());
                owned.add(id);

                boolean activated = ClientWaypointState.isActivated(info.uid());
                WaypointColor color;
                if (activated) {
                    color = info.pocket() ? WaypointColor.GREEN : WaypointColor.AQUA;
                } else {
                    color = info.pocket() ? WaypointColor.YELLOW : WaypointColor.RED;
                }
                String symbol = info.pocket() ? "P" : "W";
                String displayName = XaeroIntegration.showWaypointNames()
                        ? info.displayName().getString()
                        : "";

                Waypoint existing = map.get(id);
                if (existing != null
                        && existing.getX() == info.pos().getX()
                        && existing.getY() == info.pos().getY()
                        && existing.getZ() == info.pos().getZ()
                        && Objects.equals(existing.getSymbol(), symbol)
                        && Objects.equals(existing.getName(), displayName)
                        && existing.getWaypointColor() == color) {
                    continue;
                }

                Waypoint waypoint = new Waypoint(
                        info.pos().getX(),
                        info.pos().getY(),
                        info.pos().getZ(),
                        displayName,
                        symbol,
                        color);
                waypoint.setTemporary(true);
                map.put(id, waypoint);
            }
        }
    }

    private static UUID findUid(int id) {
        return ID_TO_UID.get(id);
    }
}
