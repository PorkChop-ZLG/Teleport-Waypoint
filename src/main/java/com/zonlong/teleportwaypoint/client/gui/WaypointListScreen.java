package com.zonlong.teleportwaypoint.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.zonlong.teleportwaypoint.block.entity.WaypointBlockEntity;
import com.zonlong.teleportwaypoint.client.ClientWaypointState;
import com.zonlong.teleportwaypoint.menu.WaypointListMenu;
import com.zonlong.teleportwaypoint.network.ActivatedWaypointInfo;
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
    private static final int IMAGE_WIDTH = 270;
    private static final int IMAGE_HEIGHT = 200;
    private static final int HEADER_HEIGHT = 64;
    private static final int FOOTER_HEIGHT = 25;
    private static final int ENTRY_WIDTH = 220;
    private static final int MARGIN = 2;
    private static final int SORT_BUTTON_WIDTH = 20;

    private int leftPos;
    private int topPos;

    private UUID selfUid;
    private WaypointBlockEntity selfEntity;
    private boolean canEditName;
    private EditBox searchBox;
    private WaypointList waypointList;
    private String searchText = "";
    private boolean sortByName = false;
    private Button sortButton;

    public WaypointListScreen(WaypointListMenu menu, Inventory inventory, Component title) {
        super(menu, title);
    }

    @Override
    protected void init() {
        leftPos = (width - IMAGE_WIDTH) / 2;
        topPos = (height - IMAGE_HEIGHT) / 2;

        selfUid = null;
        selfEntity = null;
        canEditName = false;
        if (minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof WaypointBlockEntity wbe) {
            selfEntity = wbe;
            selfUid = wbe.getExistingUid();
            canEditName = canEdit(wbe);
        }

        int searchBoxWidth = ENTRY_WIDTH - MARGIN - SORT_BUTTON_WIDTH;
        searchBox = new EditBox(font, width / 2 - ENTRY_WIDTH / 2, topPos + HEADER_HEIGHT - 24, searchBoxWidth, 20,
                Component.translatable("gui.teleportwaypoint.search"));
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
                .bounds(searchBox.getX() + searchBox.getWidth() + MARGIN, searchBox.getY(), SORT_BUTTON_WIDTH, 20).build();
        addRenderableWidget(sortButton);

        waypointList = new WaypointList(leftPos, topPos + HEADER_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT - HEADER_HEIGHT - FOOTER_HEIGHT, selfUid,
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

    private void updateList() {
        String query = searchText == null ? "" : searchText.toLowerCase();
        List<ActivatedWaypointInfo> list = new ArrayList<>();
        for (ActivatedWaypointInfo info : ClientWaypointState.getActivated()) {
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
                && mouseY >= topPos + 20 && mouseY < topPos + 20 + font.lineHeight;
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

        // Line 1: title (Waystones headerY = 0 relative to topPos)
        guiGraphics.drawCenteredString(font, title, width / 2, topPos, 0xFFFFFFFF);

        // Line 2: current waypoint name (Waystones locationHeaderY = 20)
        if (selfEntity != null) {
            Component name = selfEntity.getDisplayName();
            boolean hovered = isNameHeaderHovered(mouseX, mouseY);
            int color = hovered ? 0xFFFFFF55 : 0xFFFFFFFF;
            guiGraphics.drawCenteredString(font, name, width / 2, topPos + 20, color);
            if (hovered) {
                int halfWidth = font.width(name) / 2;
                guiGraphics.drawString(font, Component.literal("\u270E"), width / 2 + halfWidth + 4, topPos + 20, 0xFFFFFFFF, false);
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
