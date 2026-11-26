package com.omni.salvage;

import com.omni.registry.OmniComponents;
import net.minecraft.world.item.ItemStack;

/** Pièces récupérées « endommagées » : à recalibrer au banc de diagnostic. */
public final class Damage {
    private Damage() {}

    public static boolean isDamaged(ItemStack stack) {
        return stack.has(OmniComponents.DAMAGED.get());
    }

    /** Une arme endommagée s'enraye 30 % du temps (tirage déterministe : client et serveur sont d'accord). */
    public static final float MISFIRE_CHANCE = 0.3F;

    public static boolean isMisfire(int seed) {
        // Graine mélangée (SplitMix64) : des graines proches donnent des tirages indépendants.
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 40) / (float) (1L << 24) < MISFIRE_CHANCE;
    }

    public static void repair(ItemStack stack) {
        stack.remove(OmniComponents.DAMAGED.get());
    }
}
