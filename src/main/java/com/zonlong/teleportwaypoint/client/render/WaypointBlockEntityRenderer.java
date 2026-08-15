package com.zonlong.teleportwaypoint.client.render;

import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import com.zonlong.teleportwaypoint.TeleportWaypoint;
import com.zonlong.teleportwaypoint.block.entity.WaypointBlockEntity;

/**
 * 传送锚点动态渲染器（BER）
 *
 * 游戏内模型 = 静态层（blockstate 模型：底座 + 支柱 + 柱顶水晶）
 *            + 动态层（本渲染器叠加，全亮自发光）：
 *   - 晶核（waypoint_crystal）：绕 Y 轴匀速自转，360° / 12 秒
 *   - 能量环（waypoint_ring）：绕 Y 轴反向慢转，360° / 24 秒，并上下轻柔浮动
 *   - 核心光球（waypoint_orb）：上下浮动 + 呼吸式脉冲缩放
 *
 * 口袋锚点暂不叠加动态层（其静态模型独立设计）。
 */
public class WaypointBlockEntityRenderer implements BlockEntityRenderer<WaypointBlockEntity> {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String MARKER = "[WaypointBER]";

    public static final ModelResourceLocation CRYSTAL_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "block/waypoint_crystal"));
    public static final ModelResourceLocation RING_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "block/waypoint_ring"));
    public static final ModelResourceLocation ORB_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(TeleportWaypoint.MODID, "block/waypoint_orb"));

    /** 全亮光照：动态部件自发光，不受昼夜/光照影响 */
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;

    private final RandomSource random = RandomSource.create();
    private int logCounter = 0;

    public WaypointBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        LOGGER.info("{} BER created", MARKER);
    }

    @Override
    public void render(WaypointBlockEntity blockEntity, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (blockEntity.getLevel() == null || blockEntity.isPocketWaypoint()) {
            return;
        }
        float t = blockEntity.getLevel().getGameTime() + partialTick;
        var modelManager = Minecraft.getInstance().getModelManager();

        // 诊断：每 100 帧输出一次渲染状态
        if (++logCounter % 100 == 1) {
            BakedModel missing = modelManager.getMissingModel();
            LOGGER.info("{} render tick={} crystal={} ring={} orb={}",
                    MARKER, blockEntity.getLevel().getGameTime(),
                    modelManager.getModel(CRYSTAL_MODEL) != missing,
                    modelManager.getModel(RING_MODEL) != missing,
                    modelManager.getModel(ORB_MODEL) != missing);
        }

        // 1) 晶核：绕方块中心 Y 轴自转（360° / 12 秒）
        renderBakedModel(pose, buffer, modelManager.getModel(CRYSTAL_MODEL), p -> {
            p.translate(0.5, 0.5, 0.5);
            p.mulPose(Axis.YP.rotationDegrees((t * 1.5F) % 360.0F));
            p.translate(-0.5, -0.5, -0.5);
        });

        // 2) 能量环：反向慢转（360° / 24 秒）+ 上下浮动
        renderBakedModel(pose, buffer, modelManager.getModel(RING_MODEL), p -> {
            p.translate(0.5, 0.5 + (float) Math.sin(t * 0.1) * 0.0625F, 0.5);
            p.mulPose(Axis.YP.rotationDegrees((-t * 0.75F) % 360.0F));
            p.translate(-0.5, -0.5, -0.5);
        });

        // 3) 核心光球：上下浮动 + 呼吸式脉冲缩放
        renderBakedModel(pose, buffer, modelManager.getModel(ORB_MODEL), p -> {
            p.translate(0.5, 0.5 + (float) Math.sin(t * 0.15) * 0.125F, 0.5);
            float scale = 1.0F + (float) Math.sin(t * 0.2) * 0.08F;
            p.scale(scale, scale, scale);
            p.translate(-0.5, -0.5, -0.5);
        });
    }

    /**
     * 以全亮光照渲染一个烘焙模型，transform 用于施加动画变换。
     * 注意：必须同时渲染 culled 面（有 cullface）与 unculled 面（无 cullface，
     * 通过 direction == null 查询）——动态模型的所有面都没有 cullface。
     */
    private void renderBakedModel(PoseStack pose, MultiBufferSource buffer, BakedModel model,
                                  Consumer<PoseStack> transform) {
        pose.pushPose();
        transform.accept(pose);
        VertexConsumer consumer = buffer.getBuffer(RenderType.solid());
        random.setSeed(42L);
        for (Direction direction : Direction.values()) {
            for (BakedQuad quad : model.getQuads(null, direction, random)) {
                consumer.putBulkData(pose.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
        }
        random.setSeed(42L);
        for (BakedQuad quad : model.getQuads(null, null, random)) {
            consumer.putBulkData(pose.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();
    }
}
