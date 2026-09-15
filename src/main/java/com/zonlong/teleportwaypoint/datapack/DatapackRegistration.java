package com.zonlong.teleportwaypoint.datapack;

import com.zonlong.teleportwaypoint.TeleportWaypoint;
import com.zonlong.teleportwaypoint.config.CommonConfig;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Registers optional structure-compatibility datapacks.
 *
 * <p>Two datapacks are provided:
 * <ul>
 *   <li>"vanilla structure waypoints" overrides selected vanilla structure NBT files;</li>
 *   <li>"YUNG structure waypoints" overrides entry NBT files from YUNG's structure enhancement mods.</li>
 * </ul>
 * Both are enabled by default in new worlds according to their Common config options,
 * but remain optional so players can disable them from the world datapack screen.
 */
public final class DatapackRegistration {
    private static final String VANILLA_DATAPACK_PATH =
            "data/teleportwaypoint/datapacks/vanilla_structure_waypoints";
    private static final String YUNG_DATAPACK_PATH =
            "data/teleportwaypoint/datapacks/yung_structure_waypoints";

    private DatapackRegistration() {
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }

        registerPack(
                event,
                VANILLA_DATAPACK_PATH,
                "datapack.teleportwaypoint.vanilla_structure_waypoints.name",
                CommonConfig.DEFAULT_ENABLE_STRUCTURE_WAYPOINTS.get()
        );
        registerPack(
                event,
                YUNG_DATAPACK_PATH,
                "datapack.teleportwaypoint.yung_structure_waypoints.name",
                CommonConfig.DEFAULT_ENABLE_YUNG_STRUCTURE_WAYPOINTS.get()
        );
    }

    private static void registerPack(AddPackFindersEvent event, String path, String nameKey, boolean defaultEnable) {
        PackSource packSource = defaultEnable ? PackSource.BUILT_IN : PackSource.FEATURE;
        event.addPackFinders(
                ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, path),
                PackType.SERVER_DATA,
                Component.translatable(nameKey),
                packSource,
                false,
                Pack.Position.TOP
        );
    }
}
