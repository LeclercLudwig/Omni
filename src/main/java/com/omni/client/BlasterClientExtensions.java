package com.omni.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.omni.weapon.BlasterItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * Position de l'arme en première personne : on remplace la transformation vanilla
 * (bras + balancement d'attaque) pour ramener l'arme au centre quand on vise.
 */
public class BlasterClientExtensions implements IClientItemExtensions {
    /** Vue à la 3e personne : arme épaulée à deux mains (les modèles sont conçus pour cette pose). */
    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return HumanoidModel.ArmPose.CROSSBOW_HOLD;
    }

    private static float reloadDip(LocalPlayer player, ItemStack stack, float partialTick) {
        return BlasterItem.definition(true, stack).map(def -> {
            float progress = BlasterItem.magazine(stack).reloadProgress(def, player.level().getGameTime(), partialTick);
            return progress >= 1 ? 0F : Mth.sin(progress * Mth.PI);
        }).orElse(0F);
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                           float partialTick, float equipProcess, float swingProcess) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float aim = ClientWeaponControls.aimProgress(partialTick);
        // Valeurs vanilla (ItemInHandRenderer#applyItemArmTransform) interpolées vers le centre.
        pose.translate(side * 0.56F * (1 - aim), -0.52F + equipProcess * -0.6F + aim * 0.12F, -0.72F + aim * 0.18F);

        // Rechargement : l'arme s'abaisse et pivote, puis remonte.
        float dip = reloadDip(player, stack, partialTick);
        if (dip > 0) {
            pose.translate(0, -0.25F * dip, 0.05F * dip);
            pose.mulPose(Axis.XP.rotationDegrees(-35 * dip));
            pose.mulPose(Axis.ZP.rotationDegrees(side * 15 * dip));
        }
        return true; // pas d'animation de coup d'épée
    }
}
