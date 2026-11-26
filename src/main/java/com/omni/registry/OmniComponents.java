package com.omni.registry;

import com.omni.Omni;
import com.omni.weapon.BlasterState;
import com.omni.weapon.GuardState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Données stockées sur l'ItemStack du blaster. */
public final class OmniComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Omni.MODID);

    /** Quelle définition d'arme (JSON) ce blaster utilise. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> WEAPON =
            COMPONENTS.register("weapon", () -> DataComponentType.<ResourceLocation>builder()
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC)
                    .build());

    /** Chaleur, surchauffe et horodatage du dernier tir. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlasterState>> STATE =
            COMPONENTS.register("state", () -> DataComponentType.<BlasterState>builder()
                    .persistent(BlasterState.CODEC)
                    .networkSynchronized(BlasterState.STREAM_CODEC)
                    .build());

    /** Endurance de garde de la lame à énergie. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GuardState>> GUARD =
            COMPONENTS.register("guard", () -> DataComponentType.<GuardState>builder()
                    .persistent(GuardState.CODEC)
                    .networkSynchronized(GuardState.STREAM_CODEC)
                    .build());

    private OmniComponents() {}
}
