package com.zonlong.teleportwaypoint.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.MenuType;

public class RenameWaypointMenu extends AbstractWaypointMenu implements RenameMenu {
    private final boolean canEdit;
    private final String name;

    public RenameWaypointMenu(MenuType<?> type, int containerId, BlockPos pos, boolean canEdit, String name) {
        super(type, containerId, pos);
        this.canEdit = canEdit;
        this.name = name;
    }

    @Override
    public boolean canEdit() {
        return canEdit;
    }

    @Override
    public String getName() {
        return name;
    }
}
