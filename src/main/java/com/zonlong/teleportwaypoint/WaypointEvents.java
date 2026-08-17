package com.zonlong.teleportwaypoint;

import com.zonlong.teleportwaypoint.core.WaypointManager;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = TeleportWaypoint.MODID)
public final class WaypointEvents {
    private WaypointEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WaypointManager.syncAllTo(player);
            WaypointManager.syncTo(player);
        }
    }
}
