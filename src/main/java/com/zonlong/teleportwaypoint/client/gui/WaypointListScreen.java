package com.zonlong.teleportwaypoint.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.zonlong.teleportwaypoint.block.entity.WaypointBlockEntity;
import com.zonlong.teleportwaypoint.client.ClientWaypointState;
import com.zonlong.teleportwaypoint.menu.WaypointListMenu;
import com.zonlong.teleportwaypoint.network.DeleteWaypointPayload;
import com.zonlong.teleportwaypoint.network.OpenRenameScreenPayload;
import com.zonlong.teleportwaypoint.network.TeleportRequestPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class WaypointListScreen extends AbstractWaypointScreen<WaypointListMenu> {
    private UUID selfUid;
    private WaypointBlockEntity selfEntity;
    private boolean canEditName;
    private EditBox searchBox;
    private WaypointList waypointList;
    private String searchText = "";
    private boolean sortByName = false;
    private Button sortButton;

    private static final int LIST_WIDTH = 200;

    public WaypointListScreen(WaypointListMenu menu, Inventory inventory, Component title) {
        super(menu, title);
    }

    @Override
    protected void init() {
        selfUid = null;
        selfEntity = null;
        canEditName = false;
        if (minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof WaypointBlockEntity wbe) {
            selfEntity = wbe;
            selfUid = wbe.getExistingUid();
            canEditName = canEdit(wbe);
        }

        int listX = width / 2 - LIST_WIDTH / 2;

        searchBox = new EditBox(font, listX, height / 2 - 60, LIST_WIDTH - 24, 20, Component.translatable("gui.teleportwaypoint.search"));
        searchBox.setMaxLength(64);
        searchBox.setResponder(text -> {
            searchText = text;
            updateList();
        });
        addRenderableWidget(searchBox);

        sortButton = Button.builder(Component.literal(sortByName ? "A-Z" : "\u2261"), btn -> {
                    sortByName = !sortByName;
                    btn.setMessage(Component.literal(sortByName ? "A-Z" : "\u2261"));
                    updateList();
                })
                .bounds(listX + LIST_WIDTH - 20, height / 2 - 60, 20, 20).build();
        addRenderableWidget(sortButton);

        waypointList = new WaypointList(listX, height / 2 - 35, LIST_WIDTH, 130, selfUid,
                target -> {
                    if (selfUid != null) {
                        PacketDistributor.sendToServer(new TeleportRequestPayload(selfUid, target));
                    }
                    onClose();
                },
                uid -> {
                    PacketDistributor.sendToServer(new DeleteWaypointPayload(uid));
                    ClientWaypointState.removeActivated(uid);
                    updateList();
                });
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

    private int nameHeaderY() {
        return height / 2 - 66;
    }

    private void updateList() {
        String query = searchText == null ? "" : searchText.toLowerCase();
        List<com.zonlong.teleportwaypoint.network.ActivatedWaypointInfo> list = new ArrayList<>();
        for (var info : ClientWaypointState.getActivated()) {
            if (!query.isEmpty() && !info.toComponent().getString().toLowerCase().contains(query)) {
                continue;
            }
            list.add(info);
        }
        if (sortByName) {
            list.sort(Comparator.comparing(info -> info.toComponent().getString()));
        }
        waypointList.setWaypoints(list);
    }

    private boolean isNameHeaderHovered(double mouseX, double mouseY) {
        if (selfEntity == null || !canEditName) {
            return false;
        }
        Component name = selfEntity.getDisplayName();
        int halfWidth = font.width(name) / 2;
        int centerX = width / 2;
        return mouseX >= centerX - halfWidth - 8 && mouseX < centerX + halfWidth + 8
                && mouseY >= nameHeaderY() && mouseY < nameHeaderY() + font.lineHeight;
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Line 1: title
        guiGraphics.drawCenteredString(font, title, width / 2, height / 2 - 64, 0xFFFFFFFF);

        // Line 2: current waypoint name (clickable when the player may rename)
        if (selfEntity != null) {
            Component name = selfEntity.getDisplayName();
            boolean hovered = isNameHeaderHovered(mouseX, mouseY);
            int color = hovered ? 0xFFFFFF55 : 0xFFFFFFFF;
            guiGraphics.drawCenteredString(font, name, width / 2, nameHeaderY(), color);
            if (hovered) {
                int halfWidth = font.width(name) / 2;
                guiGraphics.drawString(font, Component.literal("\u270E"), width / 2 + halfWidth + 4, nameHeaderY(), 0xFFFFFFFF, false);
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                onClose();
            } else {
                searchBox.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
