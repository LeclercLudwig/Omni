package com.omni;

import com.mojang.logging.LogUtils;
import com.omni.entity.SoldierEntity;
import com.omni.network.OmniNetwork;
import com.omni.registry.OmniComponents;
import com.omni.registry.OmniEntities;
import com.omni.registry.OmniItems;
import com.omni.registry.OmniSounds;
import com.omni.registry.OmniTabs;
import com.omni.weapon.WeaponManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import org.slf4j.Logger;

/** Point d'entrée commun (client + serveur). */
@Mod(Omni.MODID)
public class Omni {
    public static final String MODID = "omni";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public Omni(IEventBus modBus) {
        OmniComponents.COMPONENTS.register(modBus);
        OmniItems.ITEMS.register(modBus);
        OmniEntities.ENTITIES.register(modBus);
        OmniTabs.TABS.register(modBus);
        OmniSounds.SOUNDS.register(modBus);

        modBus.addListener(OmniNetwork::register);
        modBus.addListener(Omni::registerAttributes);
        modBus.addListener(Omni::registerSpawnPlacements);

        // Les armes sont lues depuis data/<namespace>/omni_weapons/*.json (rechargeables avec /reload).
        NeoForge.EVENT_BUS.addListener(WeaponManager::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(OmniNetwork::onDatapackSync);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(OmniEntities.SOLDIER.get(), SoldierEntity.createAttributes().build());
    }

    /** Apparition naturelle : au sol, dans l'obscurité, comme les monstres vanilla. */
    private static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(OmniEntities.SOLDIER.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
