package com.zonlong.teleportwaypoint.item;

import com.zonlong.teleportwaypoint.TeleportWaypoint;
import com.zonlong.teleportwaypoint.block.ModBlocks;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TeleportWaypoint.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TeleportWaypoint.MODID);

    public static final DeferredItem<BlockItem> WAYPOINT =
            ITEMS.registerSimpleBlockItem("waypoint", ModBlocks.WAYPOINT);

    public static final DeferredItem<BlockItem> POCKET_WAYPOINT =
            ITEMS.registerSimpleBlockItem("pocket_waypoint", ModBlocks.POCKET_WAYPOINT);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB =
            CREATIVE_MODE_TABS.register("teleportwaypoint", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.teleportwaypoint"))
                    .icon(() -> WAYPOINT.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(WAYPOINT.get());
                        output.accept(POCKET_WAYPOINT.get());
                    }).build());
}
