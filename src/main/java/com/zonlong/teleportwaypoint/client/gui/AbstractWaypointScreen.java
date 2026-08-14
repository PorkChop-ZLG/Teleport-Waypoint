package com.zonlong.teleportwaypoint.client.gui;

import com.zonlong.teleportwaypoint.menu.AbstractWaypointMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Common base for our GUIs: extends plain {@link Screen} (not AbstractContainerScreen) and
 * implements {@link MenuAccess}, mirroring Balm's approach so that no vanilla container chrome
 * ("物品栏"/villager labels) is rendered.
 */
public abstract class AbstractWaypointScreen<T extends AbstractWaypointMenu> extends Screen implements MenuAccess<T> {
    protected final T menu;
    protected final BlockPos pos;

    protected AbstractWaypointScreen(T menu, Component title) {
        super(title);
        this.menu = menu;
        this.pos = menu.getPos();
    }

    @Override
    public T getMenu() {
        return menu;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Opaque-ish dark background instead of the blurred vanilla background, so text stays crisp.
        guiGraphics.fill(0, 0, width, height, 0xC0101010);
    }

    @Override
    public void removed() {
        if (minecraft != null && minecraft.player != null) {
            menu.removed(minecraft.player);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.closeContainer();
        }
        super.onClose();
    }
}
