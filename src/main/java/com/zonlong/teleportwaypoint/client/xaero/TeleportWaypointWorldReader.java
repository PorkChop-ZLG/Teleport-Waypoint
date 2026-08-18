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

    private static final int ICON_HALF = 16;
    private static final int BOX_TOP = -16;
    private static final int BOX_BOTTOM = 16;

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
        return Component.translatable("gui.teleportwaypoint.map_menu_name",
                element.info().displayName().getString()).getString();
    }

    @Override
    public String getFilterName(TeleportWaypointElement element) {
        return element.info().displayName().getString();
    }

    @Override
    public int getMenuTextFillLeftPadding(TeleportWaypointElement element) {
        return 0;
    }

    @Override
    public int getRightClickTitleBackgroundColor(TeleportWaypointElement element) {
        // Use a neutral gray so the first menu row (the waypoint name) is highlighted
        // like Xaero's "Choose an Option" placeholder instead of using state colors.
        return 0xFF6B6B6B;
    }

    @Override
    public boolean shouldScaleBoxWithOptionalScale() {
        return true;
    }

    @Override
    public ArrayList<RightClickOption> getRightClickOptions(TeleportWaypointElement element, IRightClickableElement target) {
        ArrayList<RightClickOption> options = new ArrayList<>();

        String name = Component.translatable("gui.teleportwaypoint.map_menu_name",
                element.info().displayName().getString()).getString();
        options.add(new TeleportWaypointInfoOption(name, options.size(), target));

        String coords = Component.translatable("gui.teleportwaypoint.map_coords",
                element.getX(), element.getY(), element.getZ()).getString();
        options.add(new TeleportWaypointInfoOption(coords, options.size(), target));

        TeleportRightClickOption teleport = new TeleportRightClickOption(
                element.uid(),
                "gui.teleportwaypoint.map_teleport",
                options.size(),
                target);
        teleport.setActive(element.activated());
        options.add(teleport);

        return options;
    }

    @Override
    public boolean isRightClickValid(TeleportWaypointElement element) {
        return true;
    }

    @Override
    public Tooltip getTooltip(TeleportWaypointElement element, TeleportWaypointContext context, boolean hovered) {
        return new TeleportWaypointTooltip(element.info().displayName());
    }
}
