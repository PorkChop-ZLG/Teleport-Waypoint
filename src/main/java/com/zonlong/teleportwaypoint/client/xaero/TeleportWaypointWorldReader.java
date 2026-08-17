package com.zonlong.teleportwaypoint.client.xaero;

import java.util.ArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import xaero.lib.client.gui.widget.Tooltip;
import xaero.map.element.render.ElementReader;
import xaero.map.element.render.ElementRenderLocation;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

/**
 * Reader for teleport waypoint elements on Xaero's world map. It controls hover
 * boxes, tooltip text, and the right-click menu.
 */
public class TeleportWaypointWorldReader
        extends ElementReader<TeleportWaypointElement, TeleportWaypointContext, TeleportWaypointWorldRenderer> {

    private static final int ICON_HALF = 7;
    private static final int BOX_TOP = -12;
    private static final int BOX_BOTTOM = 12;

    @Override
    public boolean isHidden(TeleportWaypointElement element, TeleportWaypointContext context) {
        return false;
    }

    @Override
    public boolean isInteractable(ElementRenderLocation location, TeleportWaypointElement element) {
        return true;
    }

    @Override
    public double getRenderX(TeleportWaypointElement element, TeleportWaypointContext context, float partialTicks) {
        return element.getX() + 0.5;
    }

    @Override
    public double getRenderZ(TeleportWaypointElement element, TeleportWaypointContext context, float partialTicks) {
        return element.getZ() + 0.5;
    }

    @Override
    public int getInteractionBoxLeft(TeleportWaypointElement element, TeleportWaypointContext context, float partialTicks) {
        return -ICON_HALF;
    }

    @Override
    public int getInteractionBoxRight(TeleportWaypointElement element, TeleportWaypointContext context, float partialTicks) {
        return ICON_HALF;
    }

    @Override
    public int getInteractionBoxTop(TeleportWaypointElement element, TeleportWaypointContext context, float partialTicks) {
        return BOX_TOP;
    }

    @Override
    public int getInteractionBoxBottom(TeleportWaypointElement element, TeleportWaypointContext context, float partialTicks) {
        return BOX_BOTTOM;
    }

    @Override
    public int getRenderBoxLeft(TeleportWaypointElement element, TeleportWaypointContext context, float partialTicks) {
        return -ICON_HALF;
    }

    @Override
    public int getRenderBoxRight(TeleportWaypointElement element, TeleportWaypointContext context, float partialTicks) {
        return ICON_HALF;
    }

    @Override
    public int getRenderBoxTop(TeleportWaypointElement element, TeleportWaypointContext context, float partialTicks) {
        return BOX_TOP;
    }

    @Override
    public int getRenderBoxBottom(TeleportWaypointElement element, TeleportWaypointContext context, float partialTicks) {
        return BOX_BOTTOM;
    }

    @Override
    public int getLeftSideLength(TeleportWaypointElement element, Minecraft mc) {
        return 10;
    }

    @Override
    public String getMenuName(TeleportWaypointElement element) {
        return element.name();
    }

    @Override
    public String getFilterName(TeleportWaypointElement element) {
        return element.name();
    }

    @Override
    public int getMenuTextFillLeftPadding(TeleportWaypointElement element) {
        return 0;
    }

    @Override
    public int getRightClickTitleBackgroundColor(TeleportWaypointElement element) {
        return colorFor(element);
    }

    @Override
    public boolean shouldScaleBoxWithOptionalScale() {
        return true;
    }

    @Override
    public ArrayList<RightClickOption> getRightClickOptions(TeleportWaypointElement element, IRightClickableElement target) {
        ArrayList<RightClickOption> options = new ArrayList<>();
        if (element.activated()) {
            options.add(new TeleportRightClickOption(
                    element.uid(),
                    "gui.teleportwaypoint.map_teleport",
                    0,
                    target));
        }
        return options;
    }

    @Override
    public boolean isRightClickValid(TeleportWaypointElement element) {
        return element.activated();
    }

    @Override
    public Tooltip getTooltip(TeleportWaypointElement element, TeleportWaypointContext context, boolean hovered) {
        String text = element.name()
                + "\nX: " + element.getX()
                + ", Y: " + element.getY()
                + ", Z: " + element.getZ();
        return new Tooltip(Component.literal(text));
    }

    private static int colorFor(TeleportWaypointElement element) {
        if (!element.activated()) {
            return 0xFF9E9E9E;
        }
        return element.pocket() ? 0xFF66BB6A : 0xFF26C6DA;
    }
}
