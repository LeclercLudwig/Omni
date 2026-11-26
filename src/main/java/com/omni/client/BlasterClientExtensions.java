package com.omni.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * Position de l'arme en première personne : on remplace la transformation vanilla
 * (bras + balancement d'attaque) pour ramener l'arme au centre quand on vise.
 */
public class BlasterClientExtensions implements IClientItemExtensions {
    @Override
    public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                           float partialTick, float equipProcess, float swingProcess) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float aim = ClientWeaponControls.aimProgress(partialTick);
        // Valeurs vanilla (ItemInHandRenderer#applyItemArmTransform) interpolées vers le centre.
        pose.translate(side * 0.56F * (1 - aim), -0.52F + equipProcess * -0.6F + aim * 0.12F, -0.72F + aim * 0.18F);
        return true; // pas d'animation de coup d'épée
    }
}
