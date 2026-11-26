package com.omni.registry;

import com.omni.Omni;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;

public final class OmniTags {
    /** Entités sensibles aux tirs ioniques (futurs droïdes, golems…). */
    public static final TagKey<EntityType<?>> ION_VULNERABLE =
            TagKey.create(Registries.ENTITY_TYPE, Omni.id("ion_vulnerable"));

    /** Type de dégâts défini dans data/omni/damage_type/blaster.json. */
    public static final ResourceKey<DamageType> BLASTER_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Omni.id("blaster"));

    private OmniTags() {}
}
