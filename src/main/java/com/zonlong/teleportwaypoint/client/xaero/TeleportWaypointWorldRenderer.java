package com.zonlong.teleportwaypoint.client.xaero;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zonlong.teleportwaypoint.TeleportWaypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

import xaero.map.element.render.ElementRenderInfo;
import xaero.map.element.render.ElementRenderLocation;
import xaero.map.element.render.ElementRenderProvider;
import xaero.map.element.render.ElementReader;
import xaero.map.element.render.ElementRenderer;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;

/**
 * Renders teleport waypoint markers on Xaero's world map using dedicated crystal
 * icons: red for inactive regular waypoints, cyan for active regular waypoints,
 * yellow for inactive pocket waypoints and green for active pocket waypoints.
 * When hovered, the waypoint name is drawn directly above the icon, matching the
 * native Xaero Waypoint behavior instead of using a mouse-position tooltip.
 */
public class TeleportWaypointWorldRenderer
        extends ElementRenderer<TeleportWaypointElement, TeleportWaypointContext, TeleportWaypointWorldRenderer> {

    private static final int ICON_SIZE = 32;
    private static final ResourceLocation WAYPOINT_ACTIVE = ResourceLocation.fromNamespaceAndPath(
            TeleportWaypoint.MODID, "textures/gui/waypoint_active.png");
    private static final ResourceLocation WAYPOINT_INACTIVE = ResourceLocation.fromNamespaceAndPath(
            TeleportWaypoint.MODID, "textures/gui/waypoint_inactive.png");
    private static final ResourceLocation POCKET_WAYPOINT_ACTIVE = ResourceLocation.fromNamespaceAndPath(
            TeleportWaypoint.MODID, "textures/gui/pocket_waypoint_active.png");
    private static final ResourceLocation POCKET_WAYPOINT_INACTIVE = ResourceLocation.fromNamespaceAndPath(
            TeleportWaypoint.MODID, "textures/gui/pocket_waypoint_inactive.png");

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
        getContext().mapDimension = renderInfo.mapDimension;
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
        ResourceLocation texture;
        if (!element.activated()) {
            texture = element.pocket() ? POCKET_WAYPOINT_INACTIVE : WAYPOINT_INACTIVE;
        } else {
            texture = element.pocket() ? POCKET_WAYPOINT_ACTIVE : WAYPOINT_ACTIVE;
        }
        int half = ICON_SIZE / 2;
        guiGraphics.blit(texture, -half, -half, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

        if (hovered) {
            renderHoverName(guiGraphics, element);
        }
        return true;
    }

    private static void renderHoverName(GuiGraphics guiGraphics, TeleportWaypointElement element) {
        Font font = Minecraft.getInstance().font;
        String name = element.info().displayName().getString();
        int nameWidth = font.width(name);
        int backgroundWidth = Math.max(nameWidth + 4, 12);
        int halfBackgroundWidth = backgroundWidth / 2;

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        // Position the label directly above the 32x32 icon (icon top is -16).
        pose.translate(0.0F, -18.0F, 0.0F);
        pose.scale(3.0F, 3.0F, 1.0F);
        guiGraphics.fill(-halfBackgroundWidth, -1, halfBackgroundWidth, 8, 0x80000000);
        guiGraphics.drawString(font, name, -nameWidth / 2, 0, 0xFFFFFFFF, false);
        pose.popPose();
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
