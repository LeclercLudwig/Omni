package com.omni.gametest;

import com.omni.Omni;
import com.omni.registry.OmniComponents;
import com.omni.registry.OmniEntities;
import com.omni.weapon.BlasterItem;
import com.omni.weapon.BlasterLogic;
import com.omni.weapon.BlasterState;
import com.omni.weapon.WeaponDefinition;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests automatisés de la logique serveur des blasters.
 * Lancer : ./gradlew runGameTestServer (ou /test runall en jeu).
 * Le sol de la structure est à y=1 relatif (y=0 = bloc de structure), donc tout apparaît à y=2.
 * Structure : data/omni/structure/empty.nbt (boîte de pierre fermée, intérieur 9x4x5),
 * pour que les tirs d'un test ne touchent pas les cibles des tests voisins.
 */
@GameTestHolder(Omni.MODID)
@PrefixGameTestTemplate(false)
public class BlasterGameTests {
    private static final String TEMPLATE = "empty";

    /** Joueur factice à gauche de la structure, arme en main. */
    @SuppressWarnings("removal") // seule API de joueur factice disponible en 1.21.1
    private static ServerPlayer shooter(GameTestHelper helper, String weapon) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 2, 3.5));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, BlasterItem.create(Omni.id(weapon)));
        return player;
    }

    /** Vise le torse de la cible (sous la zone de headshot). */
    private static void aimAt(ServerPlayer player, Mob target, double heightAboveFeet) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, target.position().add(0, heightAboveFeet, 0));
    }

    /** Tirs de CE joueur (les tests tournent en parallèle). */
    private static int boltCount(GameTestHelper helper, ServerPlayer owner) {
        return helper.getLevel().getEntities(OmniEntities.BOLT.get(), helper.getBounds(), e -> e.getOwner() == owner).size();
    }

    private static BlasterState state(ServerPlayer player) {
        return BlasterItem.state(player.getMainHandItem());
    }

    @GameTest(template = TEMPLATE)
    public static void semiAutoFiresOncePerTick(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "blaster_pistol");
        BlasterLogic.tryFire(player, false, 42);
        BlasterLogic.tryFire(player, false, 42); // même tick : doit être refusé (fire_delay_ticks = 5)
        helper.assertTrue(boltCount(helper, player) == 1, "1 tir attendu, " + boltCount(helper, player) + " obtenus");
        helper.assertTrue(state(player).heat() == 10, "chaleur attendue 10, obtenue " + state(player).heat());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void fireDelayAllowsNextShot(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "blaster_pistol");
        BlasterLogic.tryFire(player, false, 42);
        long first = state(player).lastShot();
        helper.runAfterDelay(5, () -> {
            BlasterLogic.tryFire(player, false, 42);
            helper.assertTrue(state(player).lastShot() > first, "le 2e tir aurait dû partir après 5 ticks");
            helper.succeed();
        });
    }

    /** Un paquet arrivé 2 ticks en avance est accepté, mais la cadence moyenne est préservée. */
    @GameTest(template = TEMPLATE)
    public static void jitterToleranceKeepsAverageRate(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "blaster_pistol"); // fire_delay_ticks = 5
        BlasterLogic.tryFire(player, false, 1);
        long first = state(player).lastShot();
        helper.runAfterDelay(3, () -> {
            BlasterLogic.tryFire(player, false, 2); // 2 ticks d'avance : toléré
            helper.assertTrue(state(player).lastShot() == first + 3, "le tir en avance de 2 ticks aurait dû passer");
            helper.assertTrue(state(player).nextShot() == first + 10, "le tir suivant doit rester à +10, pas +8");
            helper.runAfterDelay(3, () -> {
                BlasterLogic.tryFire(player, false, 3); // t+6 < t+10-2 : refusé
                helper.assertTrue(state(player).lastShot() == first + 3, "la cadence moyenne ne doit pas être dépassée");
                helper.succeed();
            });
        });
    }

    /** Client et serveur doivent tirer exactement les mêmes directions pour une même graine. */
    @GameTest(template = TEMPLATE)
    public static void spreadIsDeterministicPerSeed(GameTestHelper helper) {
        WeaponDefinition def = com.omni.weapon.WeaponManager.get(false, Omni.id("scatter_blaster")).orElseThrow();
        Vec3 look = new Vec3(1, 0, 0);
        helper.assertTrue(BlasterLogic.shotDirections(look, def, false, 1234).equals(BlasterLogic.shotDirections(look, def, false, 1234)),
                "même graine, mêmes directions");
        helper.assertFalse(BlasterLogic.shotDirections(look, def, false, 1234).equals(BlasterLogic.shotDirections(look, def, false, 99)),
                "graines différentes, directions différentes");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void scatterFiresEightBolts(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "scatter_blaster");
        BlasterLogic.tryFire(player, false, 42);
        helper.assertTrue(boltCount(helper, player) == 8, "8 projectiles attendus, " + boltCount(helper, player) + " obtenus");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void overheatLocksWeapon(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "blaster_pistol");
        long now = helper.getLevel().getGameTime();
        player.getMainHandItem().set(OmniComponents.STATE.get(), new BlasterState(95, now, false, -1, -1));

        BlasterLogic.tryFire(player, false, 42); // 95 + 10 >= 100 → surchauffe
        helper.assertTrue(state(player).overheated(), "l'arme devrait être en surchauffe");
        long shotAt = state(player).lastShot();

        helper.runAfterDelay(10, () -> { // cadence OK, mais toujours chaud (100 - 10*1.2 = 88)
            BlasterLogic.tryFire(player, false, 42);
            helper.assertTrue(state(player).lastShot() == shotAt, "le tir aurait dû être bloqué par la surchauffe");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void pistolDamagesPig(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "blaster_pistol");
        Mob pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(7.5, 2, 3.5));
        aimAt(player, pig, 0.2);
        BlasterLogic.tryFire(player, true, 42);
        helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() == 4.0F,
                "cochon : 4 PV attendus (10 - 6), " + pig.getHealth() + " obtenus"));
    }

    @GameTest(template = TEMPLATE)
    public static void stunSlowsTarget(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "stun_pistol");
        Mob pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(7.5, 2, 3.5));
        aimAt(player, pig, 0.2);
        BlasterLogic.tryFire(player, true, 42);
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "le cochon devrait être ralenti");
            helper.assertTrue(pig.getHealth() == 9.0F, "cochon : 9 PV attendus, " + pig.getHealth() + " obtenus");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void ionIsStrongAgainstGolems(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "ion_carbine");
        Mob golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new Vec3(7.5, 2, 3.5));
        aimAt(player, golem, 1.0);
        BlasterLogic.tryFire(player, true, 42);
        helper.succeedWhen(() -> helper.assertTrue(golem.getHealth() == 84.0F,
                "golem : 84 PV attendus (100 - 4x4), " + golem.getHealth() + " obtenus"));
    }

    @GameTest(template = TEMPLATE)
    public static void ionIsWeakAgainstOrganics(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "ion_carbine");
        Mob pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(7.5, 2, 3.5));
        aimAt(player, pig, 0.2);
        BlasterLogic.tryFire(player, true, 42);
        helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() == 9.0F,
                "cochon : 9 PV attendus (10 - 4x0.25), " + pig.getHealth() + " obtenus"));
    }

    @GameTest(template = TEMPLATE)
    public static void headshotMultipliesDamage(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "blaster_pistol");
        Mob golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new Vec3(7.5, 2, 3.5));
        aimAt(player, golem, 2.5); // tête du golem (yeux à ~2.3)
        BlasterLogic.tryFire(player, true, 42);
        helper.succeedWhen(() -> helper.assertTrue(golem.getHealth() == 91.0F,
                "golem : 91 PV attendus (100 - 6x1.5), " + golem.getHealth() + " obtenus"));
    }

    @GameTest(template = TEMPLATE)
    public static void boltDisappearsOnWall(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "blaster_pistol");
        player.setXRot(90); // vise le sol
        BlasterLogic.tryFire(player, false, 42);
        helper.succeedWhen(() -> helper.assertTrue(boltCount(helper, player) == 0, "le tir aurait dû disparaître à l'impact"));
    }
}
