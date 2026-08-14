package com.zonlong.teleportwaypoint.item;

import com.zonlong.teleportwaypoint.TeleportWaypoint;
import com.zonlong.teleportwaypoint.block.ModBlocks;

import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TeleportWaypoint.MODID);

    public static final DeferredItem<BlockItem> POCKET_WAYPOINT =
            ITEMS.registerSimpleBlockItem("pocket_waypoint", ModBlocks.POCKET_WAYPOINT);
}
