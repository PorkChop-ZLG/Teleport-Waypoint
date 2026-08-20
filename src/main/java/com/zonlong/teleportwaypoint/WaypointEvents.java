package com.zonlong.teleportwaypoint;

import com.zonlong.teleportwaypoint.core.TeleportRateLimiter;
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
            WaypointManager.syncTo(player);
            WaypointManager.syncDimensionTo(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WaypointManager.syncDimensionTo(player, event.getTo());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TeleportRateLimiter.remove(player.getUUID());
        }
    }
}
