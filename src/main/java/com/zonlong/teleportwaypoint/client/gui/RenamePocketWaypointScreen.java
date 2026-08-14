package com.zonlong.teleportwaypoint.client.gui;

import com.zonlong.teleportwaypoint.menu.RenamePocketWaypointMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class RenamePocketWaypointScreen extends AbstractRenameScreen<RenamePocketWaypointMenu> {

    public RenamePocketWaypointScreen(RenamePocketWaypointMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
