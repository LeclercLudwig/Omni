package com.omni.planet;

import com.omni.Omni;
import com.omni.OmniConfig;
import com.omni.registry.OmniComponents;
import com.omni.registry.OmniItems;
import com.omni.weapon.BlasterItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.gametest.framework.GameTestServer;

/**
 * Début de partie : à sa première connexion, le joueur se réveille sur Khessar à côté de sa
 * capsule de survie écrasée, avec un kit minimal (chalumeau usé, blaster endommagé...).
 * La capsule contient un banc de diagnostic : découper la coque donne la ferraille qui répare
 * le blaster. Désactivable dans config/omni-common.toml (startOnKhessar).
 */
public final class CrashLanding {
    private static final String ARRIVED_TAG = "omni.arrived_on_khessar";
    /** Capsule : 9×6×11, point d'arrivée devant l'écoutille. */
    private static final BlockPos POD_ARRIVAL_OFFSET = new BlockPos(4, 1, 9);

    private CrashLanding() {}

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.server instanceof GameTestServer) return; // joueurs factices des tests
        if (!OmniConfig.START_ON_KHESSAR.get() || hasArrived(player)) return;
        arrive(player);
    }

    public static boolean hasArrived(ServerPlayer player) {
        return player.getPersistentData().getBoolean(ARRIVED_TAG);
    }

    /** Téléporte le joueur sur le site du crash, lui donne son kit et y fixe sa réapparition. */
    public static void arrive(ServerPlayer player) {
        ServerLevel planet = player.server.getLevel(Khessar.LEVEL);
        if (planet == null) {
            Omni.LOGGER.error("Dimension omni:khessar introuvable : départ sur Khessar ignoré");
            return;
        }
        arrive(player, planet);
    }

    /** Variante qui reçoit la planète (le serveur de test ne charge pas les dimensions des mods). */
    public static void arrive(ServerPlayer player, ServerLevel planet) {
        BlockPos arrival = crashSite(player.server, planet);

        Omni.LOGGER.info("Arrivée de {} sur {} en {}", player.getGameProfile().getName(), planet.dimension().location(), arrival);
        player.teleportTo(planet, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, 180, 0);
        player.setRespawnPosition(planet.dimension(), arrival, 180, true, false);

        if (!hasArrived(player)) {
            giveKit(player);
            player.getPersistentData().putBoolean(ARRIVED_TAG, true);
            player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.omni.crash")));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("subtitle.omni.crash")));
            player.sendSystemMessage(Component.translatable("message.omni.crash.hint"));
        }
    }

    /** Pose la capsule une seule fois par monde (près de 0,0) et retourne le point d'arrivée. */
    private static BlockPos crashSite(MinecraftServer server, ServerLevel planet) {
        CrashSiteData data = server.overworld().getDataStorage().computeIfAbsent(CrashSiteData.FACTORY, CrashSiteData.NAME);
        if (data.arrival() != null) return data.arrival();

        BlockPos surface = planet.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, BlockPos.ZERO);
        BlockPos origin = surface.offset(-4, -1, -4);
        StructureTemplate pod = planet.getStructureManager().getOrCreate(Omni.id("escape_pod"));
        pod.placeInWorld(planet, origin, origin, new StructurePlaceSettings(), planet.random, Block.UPDATE_CLIENTS);

        BlockPos front = origin.offset(POD_ARRIVAL_OFFSET);
        BlockPos arrival = planet.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, front);
        data.setArrival(arrival);
        return arrival;
    }

    private static void giveKit(ServerPlayer player) {
        ItemStack cutter = new ItemStack(OmniItems.PLASMA_CUTTER.get());
        cutter.setDamageValue(cutter.getMaxDamage() * 2 / 3); // usé
        ItemStack pistol = BlasterItem.create(Omni.id("blaster_pistol"));
        pistol.set(OmniComponents.DAMAGED.get(), Unit.INSTANCE);
        for (ItemStack stack : new ItemStack[]{cutter, pistol, new ItemStack(OmniItems.GAS_CARTRIDGE.get(), 4),
                new ItemStack(Items.BREAD, 6)}) {
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
    }
}
