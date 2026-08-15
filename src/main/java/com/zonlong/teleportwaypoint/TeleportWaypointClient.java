package com.zonlong.teleportwaypoint;

import com.zonlong.teleportwaypoint.block.entity.ModBlockEntities;
import com.zonlong.teleportwaypoint.client.gui.RenamePocketWaypointScreen;
import com.zonlong.teleportwaypoint.client.gui.RenameWaypointScreen;
import com.zonlong.teleportwaypoint.client.gui.WaypointListScreen;
import com.zonlong.teleportwaypoint.client.render.WaypointBlockEntityRenderer;
import com.zonlong.teleportwaypoint.menu.ModMenus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
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
        modEventBus.addListener(TeleportWaypointClient::onRegisterEntityRenderers);
        modEventBus.addListener(TeleportWaypointClient::onRegisterAdditionalModels);
    }

    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.RENAME_WAYPOINT.get(), RenameWaypointScreen::new);
        event.register(ModMenus.RENAME_POCKET_WAYPOINT.get(), RenamePocketWaypointScreen::new);
        event.register(ModMenus.WAYPOINT_LIST.get(), WaypointListScreen::new);
    }

    /** 注册传送锚点方块实体渲染器（动态层：晶核旋转 / 能量环浮动 / 光球脉冲） */
    static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.WAYPOINT.get(), WaypointBlockEntityRenderer::new);
        org.slf4j.LoggerFactory.getLogger("teleportwaypoint")
                .info("[WaypointBER] registered block entity renderer for {}", ModBlockEntities.WAYPOINT.get());
    }

    /** 注册动态层附加模型，供 BER 烘焙使用 */
    static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(WaypointBlockEntityRenderer.CRYSTAL_MODEL);
        event.register(WaypointBlockEntityRenderer.RING_MODEL);
        event.register(WaypointBlockEntityRenderer.ORB_MODEL);
        org.slf4j.LoggerFactory.getLogger("teleportwaypoint")
                .info("[WaypointBER] registered additional models: crystal, ring, orb");
    }
}
