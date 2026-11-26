package com.omni.registry;

import com.omni.Omni;
import com.omni.weapon.AttachmentItem;
import com.omni.weapon.AttachmentType;
import com.omni.weapon.BlasterItem;
import com.omni.salvage.PlasmaCutterItem;
import com.omni.weapon.EnergyBladeItem;
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

    /** Munition par défaut des blasters (voir "magazine" dans les JSON d'armes). */
    public static final DeferredItem<Item> GAS_CARTRIDGE = ITEMS.registerSimpleItem("gas_cartridge", new Item.Properties().stacksTo(16));

    public static final DeferredItem<AttachmentItem> SCOPE = attachment("scope", AttachmentType.SCOPE);
    public static final DeferredItem<AttachmentItem> STABILIZER = attachment("stabilizer", AttachmentType.STABILIZER);
    public static final DeferredItem<AttachmentItem> HEAT_SINK = attachment("heat_sink", AttachmentType.HEAT_SINK);
    public static final DeferredItem<AttachmentItem> STOCK = attachment("stock", AttachmentType.STOCK);

    private static DeferredItem<AttachmentItem> attachment(String name, AttachmentType type) {
        return ITEMS.register(name, () -> new AttachmentItem(type, new Item.Properties()));
    }

    public static final DeferredItem<EnergyBladeItem> ENERGY_BLADE =
            ITEMS.register("energy_blade", () -> new EnergyBladeItem(new Item.Properties()));

    // --- Récupération (épaves) ---
    public static final DeferredItem<PlasmaCutterItem> PLASMA_CUTTER = ITEMS.register("plasma_cutter",
            () -> new PlasmaCutterItem(new Item.Properties()));
    public static final DeferredItem<Item> SCRAP_METAL = ITEMS.registerSimpleItem("scrap_metal");
    public static final DeferredItem<Item> FUEL_CANISTER = ITEMS.registerSimpleItem("fuel_canister", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> POWER_CELL = ITEMS.registerSimpleItem("power_cell", new Item.Properties().stacksTo(16));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> WRECK_HULL = ITEMS.registerSimpleBlockItem(OmniBlocks.WRECK_HULL);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SCORCHED_HULL = ITEMS.registerSimpleBlockItem(OmniBlocks.SCORCHED_HULL);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> FUEL_LINE = ITEMS.registerSimpleBlockItem(OmniBlocks.FUEL_LINE);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> REACTOR_CORE = ITEMS.registerSimpleBlockItem(OmniBlocks.REACTOR_CORE);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> STABLE_REACTOR_CORE = ITEMS.registerSimpleBlockItem(OmniBlocks.STABLE_REACTOR_CORE);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SALVAGE_CRATE = ITEMS.registerSimpleBlockItem(OmniBlocks.SALVAGE_CRATE);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> DIAGNOSTIC_BENCH = ITEMS.registerSimpleBlockItem(OmniBlocks.DIAGNOSTIC_BENCH);

    public static final DeferredItem<DeferredSpawnEggItem> SOLDIER_SPAWN_EGG = ITEMS.register("soldier_spawn_egg",
            () -> new DeferredSpawnEggItem(OmniEntities.SOLDIER, 0x5B6148, 0xD0532E, new Item.Properties()));

    private OmniItems() {}
}
