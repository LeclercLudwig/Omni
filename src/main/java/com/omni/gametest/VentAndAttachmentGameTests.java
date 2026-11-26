package com.omni.gametest;

import com.omni.Omni;
import com.omni.registry.OmniComponents;
import com.omni.registry.OmniItems;
import com.omni.weapon.BlasterItem;
import com.omni.weapon.BlasterLogic;
import com.omni.weapon.BlasterState;
import com.omni.weapon.WeaponDefinition;
import com.omni.weapon.WeaponManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Refroidissement actif (ventilation) et accessoires. */
@GameTestHolder(Omni.MODID)
@PrefixGameTestTemplate(false)
public class VentAndAttachmentGameTests {
    private static final String TEMPLATE = "empty";

    @SuppressWarnings("removal") // seule API de joueur factice disponible en 1.21.1
    private static ServerPlayer shooter(GameTestHelper helper, ItemStack weapon) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 2, 3.5));
        player.moveTo(pos.x, pos.y, pos.z, -90, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
        return player;
    }

    private static WeaponDefinition base(String weapon) {
        return WeaponManager.get(false, Omni.id(weapon)).orElseThrow();
    }

    private static void overheatAt(GameTestHelper helper, ItemStack stack, float heat) {
        long now = helper.getLevel().getGameTime();
        stack.set(OmniComponents.STATE.get(), new BlasterState(heat, now, true, -1, -1));
    }

    @GameTest(template = TEMPLATE)
    public static void ventInsideWindowCoolsInstantly(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, BlasterItem.create(Omni.id("blaster_rifle")));
        overheatAt(helper, player.getMainHandItem(), 45); // 45 % : dans la fenêtre 35–55 %
        BlasterLogic.tryVent(player);
        BlasterState state = BlasterItem.state(player.getMainHandItem());
        long now = helper.getLevel().getGameTime();
        helper.assertFalse(state.overheatedAt(base("blaster_rifle"), now), "l'arme doit être déverrouillée");
        helper.assertTrue(state.heatAt(base("blaster_rifle"), now) == 0, "l'arme doit être froide");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void ventOutsideWindowJams(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, BlasterItem.create(Omni.id("blaster_rifle")));
        overheatAt(helper, player.getMainHandItem(), 80); // trop tôt
        BlasterLogic.tryVent(player);
        BlasterState state = BlasterItem.state(player.getMainHandItem());
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(state.overheatedAt(base("blaster_rifle"), now), "l'arme doit rester en surchauffe");
        helper.assertTrue(state.heatAt(base("blaster_rifle"), now) == 100, "l'enrayement remet la chaleur au maximum");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void ventWithoutOverheatDoesNothing(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, BlasterItem.create(Omni.id("blaster_rifle")));
        helper.assertTrue(BlasterLogic.tryVent(player, player.getMainHandItem(), 0, null) == BlasterLogic.VentOutcome.NONE,
                "pas de surchauffe : rien à ventiler");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void stockReducesRecoil(GameTestHelper helper) {
        ItemStack blaster = BlasterItem.create(Omni.id("blaster_rifle"));
        BlasterItem.attach(blaster, new ItemStack(OmniItems.STOCK.get()));
        WeaponDefinition def = BlasterItem.definition(false, blaster).orElseThrow();
        helper.assertTrue(Math.abs(def.recoil().pitch() - base("blaster_rifle").recoil().pitch() * 0.6F) < 1e-4,
                "la crosse doit réduire le recul de 40 %");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void heatSinkReducesHeatPerShot(GameTestHelper helper) {
        ItemStack blaster = BlasterItem.create(Omni.id("blaster_rifle")); // 7 de chaleur par tir
        BlasterItem.attach(blaster, new ItemStack(OmniItems.HEAT_SINK.get()));
        ServerPlayer player = shooter(helper, blaster);
        BlasterLogic.tryFire(player, false, 1);
        float heat = BlasterItem.state(player.getMainHandItem()).heat();
        helper.assertTrue(Math.abs(heat - 4.9F) < 1e-3, "dissipateur : 7 × 0,7 = 4,9 de chaleur attendus, " + heat + " obtenus");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void scopeZoomsAndTightensAimedSpread(GameTestHelper helper) {
        ItemStack blaster = BlasterItem.create(Omni.id("blaster_rifle"));
        BlasterItem.attach(blaster, new ItemStack(OmniItems.SCOPE.get()));
        WeaponDefinition def = BlasterItem.definition(false, blaster).orElseThrow();
        WeaponDefinition base = base("blaster_rifle");
        helper.assertTrue(def.aimFovMultiplier() < base.aimFovMultiplier(), "la lunette doit zoomer davantage");
        helper.assertTrue(Math.abs(def.spread().aim() - base.spread().aim() * 0.5F) < 1e-4, "dispersion en visée divisée par 2");
        helper.assertTrue(def.spread().hip() == base.spread().hip(), "la dispersion à la hanche ne change pas");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void sameSlotAttachmentIsSwappedAndDetachable(GameTestHelper helper) {
        ItemStack blaster = BlasterItem.create(Omni.id("blaster_rifle"));
        ItemStack stabilizer = new ItemStack(OmniItems.STABILIZER.get());
        helper.assertTrue(BlasterItem.attach(blaster, stabilizer).isEmpty(), "premier accessoire : rien de remplacé");
        helper.assertTrue(stabilizer.isEmpty(), "l'accessoire posé est consommé");

        ItemStack previous = BlasterItem.attach(blaster, new ItemStack(OmniItems.HEAT_SINK.get()));
        helper.assertTrue(previous.is(OmniItems.STABILIZER.get()), "même emplacement (canon) : le stabilisateur est rendu");

        ItemStack removed = BlasterItem.detach(blaster);
        helper.assertTrue(removed.is(OmniItems.HEAT_SINK.get()), "le retrait rend le dissipateur");
        helper.assertTrue(BlasterItem.attachments(blaster).isEmpty(), "plus aucun accessoire");
        helper.succeed();
    }
}
