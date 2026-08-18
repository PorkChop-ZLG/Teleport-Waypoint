package com.zonlong.teleportwaypoint.client.xaero;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zonlong.teleportwaypoint.TeleportWaypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

import xaero.map.WorldMap;
import xaero.map.element.render.ElementRenderInfo;
import xaero.map.element.render.ElementRenderLocation;
import xaero.map.element.render.ElementRenderProvider;
import xaero.map.element.render.ElementReader;
import xaero.map.element.render.ElementRenderer;
import xaero.map.graphics.CustomRenderTypes;
import xaero.map.graphics.MapRenderHelper;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.map.misc.Misc;

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
    private VertexConsumer textBGConsumer;
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
        textBGConsumer = WorldMap.worldMapClientOnly.customVertexConsumers.getRenderTypeBuffers()
                .getBuffer(CustomRenderTypes.MAP_ELEMENT_TEXT_BG);
    }

    @Override
    public void postRender(
            ElementRenderInfo renderInfo,
            MultiBufferSource.BufferSource bufferSource,
            MultiTextureRenderTypeRendererProvider rendererProvider,
            boolean hovered) {
        if (textBGConsumer != null) {
            WorldMap.worldMapClientOnly.customVertexConsumers.getRenderTypeBuffers().endBatch();
            textBGConsumer = null;
        }
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
            renderHoverName(guiGraphics, element, bufferSource);
        }
        return true;
    }

    private void renderHoverName(GuiGraphics guiGraphics, TeleportWaypointElement element,
                                 MultiBufferSource.BufferSource bufferSource) {
        Font font = Minecraft.getInstance().font;
        String name = element.info().displayName().getString();
        int nameWidth = font.width(name);
        int backgroundWidth = Math.max(nameWidth + 4, 12);
        int halfBackgroundWidth = backgroundWidth / 2;

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        // Position the label directly above the 32x32 icon (icon top is -16).
        // With a 3x label scale the label body spans ~27px, so move the origin to -45
        // to keep the label just above the icon without overlapping it.
        pose.translate(0.0F, -45.0F, 0.0F);
        pose.scale(3.0F, 3.0F, 1.0F);

        if (textBGConsumer != null) {
            MapRenderHelper.fillIntoExistingBuffer(
                    pose.last().pose(), textBGConsumer,
                    -halfBackgroundWidth, -1, halfBackgroundWidth, 9,
                    0.0F, 0.0F, 0.0F, 0.7F);
        }
        // Push the text slightly forward on z so it is not occluded by the background.
        pose.translate(0.0F, 0.0F, 1.0F);
        Misc.drawNormalText(pose, name, -nameWidth / 2, 0, 0xFFFFFFFF, false, bufferSource);

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
