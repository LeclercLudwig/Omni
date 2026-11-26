package com.omni.weapon;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Accessoire à poser sur un blaster : clic droit avec l'accessoire sur l'arme dans l'inventaire. */
public class AttachmentItem extends Item {
    private final AttachmentType type;

    public AttachmentItem(AttachmentType type, Properties properties) {
        super(properties.stacksTo(1));
        this.type = type;
    }

    public AttachmentType type() {
        return type;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(type.translationKey() + ".effect").withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.omni.attachment.slot",
                Component.translatable("attachment.omni.slot." + type.slot().name().toLowerCase(java.util.Locale.ROOT)))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.omni.attachment.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
