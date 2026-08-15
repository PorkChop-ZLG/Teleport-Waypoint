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
import com.zonlong.teleportwaypoint.client.ClientWaypointState;

/**
 * 传送锚点发光部件渲染器（BER）
 *
 * 状态外观（per-player）：已解锁 → 青色组（默认设计）；未解锁 → 红色组（红色主题贴图）。
 * 两组共用同一套动画变换：
 *   - 柱顶水晶（caps）：静止
 *   - 晶核（crystal）：绕 Y 轴匀速自转，360° / 12 秒
 *   - 能量环（ring）：绕 Y 轴反向慢转，360° / 24 秒，并上下轻柔浮动
 *   - 核心光球（orb）：上下浮动 + 呼吸式脉冲缩放
 * 全部全亮自发光（FULL_BRIGHT），夜晚清晰可见。
 *
 * 石材底座/支柱由 blockstate 模型渲染（本色，青色符文保留）。
 * 口袋锚点暂不渲染（其静态模型独立设计，状态外观另行处理）。
 */
public class WaypointBlockEntityRenderer implements BlockEntityRenderer<WaypointBlockEntity> {

    private static final String MODEL_PATH = TeleportWaypoint.MODID + ":block/";

    /** 青色组（已解锁，默认设计） */
    public static final ModelResourceLocation CAPS_MODEL = standalone("waypoint_caps");
    public static final ModelResourceLocation CRYSTAL_MODEL = standalone("waypoint_crystal");
    public static final ModelResourceLocation RING_MODEL = standalone("waypoint_ring");
    public static final ModelResourceLocation ORB_MODEL = standalone("waypoint_orb");

    /** 红色组（未解锁，红色主题贴图） */
    public static final ModelResourceLocation CAPS_RED_MODEL = standalone("waypoint_caps_red");
    public static final ModelResourceLocation CRYSTAL_RED_MODEL = standalone("waypoint_crystal_red");
    public static final ModelResourceLocation RING_RED_MODEL = standalone("waypoint_ring_red");
    public static final ModelResourceLocation ORB_RED_MODEL = standalone("waypoint_orb_red");

    private static ModelResourceLocation standalone(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.parse(MODEL_PATH + path));
    }

    /** 全亮光照：发光部件自发光，不受昼夜/光照影响 */
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;

    private final RandomSource random = RandomSource.create();

    public WaypointBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WaypointBlockEntity blockEntity, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (blockEntity.getLevel() == null || blockEntity.isPocketWaypoint()) {
            return;
        }
        float t = blockEntity.getLevel().getGameTime() + partialTick;
        var modelManager = Minecraft.getInstance().getModelManager();

        // 状态外观（per-player）：已解锁 → 青色组；未解锁 → 红色组
        boolean activated = ClientWaypointState.isActivated(blockEntity.getExistingUid());
        var caps = modelManager.getModel(activated ? CAPS_MODEL : CAPS_RED_MODEL);
        var crystal = modelManager.getModel(activated ? CRYSTAL_MODEL : CRYSTAL_RED_MODEL);
        var ring = modelManager.getModel(activated ? RING_MODEL : RING_RED_MODEL);
        var orb = modelManager.getModel(activated ? ORB_MODEL : ORB_RED_MODEL);

        // 1) 柱顶水晶：静止
        renderBakedModel(pose, buffer, caps, p -> { });

        // 2) 晶核：绕方块中心 Y 轴自转（360° / 12 秒）
        renderBakedModel(pose, buffer, crystal, p -> {
            p.translate(0.5, 0.5, 0.5);
            p.mulPose(Axis.YP.rotationDegrees((t * 1.5F) % 360.0F));
            p.translate(-0.5, -0.5, -0.5);
        });

        // 3) 能量环：反向慢转（360° / 24 秒）+ 上下浮动
        renderBakedModel(pose, buffer, ring, p -> {
            p.translate(0.5, 0.5 + (float) Math.sin(t * 0.1) * 0.0625F, 0.5);
            p.mulPose(Axis.YP.rotationDegrees((-t * 0.75F) % 360.0F));
            p.translate(-0.5, -0.5, -0.5);
        });

        // 4) 核心光球：上下浮动 + 呼吸式脉冲缩放
        renderBakedModel(pose, buffer, orb, p -> {
            p.translate(0.5, 0.5 + (float) Math.sin(t * 0.15) * 0.125F, 0.5);
            float scale = 1.0F + (float) Math.sin(t * 0.2) * 0.08F;
            p.scale(scale, scale, scale);
            p.translate(-0.5, -0.5, -0.5);
        });
    }

    /**
     * 以全亮光照渲染一个烘焙模型，transform 用于施加动画变换。
     * 注意：必须同时渲染 culled 面（有 cullface）与 unculled 面（无 cullface，
     * 通过 direction == null 查询）——本模型的面均无 cullface。
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
