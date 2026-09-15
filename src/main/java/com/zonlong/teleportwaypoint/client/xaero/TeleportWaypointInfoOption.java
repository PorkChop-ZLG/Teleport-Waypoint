package com.zonlong.teleportwaypoint.client.xaero;

import net.minecraft.client.gui.screens.Screen;

import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

/**
 * A right-click menu row used to display information such as the waypoint name
 * or coordinates. It performs no action when clicked; depending on the active
 * flag it may be rendered normally or grayed out. Note that Xaero's native menu
 * still closes when such a row is clicked.
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
