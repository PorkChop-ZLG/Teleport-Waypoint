package com.zonlong.teleportwaypoint.client.xaero;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;

import xaero.map.element.render.ElementRenderInfo;
import xaero.map.element.render.ElementRenderLocation;
import xaero.map.element.render.ElementRenderProvider;
import xaero.map.element.render.ElementReader;
import xaero.map.element.render.ElementRenderer;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;

/**
 * Renders teleport waypoint markers on Xaero's world map. Activated waypoints are
 * colored, unactivated ones are gray; names are drawn when enabled.
 */
public class TeleportWaypointWorldRenderer
        extends ElementRenderer<TeleportWaypointElement, TeleportWaypointContext, TeleportWaypointWorldRenderer> {

    public TeleportWaypointWorldRenderer(
            TeleportWaypointContext context,
            ElementRenderProvider<TeleportWaypointElement, TeleportWaypointContext> provider,
            ElementReader<TeleportWaypointElement, TeleportWaypointContext, TeleportWaypointWorldRenderer> reader) {
        super(context, provider, reader);
    }

    @Override
    public void preRender(
            ElementRenderInfo renderInfo,
            MultiBufferSource.BufferSource bufferSource,
            MultiTextureRenderTypeRendererProvider rendererProvider,
            boolean hovered) {
        TeleportWaypointContext context = getContext();
        context.mapDimension = renderInfo.mapDimension;
        context.showWaypoints = XaeroIntegration.showWaypoints();
        context.showNames = XaeroIntegration.showWaypointNames();
    }

    @Override
    public void postRender(
            ElementRenderInfo renderInfo,
            MultiBufferSource.BufferSource bufferSource,
            MultiTextureRenderTypeRendererProvider rendererProvider,
            boolean hovered) {
    }

    @Override
    public void renderElementShadow(
            TeleportWaypointElement element,
            boolean hovered,
            float partialTicks,
            double x,
            double z,
            ElementRenderInfo renderInfo,
            GuiGraphics guiGraphics,
            MultiBufferSource.BufferSource bufferSource,
            MultiTextureRenderTypeRendererProvider rendererProvider) {
    }

    @Override
    public boolean renderElement(
            TeleportWaypointElement element,
            boolean hovered,
            double depth,
            float scale,
            double partialX,
            double partialZ,
            ElementRenderInfo renderInfo,
            GuiGraphics guiGraphics,
            MultiBufferSource.BufferSource bufferSource,
            MultiTextureRenderTypeRendererProvider rendererProvider) {
        int color = element.activated()
                ? (element.pocket() ? 0xFF66BB6A : 0xFF26C6DA)
                : 0xFF9E9E9E;

        guiGraphics.fill(-4, -4, 4, 4, color);
        guiGraphics.fill(-5, -5, -4, 5, 0xFF000000);
        guiGraphics.fill(4, -5, 5, 5, 0xFF000000);
        guiGraphics.fill(-4, -5, 4, -4, 0xFF000000);
        guiGraphics.fill(-4, 4, 4, 5, 0xFF000000);

        if (getContext().showNames) {
            guiGraphics.drawString(
                    Minecraft.getInstance().font,
                    Component.literal(element.name()),
                    7,
                    -4,
                    0xFFFFFFFF,
                    false);
        }
        return true;
    }

    @Override
    public boolean shouldRender(ElementRenderLocation location, boolean hovered) {
        return location == ElementRenderLocation.WORLD_MAP;
    }

    @Override
    public int getOrder() {
        return 201;
    }
}
