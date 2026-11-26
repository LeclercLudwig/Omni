package com.omni.gametest;

import com.omni.Omni;
import com.omni.registry.OmniBlocks;
import com.omni.registry.OmniComponents;
import com.omni.registry.OmniItems;
import com.omni.salvage.Damage;
import com.omni.salvage.DiagnosticBenchBlock;
import com.omni.salvage.ReactorCoreBlock;
import com.omni.weapon.BlasterItem;
import com.omni.weapon.BlasterLogic;
import com.omni.weapon.WeaponDefinition;
import com.omni.weapon.WeaponManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Pilier ferrailleur : épaves, chalumeau, explosions, pièces endommagées, banc de diagnostic.
 * Les tests explosifs ont chacun leur lot (batch) pour ne pas abîmer les arènes voisines.
 */
@GameTestHolder(Omni.MODID)
@PrefixGameTestTemplate(false)
public class SalvageGameTests {
    private static final String TEMPLATE = "empty";
    private static final BlockPos TARGET = new BlockPos(5, 2, 3);

    @SuppressWarnings("removal") // seule API de joueur factice disponible en 1.21.1
    private static ServerPlayer survivor(GameTestHelper helper, ItemStack hand) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        Vec3 pos = helper.absoluteVec(new Vec3(2.5, 2, 3.5));
        player.moveTo(pos.x, pos.y, pos.z, -90, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, hand);
        return player;
    }

    private static Mob witness(GameTestHelper helper) {
        return helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(7.5, 2, 3.5));
    }

    private static void breakBlock(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        player.gameMode.destroyBlock(helper.absolutePos(relative));
    }

    // --- Coque ---

    @GameTest(template = TEMPLATE)
    public static void hullNeedsThePlasmaCutter(GameTestHelper helper) {
        helper.setBlock(TARGET, OmniBlocks.WRECK_HULL.get());
        BlockState hull = helper.getBlockState(TARGET);
        BlockPos at = helper.absolutePos(TARGET);

        ServerPlayer bare = survivor(helper, new ItemStack(Items.DIAMOND_PICKAXE));
        float pickaxe = hull.getDestroyProgress(bare, helper.getLevel(), at);
        List<ItemStack> pickaxeDrops = Block.getDrops(hull, helper.getLevel(), at, null, bare, bare.getMainHandItem());

        ServerPlayer cutter = survivor(helper, new ItemStack(OmniItems.PLASMA_CUTTER.get()));
        float cut = hull.getDestroyProgress(cutter, helper.getLevel(), at);
        List<ItemStack> cutterDrops = Block.getDrops(hull, helper.getLevel(), at, null, cutter, cutter.getMainHandItem());

        helper.assertTrue(cut > pickaxe * 4, "le chalumeau doit découper bien plus vite qu'une pioche (" + cut + " vs " + pickaxe + ")");
        helper.assertTrue(pickaxeDrops.isEmpty(), "à la pioche, la coque ne donne rien");
        helper.assertTrue(cutterDrops.stream().anyMatch(s -> s.is(OmniItems.SCRAP_METAL.get())), "au chalumeau : de la ferraille");
        helper.succeed();
    }

    // --- Conduites de carburant ---

    @GameTest(template = TEMPLATE, batch = "omni_fuel_careless")
    public static void fuelLineExplodesWhenBrokenCarelessly(GameTestHelper helper) {
        helper.setBlock(TARGET, OmniBlocks.FUEL_LINE.get());
        Mob pig = witness(helper);
        ServerPlayer player = survivor(helper, ItemStack.EMPTY);
        breakBlock(helper, player, TARGET);
        helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "la conduite aurait dû exploser"));
    }

    @GameTest(template = TEMPLATE, batch = "omni_fuel_careful")
    public static void carefulCutDropsFuelCanister(GameTestHelper helper) {
        helper.setBlock(TARGET, OmniBlocks.FUEL_LINE.get());
        Mob pig = witness(helper);
        ServerPlayer player = survivor(helper, new ItemStack(OmniItems.PLASMA_CUTTER.get()));
        player.setShiftKeyDown(true);
        player.setPose(Pose.CROUCHING);
        breakBlock(helper, player, TARGET);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(pig.getHealth() == pig.getMaxHealth(), "une découpe méthodique ne doit pas exploser");
            helper.assertItemEntityPresent(OmniItems.FUEL_CANISTER.get(), TARGET, 2);
            helper.succeed();
        });
    }

    // --- Réacteur ---

    @GameTest(template = TEMPLATE, batch = "omni_reactor_unstable")
    public static void unstableReactorExplodes(GameTestHelper helper) {
        helper.setBlock(TARGET, OmniBlocks.REACTOR_CORE.get());
        Mob pig = witness(helper);
        ServerPlayer player = survivor(helper, new ItemStack(OmniItems.PLASMA_CUTTER.get()));
        breakBlock(helper, player, TARGET);
        helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "le réacteur instable aurait dû exploser"));
    }

    @GameTest(template = TEMPLATE, batch = "omni_reactor_stable")
    public static void stabilizedReactorIsSafeAndDropsPowerCell(GameTestHelper helper) {
        helper.setBlock(TARGET, OmniBlocks.REACTOR_CORE.get());
        Mob pig = witness(helper);
        ReactorCoreBlock.stabilize(helper.getLevel(), helper.absolutePos(TARGET)); // 3 s de chalumeau en jeu
        helper.assertBlockPresent(OmniBlocks.STABLE_REACTOR_CORE.get(), TARGET);
        ServerPlayer player = survivor(helper, new ItemStack(OmniItems.PLASMA_CUTTER.get()));
        breakBlock(helper, player, TARGET);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(pig.getHealth() == pig.getMaxHealth(), "un réacteur stabilisé ne doit pas exploser");
            helper.assertItemEntityPresent(OmniItems.POWER_CELL.get(), TARGET, 2);
            helper.succeed();
        });
    }

    // --- Caisses ---

    @GameTest(template = TEMPLATE)
    public static void salvageCrateDropsLoot(GameTestHelper helper) {
        helper.setBlock(TARGET, OmniBlocks.SALVAGE_CRATE.get());
        List<ItemStack> drops = Block.getDrops(helper.getBlockState(TARGET), helper.getLevel(), helper.absolutePos(TARGET), null);
        helper.assertTrue(drops.stream().anyMatch(s -> s.is(OmniItems.GAS_CARTRIDGE.get())), "une caisse contient toujours des cartouches");
        helper.assertTrue(drops.stream().anyMatch(s -> s.is(OmniItems.SCRAP_METAL.get())), "une caisse contient toujours de la ferraille");
        helper.assertFalse(drops.stream().anyMatch(s -> s.is(OmniItems.SALVAGE_CRATE.get())), "la caisse elle-même ne se récupère pas");
        helper.succeed();
    }

    // --- Pièces endommagées ---

    private static ItemStack damaged(ItemStack stack) {
        stack.set(OmniComponents.DAMAGED.get(), Unit.INSTANCE);
        return stack;
    }

    @GameTest(template = TEMPLATE)
    public static void damagedBlasterMisfiresAboutOneTimeInThree(GameTestHelper helper) {
        int misfires = 0;
        for (int seed = 0; seed < 1000; seed++) if (Damage.isMisfire(seed)) misfires++;
        helper.assertTrue(misfires > 250 && misfires < 350, "environ 30 % d'enrayements attendus, " + misfires + "/1000");

        int jamSeed = 0, cleanSeed = 0;
        while (!Damage.isMisfire(jamSeed)) jamSeed++;
        while (Damage.isMisfire(cleanSeed)) cleanSeed++;

        ServerPlayer player = survivor(helper, damaged(BlasterItem.create(Omni.id("blaster_pistol"))));
        WeaponDefinition def = WeaponManager.get(false, Omni.id("blaster_pistol")).orElseThrow();
        BlasterLogic.tryFire(player, false, jamSeed);
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(BlasterItem.state(player.getMainHandItem()).lastShot() >= 0, "l'enrayement compte comme un tir (cadence)");
        helper.assertTrue(BlasterItem.magazine(player.getMainHandItem()).roundsAt(def, now) == 14, "l'enrayement consomme une munition");
        helper.assertTrue(helper.getLevel().getEntities(com.omni.registry.OmniEntities.BOLT.get(), helper.getBounds(), e -> true).isEmpty(),
                "aucun projectile ne doit partir");

        int finalCleanSeed = cleanSeed;
        helper.runAfterDelay(6, () -> {
            BlasterLogic.tryFire(player, false, finalCleanSeed);
            helper.assertFalse(helper.getLevel().getEntities(com.omni.registry.OmniEntities.BOLT.get(), helper.getBounds(), e -> true).isEmpty(),
                    "sans enrayement, le tir doit partir");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void damagedBlasterSpreadsMore(GameTestHelper helper) {
        WeaponDefinition base = WeaponManager.get(false, Omni.id("blaster_rifle")).orElseThrow();
        WeaponDefinition worn = BlasterItem.definition(false, damaged(BlasterItem.create(Omni.id("blaster_rifle")))).orElseThrow();
        helper.assertTrue(Math.abs(worn.spread().hip() - base.spread().hip() * 1.5F) < 1e-4, "dispersion ×1,5 attendue");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void damagedAttachmentIsRefused(GameTestHelper helper) {
        ItemStack blaster = BlasterItem.create(Omni.id("blaster_rifle"));
        ItemStack scope = damaged(new ItemStack(OmniItems.SCOPE.get()));
        BlasterItem.attach(blaster, scope);
        helper.assertTrue(BlasterItem.attachments(blaster).isEmpty(), "un accessoire endommagé ne se pose pas");
        helper.assertTrue(scope.getCount() == 1, "l'accessoire refusé n'est pas consommé");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void benchRecalibratesWithScrap(GameTestHelper helper) {
        ItemStack scope = damaged(new ItemStack(OmniItems.SCOPE.get()));
        ServerPlayer poor = survivor(helper, scope);
        helper.assertTrue(DiagnosticBenchBlock.tryRepair(poor, scope) == DiagnosticBenchBlock.Result.MISSING_SCRAP,
                "sans ferraille, pas de recalibrage");
        helper.assertTrue(Damage.isDamaged(scope), "toujours endommagé");

        ServerPlayer player = survivor(helper, scope);
        player.getInventory().add(new ItemStack(OmniItems.SCRAP_METAL.get(), 3));
        helper.assertTrue(DiagnosticBenchBlock.tryRepair(player, scope) == DiagnosticBenchBlock.Result.REPAIRED, "recalibrage attendu");
        helper.assertFalse(Damage.isDamaged(scope), "la pièce n'est plus endommagée");
        helper.assertTrue(player.getInventory().countItem(OmniItems.SCRAP_METAL.get()) == 1, "2 ferrailles consommées sur 3");
        helper.succeed();
    }

    // --- Structures ---

    @GameTest(template = TEMPLATE)
    public static void wreckStructuresContainSalvage(GameTestHelper helper) {
        for (String name : new String[]{"fighter", "freighter"}) {
            StructureTemplate template = helper.getLevel().getStructureManager().get(Omni.id("wrecks/" + name))
                    .orElseThrow(() -> new IllegalStateException("structure absente : wrecks/" + name));
            StructurePlaceSettings settings = new StructurePlaceSettings();
            helper.assertTrue(template.filterBlocks(BlockPos.ZERO, settings, OmniBlocks.REACTOR_CORE.get()).size() == 1,
                    name + " : un réacteur attendu");
            helper.assertFalse(template.filterBlocks(BlockPos.ZERO, settings, OmniBlocks.SALVAGE_CRATE.get()).isEmpty(),
                    name + " : au moins une caisse attendue");
            helper.assertFalse(template.filterBlocks(BlockPos.ZERO, settings, OmniBlocks.FUEL_LINE.get()).isEmpty(),
                    name + " : au moins une conduite attendue");
        }
        // Génération du monde : les définitions JSON ont été lues et enregistrées.
        var access = helper.getLevel().registryAccess();
        helper.assertTrue(access.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).containsKey(Omni.id("fighter_wreck"))
                && access.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).containsKey(Omni.id("freighter_wreck")),
                "structures d'épaves enregistrées");
        helper.assertTrue(access.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET).containsKey(Omni.id("wrecks")),
                "ensemble de placement des épaves enregistré");
        helper.succeed();
    }
}
