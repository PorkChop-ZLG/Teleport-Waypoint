package com.zonlong.teleportwaypoint.client.gui;

import java.util.UUID;

import com.zonlong.teleportwaypoint.block.entity.WaypointBlockEntity;
import com.zonlong.teleportwaypoint.client.ClientWaypointState;
import com.zonlong.teleportwaypoint.menu.WaypointListMenu;
import com.zonlong.teleportwaypoint.network.ActivatedWaypointInfo;
import com.zonlong.teleportwaypoint.network.OpenRenameScreenPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class WaypointListScreen extends AbstractContainerScreen<WaypointListMenu> {
    private final BlockPos pos;
    private UUID selfUid;
    private WaypointBlockEntity selfEntity;
    private boolean canEditName;
    private EditBox searchBox;
    private WaypointList waypointList;
    private String searchText = "";

    private static final int NAME_HEADER_Y = 18;

    public WaypointListScreen(WaypointListMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.pos = menu.getPos();
    }

    @Override
    protected void init() {
        this.imageWidth = 220;
        this.imageHeight = 210;
        super.init();

        selfUid = null;
        selfEntity = null;
        canEditName = false;
        if (minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof WaypointBlockEntity wbe) {
            selfEntity = wbe;
            selfUid = wbe.getExistingUid();
            canEditName = canEdit(wbe);
        }

        searchBox = new EditBox(font, leftPos + 10, topPos + 44, imageWidth - 20, 20, Component.translatable("gui.teleportwaypoint.search"));
        searchBox.setMaxLength(64);
        searchBox.setResponder(text -> {
            searchText = text;
            updateList();
        });
        addRenderableWidget(searchBox);

        waypointList = new WaypointList(leftPos + 10, topPos + 70, imageWidth - 20, imageHeight - 80, selfUid);
        addRenderableWidget(waypointList);
        updateList();
    }

    private boolean canEdit(WaypointBlockEntity wbe) {
        if (minecraft == null || minecraft.player == null) {
            return false;
        }
        if (wbe.isPocketWaypoint()) {
            return wbe.getOwner() != null && wbe.getOwner().equals(minecraft.player.getUUID());
        }
        return minecraft.player.isCreative();
    }

    private void updateList() {
        String query = searchText == null ? "" : searchText.toLowerCase();
        var filtered = ClientWaypointState.getActivated().stream()
                .filter(info -> selfUid == null || !info.uid().equals(selfUid))
                .filter(info -> query.isEmpty() || info.toComponent().getString().toLowerCase().contains(query))
                .toList();
        waypointList.setWaypoints(filtered);
    }

    private boolean isNameHeaderHovered(double mouseX, double mouseY) {
        if (selfEntity == null || !canEditName) {
            return false;
        }
        Component name = selfEntity.getDisplayName();
        int halfWidth = font.width(name) / 2;
        int centerX = leftPos + imageWidth / 2;
        int y = topPos + NAME_HEADER_Y;
        return mouseX >= centerX - halfWidth - 8 && mouseX < centerX + halfWidth + 8
                && mouseY >= y && mouseY < y + font.lineHeight;
    }

    private void openRename() {
        PacketDistributor.sendToServer(new OpenRenameScreenPayload(pos));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isNameHeaderHovered(mouseX, mouseY)) {
            openRename();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Line 1: title
        guiGraphics.drawCenteredString(font, title, imageWidth / 2, 6, 0xFFFFFFFF);

        // Line 2: current waypoint name (clickable when the player may rename)
        if (selfEntity != null) {
            Component name = selfEntity.getDisplayName();
            boolean hovered = isNameHeaderHovered(mouseX, mouseY);
            int color = hovered ? 0xFFFFFF55 : 0xFFFFFFFF;
            guiGraphics.drawCenteredString(font, name, imageWidth / 2, NAME_HEADER_Y, color);
            if (hovered) {
                int halfWidth = font.width(name) / 2;
                guiGraphics.drawString(font, Component.literal("\u270E"), imageWidth / 2 + halfWidth + 4, NAME_HEADER_Y, 0xFFFFFFFF, false);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xC0101010);
    }
}
