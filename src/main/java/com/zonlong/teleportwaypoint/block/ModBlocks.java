package com.zonlong.teleportwaypoint.block;

import com.zonlong.teleportwaypoint.TeleportWaypoint;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TeleportWaypoint.MODID);

    // Unbreakable, translation-key-named waypoint placed by structures / creative mode.
    public static final DeferredBlock<WaypointBlock> WAYPOINT = BLOCKS.register("waypoint",
            () -> new WaypointBlock(BlockBehaviour.Properties.of().strength(-1.0F, 3600000.0F)));

    // Craftable, nameable waypoint with an owner.
    public static final DeferredBlock<PocketWaypointBlock> POCKET_WAYPOINT = BLOCKS.register("pocket_waypoint",
            () -> new PocketWaypointBlock(BlockBehaviour.Properties.of().strength(2.0F, 6.0F)));
}
