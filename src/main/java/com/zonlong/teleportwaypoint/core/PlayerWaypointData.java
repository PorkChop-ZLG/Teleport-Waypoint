package com.zonlong.teleportwaypoint.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
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
                new SavedData.Factory<>(PlayerWaypointData::new, PlayerWaypointData::read, DataFixTypes.SAVED_DATA_MAP_DATA),
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
            setDirty();
        }
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
        for (String key : players.getAllKeys()) {
            UUID player = UUID.fromString(key);
            ListTag list = players.getList(key, Tag.TAG_INT_ARRAY);
            Set<UUID> set = new HashSet<>();
            for (Tag entry : list) {
                set.add(NbtUtils.loadUUID(entry));
            }
            data.activated.put(player, set);
        }
        return data;
    }
}
