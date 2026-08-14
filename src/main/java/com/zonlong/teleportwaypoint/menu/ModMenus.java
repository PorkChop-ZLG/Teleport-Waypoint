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

    public static final DeferredHolder<MenuType<?>, MenuType<RenameWaypointMenu>> RENAME_WAYPOINT =
            MENUS.register("rename_waypoint", () -> new MenuType<>(
                    (IContainerFactory<RenameWaypointMenu>) (windowId, inv, data) -> new RenameWaypointMenu(ModMenus.RENAME_WAYPOINT.get(), windowId, readPos(data)),
                    FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<RenamePocketWaypointMenu>> RENAME_POCKET_WAYPOINT =
            MENUS.register("rename_pocket_waypoint", () -> new MenuType<>(
                    (IContainerFactory<RenamePocketWaypointMenu>) (windowId, inv, data) -> new RenamePocketWaypointMenu(ModMenus.RENAME_POCKET_WAYPOINT.get(), windowId, readPos(data)),
                    FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<WaypointListMenu>> WAYPOINT_LIST =
            MENUS.register("waypoint_list", () -> new MenuType<>(
                    (IContainerFactory<WaypointListMenu>) (windowId, inv, data) -> new WaypointListMenu(ModMenus.WAYPOINT_LIST.get(), windowId, readPos(data)),
                    FeatureFlags.VANILLA_SET));

    private static BlockPos readPos(RegistryFriendlyByteBuf data) {
        return data == null ? BlockPos.ZERO : data.readBlockPos();
    }
}
