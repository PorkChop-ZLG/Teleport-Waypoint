package com.zonlong.teleportwaypoint.menu;

import com.zonlong.teleportwaypoint.TeleportWaypoint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, TeleportWaypoint.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<WaypointMenu>> WAYPOINT =
            MENUS.register("waypoint", () -> new MenuType<>(
                    (IContainerFactory<WaypointMenu>) (windowId, inv, data) -> new WaypointMenu(ModMenus.WAYPOINT.get(), windowId, readPos(data)),
                    FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<PocketWaypointMenu>> POCKET_WAYPOINT =
            MENUS.register("pocket_waypoint", () -> new MenuType<>(
                    (IContainerFactory<PocketWaypointMenu>) (windowId, inv, data) -> new PocketWaypointMenu(ModMenus.POCKET_WAYPOINT.get(), windowId, readPos(data)),
                    FeatureFlags.VANILLA_SET));

    private static BlockPos readPos(RegistryFriendlyByteBuf data) {
        return data == null ? BlockPos.ZERO : data.readBlockPos();
    }
}
