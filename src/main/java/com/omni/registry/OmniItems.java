package com.omni.registry;

import com.omni.Omni;
import com.omni.weapon.BlasterItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class OmniItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Omni.MODID);

    /**
     * Un seul item pour tous les blasters : le composant {@link OmniComponents#WEAPON}
     * indique quelle définition JSON utiliser. Ajouter une arme = ajouter un fichier JSON.
     */
    public static final DeferredItem<BlasterItem> BLASTER =
            ITEMS.register("blaster", () -> new BlasterItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<DeferredSpawnEggItem> SOLDIER_SPAWN_EGG = ITEMS.register("soldier_spawn_egg",
            () -> new DeferredSpawnEggItem(OmniEntities.SOLDIER, 0x5B6148, 0xD0532E, new Item.Properties()));

    private OmniItems() {}
}
