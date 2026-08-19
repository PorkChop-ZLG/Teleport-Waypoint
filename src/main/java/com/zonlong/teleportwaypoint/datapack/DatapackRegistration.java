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
 * Registers the optional "vanilla structure waypoints" datapack.
 *
 * <p>The datapack overrides selected vanilla structure NBT files to add teleport
 * waypoints. It is enabled by default in new worlds when
 * {@link CommonConfig#DEFAULT_ENABLE_STRUCTURE_WAYPOINTS} is true, but remains
 * optional so players can disable it from the world datapack screen.
 */
public final class DatapackRegistration {
    private static final String DATAPACK_PATH =
            "data/teleportwaypoint/datapacks/vanilla_structure_waypoints";

    private DatapackRegistration() {
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }

        boolean defaultEnable = CommonConfig.DEFAULT_ENABLE_STRUCTURE_WAYPOINTS.get();
        PackSource packSource = defaultEnable ? PackSource.BUILT_IN : PackSource.FEATURE;

        event.addPackFinders(
                ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, DATAPACK_PATH),
                PackType.SERVER_DATA,
                Component.translatable("datapack.teleportwaypoint.vanilla_structure_waypoints.name"),
                packSource,
                false,
                Pack.Position.TOP
        );
    }
}
