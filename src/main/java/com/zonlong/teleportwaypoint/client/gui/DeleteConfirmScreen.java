package com.zonlong.teleportwaypoint.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Confirmation dialog shown before removing a waypoint's activation from the player's list.
 */
public class DeleteConfirmScreen extends Screen {
    private final Runnable onConfirm;

    public DeleteConfirmScreen(Runnable onConfirm) {
        super(Component.translatable("gui.teleportwaypoint.delete_confirm_title"));
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.teleportwaypoint.confirm"), btn -> {
                    onConfirm.run();
                    onClose();
                }).bounds(width / 2 - 105, height / 2 + 20, 100, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.teleportwaypoint.cancel"), btn -> onClose())
                .bounds(width / 2 + 5, height / 2 + 20, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, height / 2 - 30, 0xFFFFFFFF);
        Component desc = Component.translatable("gui.teleportwaypoint.delete_confirm_desc");
        guiGraphics.drawWordWrap(font, desc, width / 2 - 130, height / 2 - 10, 260, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
