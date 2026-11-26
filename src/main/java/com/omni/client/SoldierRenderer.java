package com.omni.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.omni.Omni;
import com.omni.entity.SoldierEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Modèle humanoïde (maillage joueur) ; arme épaulée au combat, portée canon vers le sol au repos. */
public class SoldierRenderer extends HumanoidMobRenderer<SoldierEntity, HumanoidModel<SoldierEntity>> {
    private static final ResourceLocation TEXTURE = Omni.id("textures/entity/soldier.png");

    public SoldierRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public void render(SoldierEntity soldier, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        HumanoidModel<SoldierEntity> model = getModel();
        model.rightArmPose = soldier.isAggressive() ? HumanoidModel.ArmPose.CROSSBOW_HOLD : HumanoidModel.ArmPose.ITEM;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        super.render(soldier, entityYaw, partialTick, pose, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SoldierEntity soldier) {
        return TEXTURE;
    }
}
