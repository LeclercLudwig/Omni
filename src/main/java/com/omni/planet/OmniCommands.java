package com.omni.planet;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** /omni travel overworld|khessar (opérateurs) : passer d'un monde à l'autre, en attendant les vaisseaux. */
public final class OmniCommands {
    private OmniCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("omni").requires(source -> source.hasPermission(2))
                .then(Commands.literal("travel")
                        .then(Commands.literal("overworld").executes(ctx -> travel(ctx.getSource(), Level.OVERWORLD)))
                        .then(Commands.literal("khessar").executes(ctx -> travel(ctx.getSource(), Khessar.LEVEL)))));
    }

    private static int travel(CommandSourceStack source, ResourceKey<Level> destination) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (destination == Khessar.LEVEL) {
            CrashLanding.arrive(player);
        } else {
            ServerLevel level = source.getServer().getLevel(destination);
            var spawn = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level.getSharedSpawnPos());
            player.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), player.getXRot());
        }
        source.sendSuccess(() -> Component.translatable("commands.omni.travel", destination.location().toString()), true);
        return 1;
    }
}
