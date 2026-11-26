package com.omni.weapon;

import com.omni.registry.OmniComponents;
import com.omni.registry.OmniItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
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

    /**
     * Statistiques effectives de l'arme : définition JSON modifiée par les accessoires posés.
     * Toute la logique (tir, prédiction, IA, interface) passe par ici.
     */
    public static Optional<WeaponDefinition> definition(boolean clientSide, ItemStack stack) {
        if (!(stack.getItem() instanceof BlasterItem)) return Optional.empty();
        Attachments attachments = attachments(stack);
        boolean damaged = com.omni.salvage.Damage.isDamaged(stack);
        return WeaponManager.get(clientSide, stack.get(OmniComponents.WEAPON.get()))
                .map(def -> attachments.isEmpty() ? def : attachments.applyTo(def))
                .map(def -> damaged ? def.withSpread(new WeaponDefinition.Spread(def.spread().hip() * 1.5F, def.spread().aim() * 1.5F)) : def);
    }

    public static Attachments attachments(ItemStack stack) {
        return stack.getOrDefault(OmniComponents.ATTACHMENTS.get(), Attachments.NONE);
    }

    /**
     * Pose un accessoire (consomme un exemplaire de {@code attachment}).
     * @return l'accessoire remplacé dans le même emplacement, ou {@link ItemStack#EMPTY}
     */
    public static ItemStack attach(ItemStack blaster, ItemStack attachment) {
        if (!(attachment.getItem() instanceof AttachmentItem item) || com.omni.salvage.Damage.isDamaged(attachment)) {
            return ItemStack.EMPTY;
        }
        AttachmentType.Slot slot = item.type().slot();
        Attachments current = attachments(blaster);
        ItemStack previous = current.get(slot).map(Attachments::stackOf).orElse(ItemStack.EMPTY);
        blaster.set(OmniComponents.ATTACHMENTS.get(), current.with(slot,
                Optional.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item))));
        attachment.shrink(1);
        return previous;
    }

    /** Retire un accessoire (canon, puis viseur, puis crosse). @return l'accessoire retiré ou vide */
    public static ItemStack detach(ItemStack blaster) {
        Attachments current = attachments(blaster);
        for (AttachmentType.Slot slot : new AttachmentType.Slot[]{AttachmentType.Slot.BARREL, AttachmentType.Slot.SCOPE, AttachmentType.Slot.STOCK}) {
            Optional<ResourceLocation> id = current.get(slot);
            if (id.isPresent()) {
                Attachments next = current.with(slot, Optional.empty());
                if (next.isEmpty()) blaster.remove(OmniComponents.ATTACHMENTS.get());
                else blaster.set(OmniComponents.ATTACHMENTS.get(), next);
                return Attachments.stackOf(id.get());
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Dans l'inventaire, clic droit sur l'arme :
     * avec un accessoire en main → le pose (l'ancien revient en main) ; main vide → en retire un.
     */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack blaster, ItemStack carried, Slot slot, ClickAction action,
                                            Player player, SlotAccess carriedAccess) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) return false;
        if (carried.getItem() instanceof AttachmentItem) {
            if (com.omni.salvage.Damage.isDamaged(carried)) { // à recalibrer d'abord
                if (player.level().isClientSide) {
                    player.displayClientMessage(Component.translatable("message.omni.attachment.damaged"), true);
                }
                return true;
            }
            ItemStack previous = attach(blaster, carried);
            if (!previous.isEmpty()) {
                if (carried.isEmpty()) carriedAccess.set(previous);
                else if (!player.getInventory().add(previous)) player.drop(previous, false);
            }
            player.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8F, 1.2F);
            return true;
        }
        if (carried.isEmpty()) {
            ItemStack removed = detach(blaster);
            if (removed.isEmpty()) return false;
            carriedAccess.set(removed);
            player.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8F, 0.9F);
            return true;
        }
        return false;
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
            for (AttachmentItem attachment : attachments(stack).items()) {
                tooltip.add(Component.literal("+ ").append(attachment.getDescription()).withStyle(ChatFormatting.BLUE));
            }
        });
    }
}
