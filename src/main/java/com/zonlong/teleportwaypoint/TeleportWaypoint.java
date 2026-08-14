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

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(TeleportWaypoint.MODID)
public class TeleportWaypoint {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "teleportwaypoint";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public TeleportWaypoint(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_MODE_TABS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        modEventBus.addListener(ModNetwork::register);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
