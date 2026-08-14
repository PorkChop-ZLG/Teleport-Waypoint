package com.zonlong.teleportwaypoint.client.gui;

import com.zonlong.teleportwaypoint.block.entity.WaypointBlockEntity;
import com.zonlong.teleportwaypoint.menu.RenamePocketWaypointMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public class RenamePocketWaypointScreen extends AbstractRenameScreen<RenamePocketWaypointMenu> {

    public RenamePocketWaypointScreen(RenamePocketWaypointMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected boolean canEdit(WaypointBlockEntity blockEntity, Player player) {
        return player != null && blockEntity.getOwner() != null && blockEntity.getOwner().equals(player.getUUID());
    }

    @Override
    protected String getCurrentText(WaypointBlockEntity blockEntity) {
        return blockEntity.getName();
    }
}
