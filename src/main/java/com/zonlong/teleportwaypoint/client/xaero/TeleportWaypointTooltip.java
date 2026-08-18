package com.zonlong.teleportwaypoint.client.xaero;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import xaero.lib.client.gui.widget.Tooltip;

/**
 * A {@link Tooltip} that draws above the mouse cursor instead of at the default
 * lower-right offset. Used for teleport waypoint hover names on Xaero's world map.
 */
public class TeleportWaypointTooltip extends Tooltip {

    private static final int GAP = 2;

    public TeleportWaypointTooltip(Component text) {
        super(text);
    }

    @Override
    public void drawBox(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        int lines = countLines();
        int tooltipHeight = 5 + lines * 10 + 5;
        // super.drawBox() adds +10 to the y coordinate, so shift up by height + 10 + gap.
        int aboveY = y - tooltipHeight - 10 - GAP;
        if (aboveY >= 0) {
            super.drawBox(guiGraphics, x, aboveY, width, height);
        } else {
            // Not enough room above; fall back to the default below-mouse position.
            super.drawBox(guiGraphics, x, y, width, height);
        }
    }

    private int countLines() {
        // Force the tooltip lines to be built.
        getPlainText();
        int count = 0;
        try {
            while (true) {
                getLine(count);
                count++;
            }
        } catch (IndexOutOfBoundsException ignored) {
            return count;
        }
    }
}
