package com.omni.gametest;

import com.omni.Omni;
import com.omni.entity.SoldierEntity;
import com.omni.registry.OmniComponents;
import com.omni.registry.OmniEntities;
import com.omni.weapon.BlasterItem;
import com.omni.weapon.BlasterState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Comportement des soldats (même arène que {@link BlasterGameTests}).
 * Les cibles sont des golems sans IA : ils encaissent sans riposter.
 */
@GameTestHolder(Omni.MODID)
@PrefixGameTestTemplate(false)
public class SoldierGameTests {
    private static final String TEMPLATE = "empty";

    private static SoldierEntity soldier(GameTestHelper helper, double x, String weapon) {
        SoldierEntity soldier = helper.spawn(OmniEntities.SOLDIER.get(), new Vec3(x, 2, 3.5));
        soldier.setItemSlot(EquipmentSlot.MAINHAND, BlasterItem.create(Omni.id(weapon)));
        return soldier;
    }

    private static Mob dummy(GameTestHelper helper) {
        return helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new Vec3(7.5, 2, 3.5));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void soldierShootsItsTarget(GameTestHelper helper) {
        SoldierEntity soldier = soldier(helper, 1.5, "blaster_rifle");
        Mob golem = dummy(helper);
        soldier.setTarget(golem);
        helper.succeedWhen(() -> helper.assertTrue(golem.getHealth() < golem.getMaxHealth(),
                "le golem aurait dû être touché"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void overheatedSoldierTakesCoverAndHoldsFire(GameTestHelper helper) {
        SoldierEntity soldier = soldier(helper, 1.5, "blaster_rifle");
        long now = helper.getLevel().getGameTime();
        soldier.weapon().set(OmniComponents.STATE.get(), new BlasterState(100, now, true, now, now + 3));
        Mob golem = dummy(helper);
        soldier.setTarget(golem);
        helper.succeedWhen(() -> {
            helper.assertTrue(soldier.isTakingCover(), "le soldat en surchauffe devrait se mettre à couvert");
            helper.assertTrue(golem.getHealth() == golem.getMaxHealth(), "il ne devrait pas tirer en surchauffe");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void soldiersDoNotShootAllies(GameTestHelper helper) {
        SoldierEntity shooter = soldier(helper, 1.5, "blaster_rifle");
        // Allié immobile placé dans la ligne de tir.
        SoldierEntity ally = helper.spawnWithNoFreeWill(OmniEntities.SOLDIER.get(), new Vec3(4.5, 2, 3.5));
        Mob golem = dummy(helper);
        shooter.setTarget(golem);
        helper.succeedWhen(() -> {
            helper.assertTrue(golem.getHealth() < golem.getMaxHealth(), "le golem aurait dû être touché à travers l'allié");
            helper.assertTrue(ally.getHealth() == ally.getMaxHealth(), "l'allié ne doit pas être touché");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void spottingSoldierAlertsSquad(GameTestHelper helper) {
        SoldierEntity spotter = soldier(helper, 1.5, "blaster_pistol");
        SoldierEntity squadmate = soldier(helper, 2.5, "blaster_pistol");
        Mob golem = dummy(helper);
        spotter.setTarget(golem);
        helper.succeedWhen(() -> helper.assertTrue(squadmate.getTarget() == golem,
                "le coéquipier aurait dû recevoir la cible par radio"));
    }

    @GameTest(template = TEMPLATE)
    public static void naturalSpawnGetsAWeapon(GameTestHelper helper) {
        SoldierEntity soldier = OmniEntities.SOLDIER.get().create(helper.getLevel());
        helper.assertTrue(soldier != null, "création impossible");
        soldier.moveTo(helper.absoluteVec(new Vec3(4.5, 2, 3.5)));
        soldier.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(soldier.blockPosition()),
                net.minecraft.world.entity.MobSpawnType.NATURAL, null);
        helper.assertTrue(BlasterItem.definition(false, soldier.weapon()).isPresent(),
                "un soldat doit apparaître avec un blaster valide");
        helper.succeed();
    }
}
