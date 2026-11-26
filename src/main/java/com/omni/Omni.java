package com.omni;

import com.mojang.logging.LogUtils;
import com.omni.network.OmniNetwork;
import com.omni.registry.OmniComponents;
import com.omni.registry.OmniEntities;
import com.omni.registry.OmniItems;
import com.omni.registry.OmniTabs;
import com.omni.weapon.WeaponManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
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

        modBus.addListener(OmniNetwork::register);

        // Les armes sont lues depuis data/<namespace>/omni_weapons/*.json (rechargeables avec /reload).
        NeoForge.EVENT_BUS.addListener(WeaponManager::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(OmniNetwork::onDatapackSync);
    }
}
