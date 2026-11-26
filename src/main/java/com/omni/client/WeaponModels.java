package com.omni.client;

import com.omni.registry.OmniComponents;
import com.omni.registry.OmniItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Un modèle 3D par arme pour l'item unique {@code omni:blaster}.
 * <p>
 * Convention : l'arme {@code <ns>:<nom>} utilise le modèle
 * {@code assets/<ns>/models/item/weapons/<nom>.json} s'il existe (y compris dans un pack de
 * ressources), sinon le modèle par défaut {@code omni:item/blaster}.
 */
public final class WeaponModels {
    private static final String FOLDER = "item/weapons/";

    private WeaponModels() {}

    /** Charge tous les modèles présents dans models/item/weapons/ (ils ne sont référencés par aucun item). */
    static void registerAdditional(ModelEvent.RegisterAdditional event) {
        Minecraft.getInstance().getResourceManager()
                .listResources("models/" + FOLDER.substring(0, FOLDER.length() - 1), file -> file.getPath().endsWith(".json"))
                .keySet()
                .forEach(file -> {
                    String path = file.getPath().substring("models/".length(), file.getPath().length() - ".json".length());
                    event.register(ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path)));
                });
    }

    /** Remplace le modèle du blaster par une version qui choisit le modèle de l'arme à l'affichage. */
    static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        ModelResourceLocation key = ModelResourceLocation.inventory(OmniItems.BLASTER.getId());
        BakedModel fallback = models.get(key);
        if (fallback == null) return;
        ItemOverrides overrides = new PerWeaponOverrides(models);
        models.put(key, new BakedModelWrapper<>(fallback) {
            @Override
            public ItemOverrides getOverrides() {
                return overrides;
            }
        });
    }

    private static final class PerWeaponOverrides extends ItemOverrides {
        private final Map<ModelResourceLocation, BakedModel> models;

        PerWeaponOverrides(Map<ModelResourceLocation, BakedModel> models) {
            this.models = models;
        }

        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                  @Nullable LivingEntity entity, int seed) {
            ResourceLocation weapon = stack.get(OmniComponents.WEAPON.get());
            if (weapon == null) return model;
            BakedModel specific = models.get(ModelResourceLocation.standalone(weapon.withPrefix(FOLDER)));
            return specific != null ? specific : model;
        }
    }
}
