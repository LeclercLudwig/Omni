package com.omni.gametest;

import com.omni.Omni;
import com.omni.planet.CrashLanding;
import com.omni.registry.OmniBlocks;
import com.omni.registry.OmniItems;
import com.omni.salvage.Damage;
import com.omni.weapon.BlasterItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

/**
 * Planète de départ Khessar et arrivée après le crash.
 * <p>
 * Le serveur de test crée toujours un monde plat fixe, sans les dimensions des mods : on teste
 * donc le générateur de Khessar reconstruit depuis sa définition (registre LEVEL_STEM), et
 * l'arrivée en passant la planète en paramètre.
 */
@GameTestHolder(Omni.MODID)
@PrefixGameTestTemplate(false)
public class KhessarGameTests {
    private static final String TEMPLATE = "empty";
    private static final long SEED = 1234L;

    private record Planet(NoiseBasedChunkGenerator generator, RandomState random) {}

    /** Décode data/omni/dimension/khessar.json exactement comme le jeu le fait au chargement d'un monde. */
    private static Planet planet(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        LevelStem stem;
        try (var reader = helper.getLevel().getServer().getResourceManager()
                .getResourceOrThrow(Omni.id("dimension/khessar.json")).openAsReader()) {
            stem = LevelStem.CODEC.parse(net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, access),
                    com.google.gson.JsonParser.parseReader(reader)).getOrThrow();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("dimension omni:khessar illisible", e);
        }
        helper.assertTrue(stem.generator() instanceof NoiseBasedChunkGenerator, "générateur de bruit attendu");
        NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) stem.generator();
        RandomState random = RandomState.create(generator.generatorSettings().value(), access.lookupOrThrow(Registries.NOISE), SEED);
        return new Planet(generator, random);
    }

    @GameTest(template = TEMPLATE)
    public static void khessarUsesItsOwnBiomes(GameTestHelper helper) {
        Planet planet = planet(helper);
        var sampler = planet.random().sampler();
        Set<String> seen = new HashSet<>();
        for (int x = -6000; x <= 6000; x += 400) {
            for (int z = -6000; z <= 6000; z += 400) {
                Holder<Biome> biome = planet.generator().getBiomeSource().getNoiseBiome(x >> 2, 64 >> 2, z >> 2, sampler);
                String id = biome.unwrapKey().orElseThrow().location().toString();
                helper.assertTrue(id.startsWith("omni:khessar_"), "biome inattendu sur Khessar : " + id);
                seen.add(id);
            }
        }
        helper.assertTrue(seen.size() >= 3, "plusieurs biomes attendus sur Khessar, vus : " + seen);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void khessarIsDryLandWithRelief(GameTestHelper helper) {
        Planet planet = planet(helper);
        LevelHeightAccessor height = LevelHeightAccessor.create(-64, 384);
        int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
        for (int x = -3000; x <= 3000; x += 500) {
            for (int z = -3000; z <= 3000; z += 500) {
                int top = planet.generator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, planet.random());
                var column = planet.generator().getBaseColumn(x, z, height, planet.random());
                helper.assertFalse(column.getBlock(top - 1).is(Blocks.WATER), "pas d'eau en surface sur Khessar (" + x + ", " + z + ")");
                helper.assertTrue(top > -40, "terrain trop bas ou vide en (" + x + ", " + z + ") : " + top);
                lowest = Math.min(lowest, top);
                highest = Math.max(highest, top);
            }
        }
        helper.assertTrue(highest - lowest > 20, "relief attendu (cuvettes et mesas), écart " + (highest - lowest));
        helper.succeed();
    }

    @SuppressWarnings("removal") // seule API de joueur factice disponible en 1.21.1
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void crashLandingGivesKitPodAndRespawn(GameTestHelper helper) {
        ServerLevel stand_in = helper.getLevel(); // tient lieu de Khessar dans le serveur de test
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        CrashLanding.arrive(player, stand_in);

        helper.assertTrue(player.getRespawnDimension() == stand_in.dimension(), "la réapparition doit être sur la planète");
        helper.assertTrue(CrashLanding.hasArrived(player), "l'arrivée doit être mémorisée");
        helper.assertTrue(count(player, OmniItems.PLASMA_CUTTER.get()) == 1, "un chalumeau dans le kit");
        helper.assertTrue(player.getInventory().items.stream().anyMatch(s -> s.getItem() instanceof BlasterItem && Damage.isDamaged(s)),
                "un blaster endommagé dans le kit");
        helper.assertTrue(count(player, OmniItems.GAS_CARTRIDGE.get()) > 0, "des cartouches dans le kit");

        BlockPos at = player.blockPosition();
        boolean bench = BlockPos.betweenClosedStream(at.offset(-10, -6, -14), at.offset(10, 6, 6))
                .anyMatch(pos -> stand_in.getBlockState(pos).is(OmniBlocks.DIAGNOSTIC_BENCH.get()));
        helper.assertTrue(bench, "la capsule et son banc de diagnostic doivent être posés près de l'arrivée");

        BlockPos first = player.getRespawnPosition();
        CrashLanding.arrive(player, stand_in);
        helper.assertTrue(count(player, OmniItems.PLASMA_CUTTER.get()) == 1, "le kit n'est donné qu'une fois");
        helper.assertTrue(first.equals(player.getRespawnPosition()), "même site de crash pour tout le monde");
        helper.succeed();
    }

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        return player.getInventory().items.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }
}
