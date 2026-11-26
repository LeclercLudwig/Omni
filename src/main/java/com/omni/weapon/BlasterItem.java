package com.omni.weapon;

import com.omni.registry.OmniComponents;
import com.omni.registry.OmniItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Item générique : les stats viennent de la {@link WeaponDefinition} référencée par
 * le composant {@code omni:weapon}. Le tir est géré par {@code ClientWeaponControls}
 * (entrées) puis {@link BlasterLogic} (serveur), pas par use()/attack().
 */
public class BlasterItem extends Item {
    public BlasterItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(ResourceLocation weaponId) {
        ItemStack stack = new ItemStack(OmniItems.BLASTER.get());
        stack.set(OmniComponents.WEAPON.get(), weaponId);
        return stack;
    }

    public static Optional<WeaponDefinition> definition(boolean clientSide, ItemStack stack) {
        if (!(stack.getItem() instanceof BlasterItem)) return Optional.empty();
        return WeaponManager.get(clientSide, stack.get(OmniComponents.WEAPON.get()));
    }

    public static MagazineState magazine(ItemStack stack) {
        return stack.getOrDefault(OmniComponents.MAGAZINE.get(), MagazineState.FULL);
    }

    public static BlasterState state(ItemStack stack) {
        return stack.getOrDefault(OmniComponents.STATE.get(), BlasterState.EMPTY);
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation id = stack.get(OmniComponents.WEAPON.get());
        return id == null ? super.getName(stack) : Component.translatable(id.toLanguageKey("weapon"));
    }

    /** Pas de minage au blaster (le clic gauche sert à tirer). */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    /**
     * Chaque tir modifie le composant STATE ; sans ça, l'arme refait son animation
     * de « sortie » à chaque tir.
     */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !newStack.is(this)
                || !Objects.equals(oldStack.get(OmniComponents.WEAPON.get()), newStack.get(OmniComponents.WEAPON.get()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // Les infobulles ne sont construites que côté client.
        definition(true, stack).ifPresent(def -> {
            tooltip.add(Component.translatable("tooltip.omni.damage", def.damage(), def.projectilesPerShot())
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.omni.fire_mode." + def.fireMode().getSerializedName())
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.omni.bolt_type." + def.boltType().getSerializedName())
                    .withStyle(ChatFormatting.DARK_AQUA));
            def.magazine().ifPresent(mag -> tooltip.add(Component.translatable("tooltip.omni.magazine",
                    mag.capacity(), String.format("%.1f", mag.reloadTicks() / 20.0)).withStyle(ChatFormatting.GRAY)));
        });
    }
}
