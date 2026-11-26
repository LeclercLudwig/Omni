package com.omni.salvage;

import com.omni.registry.OmniItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Banc de diagnostic : clic droit avec une pièce endommagée (blaster ou accessoire) pour la
 * recalibrer, contre {@link #SCRAP_COST} ferrailles.
 */
public class DiagnosticBenchBlock extends Block {
    public static final int SCRAP_COST = 2;

    public DiagnosticBenchBlock(Properties properties) {
        super(properties);
    }

    public enum Result { NOT_DAMAGED, MISSING_SCRAP, REPAIRED }

    /** Recalibre la pièce si le joueur a assez de ferraille (gratuit en créatif). */
    public static Result tryRepair(Player player, ItemStack stack) {
        if (!Damage.isDamaged(stack)) return Result.NOT_DAMAGED;
        if (!player.hasInfiniteMaterials()) {
            if (player.getInventory().countItem(OmniItems.SCRAP_METAL.get()) < SCRAP_COST) return Result.MISSING_SCRAP;
            int left = SCRAP_COST;
            for (ItemStack slot : player.getInventory().items) {
                if (left == 0) break;
                if (slot.is(OmniItems.SCRAP_METAL.get())) {
                    int taken = Math.min(left, slot.getCount());
                    slot.shrink(taken);
                    left -= taken;
                }
            }
        }
        Damage.repair(stack);
        return Result.REPAIRED;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!Damage.isDamaged(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;

        Result result = tryRepair(player, stack);
        if (result == Result.MISSING_SCRAP) {
            player.displayClientMessage(Component.translatable("message.omni.bench.need_scrap", SCRAP_COST), true);
            level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.5F, 1.4F);
            return ItemInteractionResult.FAIL;
        }
        player.displayClientMessage(Component.translatable("message.omni.bench.repaired", stack.getHoverName()), true);
        level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.2F);
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6F, 1.8F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    12, 0.3, 0.1, 0.3, 0.1);
        }
        return ItemInteractionResult.SUCCESS;
    }
}
