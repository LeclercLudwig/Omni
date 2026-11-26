package com.omni.gametest;

import com.omni.Omni;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import com.omni.weapon.GuardState;
import com.omni.weapon.EnergyBladeItem;
import com.omni.registry.OmniComponents;
import com.omni.entity.SoldierEntity;
import com.omni.registry.OmniEntities;
import com.omni.registry.OmniItems;
import com.omni.weapon.BlasterItem;
import com.omni.weapon.BlasterLogic;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Parade des tirs à la lame à énergie. Le joueur factice ne « tick » pas : sa garde reste
 * donc toujours dans la fenêtre de parade parfaite, et son regard ne bouge pas.
 */
@GameTestHolder(Omni.MODID)
@PrefixGameTestTemplate(false)
public class DeflectionGameTests {
    private static final String TEMPLATE = "empty";
    private static final float FACING_SOLDIER = -90; // regarde vers +x
    private static final float FACING_AWAY = 90;
    /** Un joueur qui vient d'apparaître est invulnérable 60 ticks (vanilla) : on attend la fin. */
    private static final int SPAWN_PROTECTION_TICKS = 61;

    @SuppressWarnings("removal") // seule API de joueur factice disponible en 1.21.1
    private static ServerPlayer defender(GameTestHelper helper, float yaw, boolean guarding) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL); // le joueur factice est en créatif (invulnérable) par défaut
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 2, 3.5));
        player.moveTo(pos.x, pos.y, pos.z, yaw, 0);
        player.setYHeadRot(yaw);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(OmniItems.ENERGY_BLADE.get()));
        if (guarding) player.startUsingItem(InteractionHand.MAIN_HAND);
        return player;
    }

    private static SoldierEntity shooter(GameTestHelper helper, boolean withAi) {
        Vec3 at = new Vec3(7.5, 2, 3.5);
        SoldierEntity soldier = withAi
                ? helper.spawn(OmniEntities.SOLDIER.get(), at)
                : helper.spawnWithNoFreeWill(OmniEntities.SOLDIER.get(), at);
        soldier.setItemSlot(EquipmentSlot.MAINHAND, BlasterItem.create(Omni.id("blaster_rifle")));
        return soldier;
    }

    /** Tir précis du soldat vers le torse du défenseur. */
    private static void fireAt(SoldierEntity soldier, ServerPlayer target) {
        Vec3 from = new Vec3(soldier.getX(), soldier.getEyeY() - 0.1, soldier.getZ());
        Vec3 to = target.position().add(0, 1.2, 0);
        BlasterLogic.fire(soldier, soldier.weapon(), to.subtract(from), 0, 7, 0, null);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void perfectParrySendsBoltWhereDefenderAims(GameTestHelper helper) {
        ServerPlayer defender = defender(helper, FACING_SOLDIER, true);
        SoldierEntity soldier = shooter(helper, false);
        helper.runAfterDelay(SPAWN_PROTECTION_TICKS, () -> fireAt(soldier, defender));
        helper.succeedWhen(() -> {
            helper.assertTrue(soldier.getHealth() < soldier.getMaxHealth(), "le tir renvoyé aurait dû toucher le soldat");
            helper.assertTrue(defender.getHealth() == defender.getMaxHealth(), "le défenseur ne doit pas être touché");
            helper.assertTrue(defender.getMainHandItem().getDamageValue() == 1, "la parade doit user la lame de 1");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void guardDoesNotCoverTheBack(GameTestHelper helper) {
        ServerPlayer defender = defender(helper, FACING_AWAY, true);
        SoldierEntity soldier = shooter(helper, false);
        helper.runAfterDelay(SPAWN_PROTECTION_TICKS, () -> fireAt(soldier, defender));
        helper.succeedWhen(() -> {
            helper.assertTrue(defender.getHealth() < defender.getMaxHealth(), "un tir dans le dos doit toucher");
            helper.assertTrue(soldier.getHealth() == soldier.getMaxHealth(), "rien ne doit être renvoyé");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void noGuardNoDeflection(GameTestHelper helper) {
        ServerPlayer defender = defender(helper, FACING_SOLDIER, false);
        SoldierEntity soldier = shooter(helper, false);
        helper.runAfterDelay(SPAWN_PROTECTION_TICKS, () -> fireAt(soldier, defender));
        helper.succeedWhen(() -> helper.assertTrue(defender.getHealth() < defender.getMaxHealth(),
                "sans garde, le tir doit toucher"));
    }

    /**
     * Le soldat retient d'abord son tir face à la garde, puis tire quand même pour l'épuiser.
     * Le joueur factice ne « tick » pas : chaque parade est parfaite et renvoie le tir sur le soldat.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void soldierHoldsThenPressuresGuard(GameTestHelper helper) {
        ServerPlayer defender = defender(helper, FACING_SOLDIER, true);
        SoldierEntity soldier = shooter(helper, true);
        // Immobile : sinon il peut contourner la garde (comportement voulu) et tirer sans pression.
        soldier.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        soldier.setTarget(defender);

        helper.runAfterDelay(25, () -> { // réaction (10 ticks) + début de la retenue (30 ticks)
            helper.assertTrue(BlasterItem.state(soldier.weapon()).lastShot() < 0,
                    "le soldat doit d'abord retenir son tir face à la garde");
            helper.succeedWhen(() -> {
                helper.assertTrue(BlasterItem.state(soldier.weapon()).lastShot() >= 0, "le soldat doit finir par tirer");
                helper.assertTrue(soldier.getHealth() < soldier.getMaxHealth(), "ses tirs doivent lui être renvoyés");
                helper.assertTrue(defender.getHealth() == defender.getMaxHealth(), "le défenseur doit tout parer");
            });
        });
    }

    @GameTest(template = TEMPLATE)
    public static void guardBreaksWhenStaminaRunsOut(GameTestHelper helper) {
        ServerPlayer defender = defender(helper, FACING_SOLDIER, true);
        ItemStack blade = defender.getMainHandItem();
        long now = helper.getLevel().getGameTime();
        blade.set(OmniComponents.GUARD.get(), new GuardState(5, now, true, -1)); // 5 / 1,5 par tick → vide en 4 ticks

        helper.runAfterDelay(5, () -> {
            EnergyBladeItem.tickGuard(defender, blade); // appelé à chaque tick de garde en jeu
            long later = helper.getLevel().getGameTime();
            helper.assertFalse(defender.isUsingItem(), "la garde doit être brisée");
            helper.assertTrue(defender.getCooldowns().isOnCooldown(blade.getItem()), "la lame doit être en recharge");
            helper.assertTrue(EnergyBladeItem.guard(blade).isBrokenAt(later), "l'état doit indiquer une garde brisée");
            helper.assertFalse(EnergyBladeItem.guard(blade).canRaise(later), "impossible de relever la garde tout de suite");
            helper.succeed();
        });
    }

    /** Garde levée depuis plus de 0,3 s : parade normale, qui coûte de l'endurance. */
    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void normalParryCostsStamina(GameTestHelper helper) {
        Mob defender = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(1.5, 2, 3.5));
        defender.setYRot(FACING_SOLDIER);
        defender.setYHeadRot(FACING_SOLDIER);
        defender.setYBodyRot(FACING_SOLDIER);
        defender.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(OmniItems.ENERGY_BLADE.get()));
        defender.startUsingItem(InteractionHand.MAIN_HAND); // un mob « tick » : la fenêtre parfaite s'écoule
        SoldierEntity soldier = shooter(helper, false);

        helper.runAfterDelay(20, () -> {
            Vec3 from = new Vec3(soldier.getX(), soldier.getEyeY() - 0.1, soldier.getZ());
            BlasterLogic.fire(soldier, soldier.weapon(), defender.position().add(0, 1.2, 0).subtract(from), 0, 7, 0, null);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(EnergyBladeItem.guard(defender.getMainHandItem()).staminaAt(helper.getLevel().getGameTime()) < GuardState.MAX,
                    "une parade normale doit coûter de l'endurance");
            helper.assertTrue(defender.getHealth() == defender.getMaxHealth(), "le tir doit être paré");
        });
    }

    /** Le cône de garde fait 55° de chaque côté : un tir à 70° de l'axe du regard passe. */
    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void guardDoesNotCoverWideAngles(GameTestHelper helper) {
        ServerPlayer defender = defender(helper, FACING_SOLDIER + 70, true);
        SoldierEntity soldier = shooter(helper, false);
        helper.runAfterDelay(SPAWN_PROTECTION_TICKS, () -> fireAt(soldier, defender));
        helper.succeedWhen(() -> helper.assertTrue(defender.getHealth() < defender.getMaxHealth(),
                "un tir à 70° doit passer la garde"));
    }
}
