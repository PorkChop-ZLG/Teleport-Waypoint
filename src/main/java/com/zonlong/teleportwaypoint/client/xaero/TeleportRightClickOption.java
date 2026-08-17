package com.zonlong.teleportwaypoint.client.xaero;

import java.util.UUID;

import com.zonlong.teleportwaypoint.network.MapTeleportRequestPayload;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;

import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

/**
 * The only right-click option shown for activated teleport waypoints on Xaero's
 * world map. It sends our own server-validated teleport request.
 */
public class TeleportRightClickOption extends RightClickOption {
    private final UUID target;

    public TeleportRightClickOption(UUID target, String name, int index, IRightClickableElement targetElement) {
        super(name, index, targetElement);
        this.target = target;
    }

    @Override
    public void onAction(Screen screen) {
        PacketDistributor.sendToServer(new MapTeleportRequestPayload(target));
    }
}
