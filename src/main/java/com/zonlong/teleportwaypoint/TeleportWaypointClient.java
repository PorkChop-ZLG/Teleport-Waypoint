package com.zonlong.teleportwaypoint;

import com.zonlong.teleportwaypoint.client.gui.PocketWaypointScreen;
import com.zonlong.teleportwaypoint.client.gui.WaypointScreen;
import com.zonlong.teleportwaypoint.menu.ModMenus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = TeleportWaypoint.MODID, dist = Dist.CLIENT)
public class TeleportWaypointClient {
    public TeleportWaypointClient(IEventBus modEventBus, ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modEventBus.addListener(TeleportWaypointClient::onRegisterMenuScreens);
    }

    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.WAYPOINT.get(), WaypointScreen::new);
        event.register(ModMenus.POCKET_WAYPOINT.get(), PocketWaypointScreen::new);
    }
}
