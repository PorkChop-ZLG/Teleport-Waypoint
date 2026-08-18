package com.zonlong.teleportwaypoint.client.xaero;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
        int boxWidth = computeBoxWidth();
        // super.drawBox() offsets x by +12 and y by +10, so shift both to center the
        // box horizontally and place it directly above the mouse.
        int centeredX = x - boxWidth / 2 - 12;
        int aboveY = y - tooltipHeight - 10 - GAP;
        if (aboveY >= 0) {
            super.drawBox(guiGraphics, centeredX, aboveY, width, height);
        } else {
            // Not enough room above; fall back to the default below-mouse position.
            super.drawBox(guiGraphics, centeredX, y, width, height);
        }
    }

    private int computeBoxWidth() {
        getPlainText(); // Force the tooltip lines to be built.
        Font font = Minecraft.getInstance().font;
        int maxLineWidth = 0;
        int index = 0;
        try {
            while (true) {
                Component line = getLine(index);
                maxLineWidth = Math.max(maxLineWidth, font.width(line));
                index++;
            }
        } catch (IndexOutOfBoundsException ignored) {
            // Reached the end of the lines.
        }
        // Tooltip.drawBox() adds 20px of horizontal padding around the text.
        return 20 + maxLineWidth;
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
