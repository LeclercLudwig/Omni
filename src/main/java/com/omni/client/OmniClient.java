package com.omni.client;

import com.omni.Omni;
import com.omni.registry.OmniEntities;
import com.omni.registry.OmniItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** Point d'entrée client uniquement (jamais chargé sur un serveur dédié). */
@Mod(value = Omni.MODID, dist = Dist.CLIENT)
public class OmniClient {
    public OmniClient(IEventBus modBus) {
        modBus.addListener(OmniClient::registerRenderers);
        modBus.addListener(OmniClient::registerGuiLayers);
        modBus.addListener(OmniClient::registerClientExtensions);
        modBus.addListener(OmniKeys::register);
        modBus.addListener(WeaponModels::registerAdditional);
        modBus.addListener(WeaponModels::modifyBakingResult);

        NeoForge.EVENT_BUS.addListener(ClientWeaponControls::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(ClientWeaponControls::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientWeaponControls::onComputeFov);
        NeoForge.EVENT_BUS.addListener(ClientWeaponControls::onPlayerTurn);
        NeoForge.EVENT_BUS.addListener(RecoilController::onRenderFrame);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(OmniEntities.BOLT.get(), BoltRenderer::new);
        event.registerEntityRenderer(OmniEntities.SOLDIER.get(), SoldierRenderer::new);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, Omni.id("heat"), HeatHud::render);
    }

    private static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new BlasterClientExtensions(), OmniItems.BLASTER.get());
    }
}
