package com.zonlong.teleportwaypoint;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.zonlong.teleportwaypoint.block.ModBlocks;
import com.zonlong.teleportwaypoint.block.entity.ModBlockEntities;
import com.zonlong.teleportwaypoint.item.ModItems;
import com.zonlong.teleportwaypoint.menu.ModMenus;
import com.zonlong.teleportwaypoint.network.ModNetwork;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(TeleportWaypoint.MODID)
public class TeleportWaypoint {
    public static final String MODID = "teleportwaypoint";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TeleportWaypoint(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_MODE_TABS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        modEventBus.addListener(ModNetwork::register);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
