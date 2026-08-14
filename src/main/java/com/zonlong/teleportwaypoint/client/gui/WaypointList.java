package com.zonlong.teleportwaypoint.client.gui;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.zonlong.teleportwaypoint.network.ActivatedWaypointInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

/**
 * Scrollable list of unlocked waypoints with a teleport button and a delete button per entry.
 * The currently-interacted waypoint is shown but its buttons are disabled.
 */
public class WaypointList extends ContainerObjectSelectionList<WaypointList.Entry> {
    private static final int DELETE_BUTTON_WIDTH = 20;

    private final int listWidth;
    private final UUID selfUid;
    private final Consumer<UUID> onTeleport;
    private final Consumer<UUID> onDelete;

    public WaypointList(int x, int y, int width, int height, UUID selfUid, Consumer<UUID> onTeleport, Consumer<UUID> onDelete) {
        super(Minecraft.getInstance(), width, height, y, 22);
        setX(x);
        this.listWidth = width;
        this.selfUid = selfUid;
        this.onTeleport = onTeleport;
        this.onDelete = onDelete;
    }

    public void setWaypoints(List<ActivatedWaypointInfo> infos) {
        clearEntries();
        for (ActivatedWaypointInfo info : infos) {
            addEntry(new Entry(info));
        }
    }

    @Override
    public int getRowWidth() {
        return listWidth;
    }

    @Override
    protected void renderListBackground(GuiGraphics guiGraphics) {
    }

    @Override
    protected void renderListSeparators(GuiGraphics guiGraphics) {
    }

    public class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        private final ActivatedWaypointInfo info;
        private final Button button;
        private final Button deleteButton;

        public Entry(ActivatedWaypointInfo info) {
            this.info = info;
            boolean isSelf = selfUid != null && info.uid().equals(selfUid);
            this.button = Button.builder(info.toComponent(), btn -> onTeleport.accept(info.uid())).build();
            this.button.active = !isSelf;
            this.deleteButton = Button.builder(Component.literal("\u2715"), btn -> onDelete.accept(info.uid())).build();
            this.deleteButton.active = !isSelf;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(button, deleteButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(button, deleteButton);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            button.setX(left);
            button.setY(top);
            button.setWidth(width - DELETE_BUTTON_WIDTH);
            button.setHeight(20);
            button.render(guiGraphics, mouseX, mouseY, partialTick);

            deleteButton.setX(left + width - DELETE_BUTTON_WIDTH);
            deleteButton.setY(top);
            deleteButton.setWidth(DELETE_BUTTON_WIDTH);
            deleteButton.setHeight(20);
            deleteButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }
}
