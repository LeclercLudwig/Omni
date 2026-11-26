package com.omni.salvage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Conduite de carburant encore sous pression : la casser sans précaution la fait exploser.
 * Une <b>découpe méthodique</b> (accroupi, au chalumeau plasma) la neutralise et rend une
 * bonbonne de carburant (voir la table de butin, mêmes conditions).
 */
public class FuelLineBlock extends Block {
    public static final float POWER = 2.5F;

    public FuelLineBlock(Properties properties) {
        super(properties);
    }

    public static boolean isCarefulCut(Player player) {
        return player.isCrouching() && player.getMainHandItem().getItem() instanceof PlasmaCutterItem;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockState result = super.playerWillDestroy(level, pos, state, player);
        if (!isCarefulCut(player)) Explosive.detonate(level, pos, POWER, true, player);
        return result;
    }

    /** Une explosion voisine fait sauter la conduite : réaction en chaîne. */
    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        Explosive.chain(level, pos, POWER, true, explosion.getIndirectSourceEntity());
    }
}
