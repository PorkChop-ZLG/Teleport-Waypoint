package com.zonlong.teleportwaypoint.block;

import com.zonlong.teleportwaypoint.TeleportWaypoint;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TeleportWaypoint.MODID);

    // Breakable waypoint with obsidian hardness/resistance and no drops.
    public static final DeferredBlock<WaypointBlock> WAYPOINT = BLOCKS.register("waypoint",
            () -> new WaypointBlock(BlockBehaviour.Properties.of()
                    .strength(50.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.GLASS)
                    .noLootTable()
                    .noOcclusion()));

    // Breakable pocket waypoint: stone hardness, obsidian blast resistance, requires a pickaxe, drops itself.
    public static final DeferredBlock<PocketWaypointBlock> POCKET_WAYPOINT = BLOCKS.register("pocket_waypoint",
            () -> new PocketWaypointBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.GLASS)
                    .noOcclusion()));
}
