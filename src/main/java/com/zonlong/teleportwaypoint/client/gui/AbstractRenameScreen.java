package com.zonlong.teleportwaypoint.client.gui;

import com.zonlong.teleportwaypoint.block.entity.WaypointBlockEntity;
import com.zonlong.teleportwaypoint.menu.AbstractWaypointMenu;
import com.zonlong.teleportwaypoint.network.RenameWaypointPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Base screen for the two rename GUIs (rename waypoint / rename pocket waypoint).
 */
public abstract class AbstractRenameScreen<T extends AbstractWaypointMenu> extends AbstractContainerScreen<T> {
    protected final BlockPos pos;
    private EditBox textEdit;
    private boolean canEdit;

    protected AbstractRenameScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.pos = menu.getPos();
    }

    protected abstract boolean canEdit(WaypointBlockEntity blockEntity, Player player);

    protected abstract String getCurrentText(WaypointBlockEntity blockEntity);

    @Override
    protected void init() {
        this.imageWidth = 176;
        this.imageHeight = 110;
        super.init();

        canEdit = false;
        String current = "";
        if (minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof WaypointBlockEntity wbe) {
            canEdit = canEdit(wbe, minecraft.player);
            current = getCurrentText(wbe);
        }

        textEdit = new EditBox(font, leftPos + 20, topPos + 36, imageWidth - 40, 20, Component.empty());
        textEdit.setMaxLength(64);
        textEdit.setValue(current);
        textEdit.setEditable(canEdit);
        addRenderableWidget(textEdit);

        addRenderableWidget(Button.builder(
                Component.translatable(canEdit ? "gui.teleportwaypoint.save" : "gui.teleportwaypoint.close"),
                btn -> onButton()).bounds(leftPos + (imageWidth - 50) / 2, topPos + 62, 50, 20).build());

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
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xC0101010);
    }
}
