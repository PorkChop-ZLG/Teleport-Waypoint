package com.zonlong.teleportwaypoint.client.gui;

import java.util.List;
import java.util.UUID;

import com.zonlong.teleportwaypoint.network.ActivatedWaypointInfo;
import com.zonlong.teleportwaypoint.network.TeleportRequestPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Scrollable list of unlocked waypoints (uses vanilla ContainerObjectSelectionList, which provides
 * the right-side scrollbar and mouse-wheel scrolling like the creative inventory).
 */
public class WaypointList extends ContainerObjectSelectionList<WaypointList.Entry> {
    private final int listWidth;
    private final UUID selfUid;

    public WaypointList(int x, int y, int width, int height, UUID selfUid) {
        super(Minecraft.getInstance(), width, height, y, 22);
        setX(x);
        this.listWidth = width;
        this.selfUid = selfUid;
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

        public Entry(ActivatedWaypointInfo info) {
            this.info = info;
            this.button = Button.builder(info.toComponent(), btn -> teleport()).build();
        }

        private void teleport() {
            if (selfUid != null) {
                PacketDistributor.sendToServer(new TeleportRequestPayload(selfUid, info.uid()));
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(button);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(button);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            button.setX(left);
            button.setY(top);
            button.setWidth(width);
            button.setHeight(20);
            button.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }
}
