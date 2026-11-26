package com.omni.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.omni.entity.BoltEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Trait lumineux orienté selon la vitesse : un halo coloré + un cœur blanc,
 * en rendu additif (RenderType.lightning) donc toujours « pleine lumière ».
 */
public class BoltRenderer extends EntityRenderer<BoltEntity> {
    private static final float LENGTH = 1.4F;
    private static final float GLOW_HALF_WIDTH = 0.06F;
    private static final float CORE_HALF_WIDTH = 0.022F;
    private static final ResourceLocation UNUSED_TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public BoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /**
     * Le joueur local voit ses propres tirs via leurs copies prédites (voir ClientPrediction) :
     * on masque les vrais, qui arrivent du serveur avec un temps de retard. Un tir dévié par une
     * parade redevient visible : il revient vers le tireur.
     */
    @Override
    public boolean shouldRender(BoltEntity bolt, Frustum frustum, double camX, double camY, double camZ) {
        if (!bolt.isPredicted() && !bolt.isDeflected()
                && bolt.getOwner() != null && bolt.getOwner() == Minecraft.getInstance().player) {
            return false;
        }
        return super.shouldRender(bolt, frustum, camX, camY, camZ);
    }

    @Override
    public void render(BoltEntity bolt, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        Vec3 velocity = bolt.visualDirection(partialTick);
        if (velocity.lengthSqr() < 1.0E-6) return;

        pose.pushPose();
        // Départ visuel au canon de l'arme, qui rejoint la vraie trajectoire en quelques ticks.
        Vec3 offset = bolt.visualOffset(partialTick);
        pose.translate(offset.x, offset.y, offset.z);
        float yaw = (float) Math.atan2(velocity.x, velocity.z);
        float pitch = (float) Math.atan2(velocity.y, velocity.horizontalDistance());
        pose.mulPose(Axis.YP.rotation(yaw));
        pose.mulPose(Axis.XP.rotation(-pitch));

        // Le trait part de la tête du tir (z = 0) vers l'arrière (z négatif).
        float length = Math.min(LENGTH, (float) bolt.getDeltaMovement().length() * Math.min(bolt.tickCount + partialTick, 1.0F) + 0.2F);
        int color = bolt.getColor();
        int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;

        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();
        float scale = bolt.getScale();
        box(consumer, matrix, GLOW_HALF_WIDTH * scale, length, r, g, b, 120);
        box(consumer, matrix, CORE_HALF_WIDTH * scale, length * 0.95F, 255, 255, 255, 220);

        pose.popPose();
        super.render(bolt, entityYaw, partialTick, pose, buffers, packedLight);
    }

    /** Parallélépipède de section 2w×2w, de z = -length à z = 0 (faces dans le sens anti-horaire). */
    private static void box(VertexConsumer c, Matrix4f m, float w, float length, int r, int g, int b, int a) {
        float n = -w, p = w, z0 = -length, z1 = 0;
        quad(c, m, r, g, b, a, p, n, z1, p, n, z0, p, p, z0, p, p, z1); // +X
        quad(c, m, r, g, b, a, n, n, z0, n, n, z1, n, p, z1, n, p, z0); // -X
        quad(c, m, r, g, b, a, n, p, z1, p, p, z1, p, p, z0, n, p, z0); // +Y
        quad(c, m, r, g, b, a, n, n, z0, p, n, z0, p, n, z1, n, n, z1); // -Y
        quad(c, m, r, g, b, a, n, n, z1, p, n, z1, p, p, z1, n, p, z1); // +Z (tête)
        quad(c, m, r, g, b, a, n, p, z0, p, p, z0, p, n, z0, n, n, z0); // -Z (queue)
    }

    private static void quad(VertexConsumer c, Matrix4f m, int r, int g, int b, int a,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        c.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        c.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        c.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
        c.addVertex(m, x4, y4, z4).setColor(r, g, b, a);
    }

    @Override
    public ResourceLocation getTextureLocation(BoltEntity bolt) {
        return UNUSED_TEXTURE; // le rendu n'utilise pas de texture
    }
}
