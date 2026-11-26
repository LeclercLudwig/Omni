package com.omni.planet;

import com.omni.Omni;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** La planète de départ : désert de rouille jonché d'épaves (dimension omni:khessar, définie en JSON). */
public final class Khessar {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, Omni.id("khessar"));

    private Khessar() {}
}
