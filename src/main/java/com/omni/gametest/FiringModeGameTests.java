package com.omni.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.omni.Omni;
import com.omni.weapon.BlasterItem;
import com.omni.weapon.BlasterLogic;
import com.omni.weapon.FireMode;
import com.omni.weapon.FiringSettings;
import com.omni.weapon.WeaponDefinition;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Modes de tir "burst" et "charged". */
@GameTestHolder(Omni.MODID)
@PrefixGameTestTemplate(false)
public class FiringModeGameTests {
    private static final String TEMPLATE = "empty";

    @SuppressWarnings("removal") // seule API de joueur factice disponible en 1.21.1
    private static ServerPlayer shooter(GameTestHelper helper, String weapon) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 2, 3.5));
        player.moveTo(pos.x, pos.y, pos.z, -90, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, BlasterItem.create(Omni.id(weapon)));
        return player;
    }

    private static Mob golemInSight(GameTestHelper helper, ServerPlayer player) {
        Mob golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new Vec3(7.5, 2, 3.5));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, golem.position().add(0, 1.0, 0)); // torse, pas la tête
        return golem;
    }

    /** Carabine : 3 tirs espacés de 2 ticks, puis 10 ticks avant la rafale suivante. */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void burstFiresThreeThenWaits(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "burst_carbine");
        List<Long> shots = new ArrayList<>();
        helper.onEachTick(() -> {
            // Détente « maintenue » : on tente de tirer à chaque tick, sans tolérance réseau.
            if (BlasterLogic.fire(player, player.getMainHandItem(), player.getLookAngle(), 0, 1, 0, null)) {
                shots.add(helper.getLevel().getGameTime());
            }
        });
        helper.runAfterDelay(35, () -> {
            List<Long> gaps = new ArrayList<>();
            for (int i = 1; i < shots.size(); i++) gaps.add(shots.get(i) - shots.get(i - 1));
            helper.assertTrue(gaps.size() >= 6, "au moins 7 tirs attendus, " + shots.size() + " obtenus");
            helper.assertTrue(gaps.subList(0, 6).equals(List.of(2L, 2L, 10L, 2L, 2L, 10L)),
                    "rythme attendu 2,2,10,2,2,10 ; obtenu " + gaps);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void fullChargeDoublesDamage(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "marksman_rifle"); // 12 dégâts, x0,5 à x2 en 30 ticks
        Mob golem = golemInSight(helper, player);
        BlasterLogic.startCharge(player);
        helper.runAfterDelay(30, () -> BlasterLogic.tryFire(player, true, 1, 30));
        helper.succeedWhen(() -> helper.assertTrue(golem.getHealth() == 76.0F,
                "charge pleine : 100 - 24 = 76 PV attendus, " + golem.getHealth() + " obtenus"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void tapShotIsWeak(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "marksman_rifle");
        Mob golem = golemInSight(helper, player);
        BlasterLogic.startCharge(player);
        BlasterLogic.tryFire(player, true, 1, 0);
        helper.succeedWhen(() -> helper.assertTrue(golem.getHealth() == 94.0F,
                "tir sans charge : 100 - 6 = 94 PV attendus, " + golem.getHealth() + " obtenus"));
    }

    /** Le client annonce 30 ticks de charge mais n'a chargé que 5 : le serveur plafonne (5 + 2 de marge). */
    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void chargeCannotBeFaked(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "marksman_rifle");
        Mob golem = golemInSight(helper, player);
        BlasterLogic.startCharge(player);
        helper.runAfterDelay(5, () -> BlasterLogic.tryFire(player, true, 1, 30));
        helper.succeedWhen(() -> {
            float lost = golem.getMaxHealth() - golem.getHealth();
            helper.assertTrue(lost > 0, "le tir doit toucher");
            helper.assertTrue(lost < 11, "charge plafonnée à ~7 ticks : ~10 dégâts, pas 24 (obtenu " + lost + ")");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void chargeWithoutStartIsMinimal(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "marksman_rifle");
        Mob golem = golemInSight(helper, player);
        BlasterLogic.tryFire(player, true, 1, 30); // aucun ChargePayload reçu
        helper.succeedWhen(() -> helper.assertTrue(golem.getHealth() == 94.0F,
                "sans début de charge connu : charge minimale (94 PV), " + golem.getHealth() + " obtenus"));
    }

    @GameTest(template = TEMPLATE)
    public static void burstDefaultsWhenBlockMissing(GameTestHelper helper) {
        WeaponDefinition def = WeaponDefinition.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"damage\": 3, \"fire_mode\": \"burst\"}")).getOrThrow();
        helper.assertTrue(def.fireMode() == FireMode.BURST, "mode burst attendu");
        helper.assertTrue(def.firing().burst().equals(FiringSettings.Burst.DEFAULT), "réglages de rafale par défaut");
        helper.succeed();
    }
}
