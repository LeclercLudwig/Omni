package com.omni.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.omni.Omni;
import com.omni.entity.SoldierEntity;
import com.omni.registry.OmniComponents;
import com.omni.registry.OmniEntities;
import com.omni.registry.OmniItems;
import com.omni.weapon.BlasterItem;
import com.omni.weapon.BlasterLogic;
import com.omni.weapon.MagazineState;
import com.omni.weapon.WeaponDefinition;
import com.omni.weapon.WeaponManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Chargeurs, rechargement et munitions (même arène que les autres tests). */
@GameTestHolder(Omni.MODID)
@PrefixGameTestTemplate(false)
public class AmmoGameTests {
    private static final String TEMPLATE = "empty";

    @SuppressWarnings("removal") // seule API de joueur factice disponible en 1.21.1
    private static ServerPlayer shooter(GameTestHelper helper, String weapon, boolean survival) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        if (survival) player.setGameMode(GameType.SURVIVAL);
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 2, 3.5));
        player.moveTo(pos.x, pos.y, pos.z, -90, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, BlasterItem.create(Omni.id(weapon)));
        return player;
    }

    private static WeaponDefinition def(String weapon) {
        return WeaponManager.get(false, Omni.id(weapon)).orElseThrow();
    }

    private static int rounds(GameTestHelper helper, ServerPlayer player, String weapon) {
        return BlasterItem.magazine(player.getMainHandItem()).roundsAt(def(weapon), helper.getLevel().getGameTime());
    }

    @GameTest(template = TEMPLATE)
    public static void newWeaponHasFullMagazine(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "blaster_pistol", false);
        helper.assertTrue(rounds(helper, player, "blaster_pistol") == 15, "un pistolet neuf doit avoir 15 tirs");
        BlasterLogic.tryFire(player, false, 1);
        helper.assertTrue(rounds(helper, player, "blaster_pistol") == 14, "un tir doit consommer 1 munition");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void emptyMagazineBlocksFiring(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "blaster_pistol", false);
        player.getMainHandItem().set(OmniComponents.MAGAZINE.get(), new MagazineState(0, -1));
        BlasterLogic.tryFire(player, false, 1);
        helper.assertTrue(BlasterItem.state(player.getMainHandItem()).lastShot() < 0, "chargeur vide : aucun tir");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void reloadConsumesCartridgeAndRefills(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "blaster_pistol", true); // rechargement : 30 ticks
        player.getMainHandItem().set(OmniComponents.MAGAZINE.get(), new MagazineState(0, -1));
        player.getInventory().add(new ItemStack(OmniItems.GAS_CARTRIDGE.get(), 2));

        BlasterLogic.tryReload(player);
        WeaponDefinition def = def("blaster_pistol");
        helper.assertTrue(BlasterItem.magazine(player.getMainHandItem()).isReloading(def, helper.getLevel().getGameTime()),
                "le rechargement doit avoir commencé");
        helper.assertTrue(BlasterLogic.countAmmo(player, def.magazine().get()) == 1, "une cartouche doit être consommée");
        BlasterLogic.tryFire(player, false, 1);
        helper.assertTrue(BlasterItem.state(player.getMainHandItem()).lastShot() < 0, "pas de tir pendant le rechargement");

        helper.runAfterDelay(31, () -> {
            helper.assertTrue(rounds(helper, player, "blaster_pistol") == 15, "chargeur plein après rechargement");
            BlasterLogic.tryFire(player, false, 1);
            helper.assertTrue(BlasterItem.state(player.getMainHandItem()).lastShot() >= 0, "le tir doit repartir");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void reloadWithoutAmmoFails(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "blaster_pistol", true);
        player.getMainHandItem().set(OmniComponents.MAGAZINE.get(), new MagazineState(0, -1));
        BlasterLogic.tryReload(player);
        helper.assertFalse(BlasterItem.magazine(player.getMainHandItem()).isReloading(def("blaster_pistol"), helper.getLevel().getGameTime()),
                "sans cartouche, pas de rechargement");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fullMagazineDoesNotReload(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "blaster_pistol", true);
        player.getInventory().add(new ItemStack(OmniItems.GAS_CARTRIDGE.get(), 1));
        BlasterLogic.tryReload(player);
        helper.assertTrue(BlasterLogic.countAmmo(player, def("blaster_pistol").magazine().get()) == 1,
                "recharger un chargeur plein ne doit rien consommer");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void creativeReloadsForFree(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "blaster_pistol", false); // joueur factice : créatif
        player.getMainHandItem().set(OmniComponents.MAGAZINE.get(), new MagazineState(0, -1));
        BlasterLogic.tryReload(player);
        helper.assertTrue(BlasterItem.magazine(player.getMainHandItem()).isReloading(def("blaster_pistol"), helper.getLevel().getGameTime()),
                "en créatif, le rechargement ne demande pas de cartouche");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260)
    public static void soldierReloadsWhenEmpty(GameTestHelper helper) {
        SoldierEntity soldier = helper.spawn(OmniEntities.SOLDIER.get(), new Vec3(1.5, 2, 3.5));
        soldier.setItemSlot(EquipmentSlot.MAINHAND, BlasterItem.create(Omni.id("blaster_pistol")));
        soldier.weapon().set(OmniComponents.MAGAZINE.get(), new MagazineState(0, -1));
        Mob golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new Vec3(7.5, 2, 3.5));
        soldier.setTarget(golem);
        helper.succeedWhen(() -> helper.assertTrue(golem.getHealth() < golem.getMaxHealth(),
                "le soldat doit recharger puis toucher le golem"));
    }

    @GameTest(template = TEMPLATE)
    public static void weaponWithoutMagazineIsUnlimited(GameTestHelper helper) {
        WeaponDefinition def = WeaponDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"damage\": 3}"))
                .getOrThrow();
        helper.assertTrue(def.magazine().isEmpty(), "pas de bloc magazine → pas de chargeur");
        helper.assertTrue(MagazineState.FULL.roundsAt(def, 0) == Integer.MAX_VALUE, "munitions illimitées");
        helper.succeed();
    }
}
