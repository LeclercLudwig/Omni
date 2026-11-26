package com.omni.salvage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Explosion d'un élément d'épave (conduite, réacteur), avec réaction en chaîne possible. */
final class Explosive {
    /** Délai d'une explosion déclenchée par une autre (réaction en chaîne visible, et sûre). */
    private static final int CHAIN_DELAY_TICKS = 4;

    private Explosive() {}

    static void detonate(Level level, BlockPos pos, float power, boolean fire, @Nullable Entity cause) {
        if (level.isClientSide) return;
        level.removeBlock(pos, false);
        level.explode(cause, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, power, fire, Level.ExplosionInteraction.BLOCK);
    }

    /** Appelé pendant une explosion (le bloc est déjà détruit) : on explose un peu plus tard. */
    static void chain(Level level, BlockPos pos, float power, boolean fire, @Nullable Entity cause) {
        MinecraftServer server = level.getServer();
        if (level.isClientSide || server == null) return;
        BlockPos at = pos.immutable();
        server.tell(new TickTask(server.getTickCount() + CHAIN_DELAY_TICKS, () ->
                level.explode(cause, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, power, fire, Level.ExplosionInteraction.BLOCK)));
    }
}
