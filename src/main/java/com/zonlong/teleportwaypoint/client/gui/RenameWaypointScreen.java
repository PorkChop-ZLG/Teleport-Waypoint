package com.zonlong.teleportwaypoint.client.gui;

import java.util.function.Predicate;

import com.zonlong.teleportwaypoint.menu.RenameWaypointMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class RenameWaypointScreen extends AbstractRenameScreen<RenameWaypointMenu> {

    public RenameWaypointScreen(RenameWaypointMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected Predicate<String> textFilter() {
        return s -> s.matches("[a-z0-9_]*");
    }
}
