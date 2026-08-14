package com.zonlong.teleportwaypoint.client.gui;

import java.util.UUID;

import com.zonlong.teleportwaypoint.block.entity.WaypointBlockEntity;
import com.zonlong.teleportwaypoint.client.ClientWaypointState;
import com.zonlong.teleportwaypoint.menu.PocketWaypointMenu;
import com.zonlong.teleportwaypoint.network.ActivatedWaypointInfo;
import com.zonlong.teleportwaypoint.network.RenameWaypointPayload;
import com.zonlong.teleportwaypoint.network.TeleportRequestPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class PocketWaypointScreen extends AbstractContainerScreen<PocketWaypointMenu> {
    private final BlockPos pos;
    private UUID selfUid;
    private EditBox nameEdit;
    private Button saveButton;

    public PocketWaypointScreen(PocketWaypointMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.pos = menu.getPos();
    }

    @Override
    protected void init() {
        this.imageWidth = 200;
        this.imageHeight = 180;
        super.init();

        selfUid = null;
        String currentName = "";
        if (minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof WaypointBlockEntity wbe) {
            selfUid = wbe.getExistingUid();
            currentName = wbe.getName();
        }

        int y = topPos + 24;
        int count = 0;
        for (ActivatedWaypointInfo info : ClientWaypointState.getActivated()) {
            if (selfUid != null && info.uid().equals(selfUid)) {
                continue;
            }
            if (count >= 5) {
                break;
            }
            final UUID target = info.uid();
            addRenderableWidget(Button.builder(info.toComponent(), btn -> teleport(target))
                    .bounds(leftPos + 10, y, imageWidth - 20, 20).build());
            y += 22;
            count++;
        }

        nameEdit = new EditBox(font, leftPos + 10, topPos + imageHeight - 42, imageWidth - 20, 20, Component.literal("name"));
        nameEdit.setMaxLength(64);
        nameEdit.setValue(currentName);
        addRenderableWidget(nameEdit);

        saveButton = Button.builder(Component.translatable("gui.teleportwaypoint.save"), btn -> save())
                .bounds(leftPos + 10, topPos + imageHeight - 20, imageWidth - 20, 18).build();
        addRenderableWidget(saveButton);
    }

    private void teleport(UUID target) {
        if (selfUid != null) {
            PacketDistributor.sendToServer(new TeleportRequestPayload(selfUid, target));
            onClose();
        }
    }

    private void save() {
        PacketDistributor.sendToServer(new RenameWaypointPayload(pos, nameEdit.getValue()));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xC0101010);
    }
}
