package com.zonlong.teleportwaypoint.client.gui;

import com.zonlong.teleportwaypoint.block.entity.WaypointBlockEntity;
import com.zonlong.teleportwaypoint.menu.RenameWaypointMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public class RenameWaypointScreen extends AbstractRenameScreen<RenameWaypointMenu> {

    public RenameWaypointScreen(RenameWaypointMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected boolean canEdit(WaypointBlockEntity blockEntity, Player player) {
        return player != null && player.isCreative();
    }

    @Override
    protected String getCurrentText(WaypointBlockEntity blockEntity) {
        return blockEntity.getId();
    }
}
