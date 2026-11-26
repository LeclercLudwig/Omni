package com.omni.weapon;

import com.omni.registry.OmniComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Lame à énergie : arme de mêlée qui, en garde (clic droit maintenu), renvoie les tirs
 * de blaster. La garde consomme de l'endurance ({@link GuardState}) ; la logique de parade
 * est dans {@link BladeDeflection}.
 */
public class EnergyBladeItem extends SwordItem {
    public EnergyBladeItem(Properties properties) {
        super(Tiers.DIAMOND, properties.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.2F)));
    }

    public static GuardState guard(ItemStack stack) {
        return stack.getOrDefault(OmniComponents.GUARD.get(), GuardState.FULL);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        long now = level.getGameTime();
        GuardState state = guard(stack);
        if (!state.canRaise(now)) return InteractionResultHolder.fail(stack);

        if (!level.isClientSide) stack.set(OmniComponents.GUARD.get(), state.raise(now));
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** Appelé chaque tick tant que la garde est levée (client et serveur). */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        tickGuard(entity, stack);
    }

    /** Brise la garde si l'endurance est épuisée. Public pour les tests. */
    public static void tickGuard(LivingEntity entity, ItemStack stack) {
        Level level = entity.level();
        long now = level.getGameTime();
        boolean onCooldown = entity instanceof Player p && p.getCooldowns().isOnCooldown(stack.getItem());
        if (!onCooldown && guard(stack).staminaAt(now) > 0) return;

        if (!level.isClientSide && !onCooldown) {
            stack.set(OmniComponents.GUARD.get(), guard(stack).broken(now));
            if (entity instanceof Player p) p.getCooldowns().addCooldown(stack.getItem(), GuardState.BREAK_COOLDOWN_TICKS);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 0.8F, 1.3F);
        }
        entity.stopUsingItem();
    }

    /** Garde baissée (relâchement, changement d'objet, garde brisée…). */
    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        Level level = entity.level();
        if (level.isClientSide) return;
        GuardState state = guard(stack);
        if (state.guarding()) stack.set(OmniComponents.GUARD.get(), state.lower(level.getGameTime()));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    /** Les changements d'endurance ne doivent pas rejouer l'animation de sortie de l'arme. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !newStack.is(this);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.omni.energy_blade.guard").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.omni.energy_blade.parry").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.omni.energy_blade.stamina").withStyle(ChatFormatting.DARK_GRAY));
    }
}
