package com.omni.registry;

import com.omni.Omni;
import com.omni.entity.BoltEntity;
import com.omni.entity.SoldierEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class OmniEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Omni.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<BoltEntity>> BOLT =
            ENTITIES.register("bolt", () -> EntityType.Builder.<BoltEntity>of(BoltEntity::new, MobCategory.MISC)
                    .sized(0.2F, 0.2F)
                    .clientTrackingRange(8)
                    .updateInterval(1) // position envoyée chaque tick : indispensable pour un projectile rapide
                    .build("bolt"));

    public static final DeferredHolder<EntityType<?>, EntityType<SoldierEntity>> SOLDIER =
            ENTITIES.register("soldier", () -> EntityType.Builder.<SoldierEntity>of(SoldierEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(8)
                    .build("soldier"));

    private OmniEntities() {}
}
