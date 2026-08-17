package com.zonlong.teleportwaypoint.client.xaero;

import net.minecraft.client.gui.screens.Screen;

import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

/**
 * A non-interactive right-click menu row used to display information such as the
 * waypoint coordinates. It is rendered grayed out and never performs an action.
 */
public class TeleportWaypointInfoOption extends RightClickOption {

    public TeleportWaypointInfoOption(String name, int index, IRightClickableElement target) {
        this(name, index, target, true);
    }

    public TeleportWaypointInfoOption(String name, int index, IRightClickableElement target, boolean active) {
        super(name, index, target);
        setActive(active);
    }

    @Override
    public void onAction(Screen screen) {
        // Information row: intentionally does nothing.
    }
}
