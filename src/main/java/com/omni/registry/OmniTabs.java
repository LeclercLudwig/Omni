package com.omni.registry;

import com.omni.Omni;
import com.omni.weapon.BlasterItem;
import com.omni.weapon.WeaponManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class OmniTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Omni.MODID);

    /** Un blaster par définition chargée (liste synchronisée depuis le serveur). */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.omni"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> BlasterItem.create(Omni.id("blaster_rifle")))
                    .displayItems((params, output) -> {
                        WeaponManager.all(true).keySet().forEach(id -> output.accept(BlasterItem.create(id)));
                        output.accept(OmniItems.ENERGY_BLADE.get());
                        output.accept(OmniItems.SOLDIER_SPAWN_EGG.get());
                    })
                    .build());

    private OmniTabs() {}
}
