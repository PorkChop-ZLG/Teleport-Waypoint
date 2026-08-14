package com.zonlong.teleportwaypoint.client.gui;

import java.util.function.Predicate;

import com.zonlong.teleportwaypoint.menu.AbstractWaypointMenu;
import com.zonlong.teleportwaypoint.menu.RenameMenu;
import com.zonlong.teleportwaypoint.network.RenameWaypointPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Base screen for the two rename GUIs. Rename capability and current name are provided by the menu
 * (computed on the server), not read from the client block entity.
 */
public abstract class AbstractRenameScreen<T extends AbstractWaypointMenu & RenameMenu> extends AbstractWaypointScreen<T> {
    private EditBox textEdit;
    private boolean canEdit;

    protected AbstractRenameScreen(T menu, Inventory inventory, Component title) {
        super(menu, title);
    }

    protected Predicate<String> textFilter() {
        return null;
    }

    @Override
    protected void init() {
        canEdit = menu.canEdit();
        String current = menu.getName();

        int boxWidth = 136;
        int boxX = width / 2 - boxWidth / 2;
        int boxY = height / 2 - 30;

        textEdit = new EditBox(font, boxX, boxY, boxWidth, 20, Component.empty());
        textEdit.setMaxLength(64);
        textEdit.setValue(current);
        textEdit.setEditable(canEdit);
        Predicate<String> filter = textFilter();
        if (filter != null) {
            textEdit.setFilter(filter);
        }
        addRenderableWidget(textEdit);

        addRenderableWidget(Button.builder(
                Component.translatable(canEdit ? "gui.teleportwaypoint.save" : "gui.teleportwaypoint.close"),
                btn -> onButton()).bounds(width / 2 - 25, height / 2, 50, 20).build());

        if (canEdit && current.isEmpty()) {
            setInitialFocus(textEdit);
        }
    }

    private void onButton() {
        if (canEdit) {
            // Server renames and then opens the waypoint list.
            PacketDistributor.sendToServer(new RenameWaypointPayload(pos, textEdit.getValue()));
        } else {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFFFF);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (textEdit != null && textEdit.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                onClose();
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                onButton();
            } else {
                textEdit.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
