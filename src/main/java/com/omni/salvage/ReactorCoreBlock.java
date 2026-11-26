package com.omni.salvage;

import com.omni.registry.OmniBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Réacteur instable : il crépite, fume, et explose violemment s'il est cassé ou pris dans une
 * explosion. Il faut d'abord le <b>stabiliser</b> en maintenant le chalumeau plasma dessus
 * ({@link PlasmaCutterItem}) : il devient un réacteur stable, qui donne une cellule d'énergie.
 */
public class ReactorCoreBlock extends Block {
    public static final float POWER = 4.0F;

    public ReactorCoreBlock(Properties properties) {
        super(properties);
    }

    public static void stabilize(Level level, BlockPos pos) {
        level.setBlockAndUpdate(pos, OmniBlocks.STABLE_REACTOR_CORE.get().defaultBlockState());
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockState result = super.playerWillDestroy(level, pos, state, player);
        Explosive.detonate(level, pos, POWER, true, player);
        return result;
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        Explosive.chain(level, pos, POWER, true, explosion.getIndirectSourceEntity());
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + random.nextGaussian() * 0.4, y + random.nextGaussian() * 0.4,
                    z + random.nextGaussian() * 0.4, 0, 0, 0);
        }
        if (random.nextInt(5) == 0) level.addParticle(ParticleTypes.SMOKE, x, pos.getY() + 1.05, z, 0, 0.03, 0);
        if (random.nextInt(60) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.6F, 0.6F + random.nextFloat() * 0.2F, false);
        }
    }
}
