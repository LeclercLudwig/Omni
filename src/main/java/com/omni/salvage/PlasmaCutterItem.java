package com.omni.salvage;

import com.omni.registry.OmniTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chalumeau plasma : seul outil qui découpe efficacement la coque des épaves (et en récupère la
 * ferraille). Maintenu 3 s sur un réacteur instable, il le stabilise.
 */
public class PlasmaCutterItem extends Item {
    public static final int STABILIZE_TICKS = 60;
    private static final float CUT_SPEED = 12.0F;

    /** Réacteur visé par chaque joueur pendant une stabilisation (client et serveur séparément). */
    private static final Map<UUID, BlockPos> TARGETS = new ConcurrentHashMap<>();

    public PlasmaCutterItem(Properties properties) {
        super(properties.durability(600));
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return state.is(OmniTags.WRECK_BLOCKS);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return state.is(OmniTags.WRECK_BLOCKS) ? CUT_SPEED : 1.0F;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0) stack.hurtAndBreak(1, miner, EquipmentSlot.MAINHAND);
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof ReactorCoreBlock)) {
            return InteractionResult.PASS;
        }
        TARGETS.put(player.getUUID(), context.getClickedPos().immutable());
        player.startUsingItem(context.getHand());
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        BlockPos target = TARGETS.get(entity.getUUID());
        if (target == null || !(level.getBlockState(target).getBlock() instanceof ReactorCoreBlock)
                || entity.distanceToSqr(target.getCenter()) > 36) {
            entity.stopUsingItem();
            return;
        }
        if (level.isClientSide) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, target.getX() + level.random.nextDouble(),
                    target.getY() + level.random.nextDouble(), target.getZ() + level.random.nextDouble(), 0, 0, 0);
        } else if (remaining % 10 == 0) {
            level.playSound(null, target, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.6F);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        BlockPos target = TARGETS.remove(entity.getUUID());
        if (!level.isClientSide && target != null && level.getBlockState(target).getBlock() instanceof ReactorCoreBlock) {
            ReactorCoreBlock.stabilize(level, target);
            stack.hurtAndBreak(10, entity, EquipmentSlot.MAINHAND);
        }
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        TARGETS.remove(entity.getUUID());
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return STABILIZE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.omni.plasma_cutter.cut").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.omni.plasma_cutter.fuel").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.omni.plasma_cutter.reactor").withStyle(ChatFormatting.GOLD));
    }
}
