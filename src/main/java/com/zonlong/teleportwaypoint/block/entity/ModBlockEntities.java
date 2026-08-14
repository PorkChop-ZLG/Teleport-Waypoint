package com.zonlong.teleportwaypoint.block.entity;

import com.zonlong.teleportwaypoint.TeleportWaypoint;
import com.zonlong.teleportwaypoint.block.ModBlocks;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TeleportWaypoint.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaypointBlockEntity>> WAYPOINT =
            BLOCK_ENTITIES.register("waypoint", () -> BlockEntityType.Builder
                    .of(WaypointBlockEntity::new, ModBlocks.WAYPOINT.get(), ModBlocks.POCKET_WAYPOINT.get())
                    .build(null));
}
