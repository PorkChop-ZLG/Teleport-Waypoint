package com.zonlong.teleportwaypoint.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.zonlong.teleportwaypoint.TeleportWaypoint;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-player activation state: player UUID -> set of activated waypoint uids.
 * Server-authoritative, persisted as a dedicated world SavedData file.
 */
public class PlayerWaypointData extends SavedData {
    private static final String DATA_NAME = "teleportwaypoint_players";
    private static final String TAG_PLAYERS = "players";

    private final Map<UUID, Set<UUID>> activated = new HashMap<>();

    public static PlayerWaypointData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PlayerWaypointData::new, PlayerWaypointData::read, null),
                DATA_NAME);
    }

    public boolean isActivated(UUID player, UUID waypoint) {
        Set<UUID> set = activated.get(player);
        return set != null && set.contains(waypoint);
    }

    public void activate(UUID player, UUID waypoint) {
        activated.computeIfAbsent(player, k -> new HashSet<>()).add(waypoint);
        setDirty();
    }

    public void deactivate(UUID player, UUID waypoint) {
        Set<UUID> set = activated.get(player);
        if (set != null && set.remove(waypoint)) {
            if (set.isEmpty()) {
                activated.remove(player);
            }
            setDirty();
        }
    }

    public Set<UUID> deactivateAll(UUID waypoint) {
        Set<UUID> affectedPlayers = new HashSet<>();
        for (Map.Entry<UUID, Set<UUID>> entry : activated.entrySet()) {
            if (entry.getValue().remove(waypoint)) {
                affectedPlayers.add(entry.getKey());
            }
        }
        activated.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        if (!affectedPlayers.isEmpty()) {
            setDirty();
        }
        return affectedPlayers;
    }

    public Set<UUID> getActivated(UUID player) {
        return activated.getOrDefault(player, Set.of());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, Set<UUID>> entry : activated.entrySet()) {
            ListTag list = new ListTag();
            for (UUID waypoint : entry.getValue()) {
                list.add(NbtUtils.createUUID(waypoint));
            }
            players.put(entry.getKey().toString(), list);
        }
        tag.put(TAG_PLAYERS, players);
        return tag;
    }

    public static PlayerWaypointData read(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerWaypointData data = new PlayerWaypointData();
        CompoundTag players = tag.getCompound(TAG_PLAYERS);
        boolean dirty = false;
        for (String key : players.getAllKeys()) {
            UUID player;
            try {
                player = UUID.fromString(key);
            } catch (Exception e) {
                TeleportWaypoint.LOGGER.warn("[TeleportWaypoint] Skipping invalid player waypoint data for key {}", key, e);
                dirty = true;
                continue;
            }

            ListTag list = players.getList(key, Tag.TAG_INT_ARRAY);
            Set<UUID> set = new HashSet<>();
            for (Tag entry : list) {
                try {
                    set.add(NbtUtils.loadUUID(entry));
                } catch (Exception e) {
                    TeleportWaypoint.LOGGER.warn("[TeleportWaypoint] Skipping invalid waypoint UUID for player {}", key, e);
                    dirty = true;
                }
            }

            if (!set.isEmpty()) {
                data.activated.put(player, set);
            } else {
                dirty = true;
            }
        }
        if (dirty) {
            data.setDirty();
        }
        return data;
    }
}
