package com.omni.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Accessoires posés sur un blaster (identifiants d'items), un par emplacement. */
public record Attachments(Optional<ResourceLocation> scope, Optional<ResourceLocation> barrel, Optional<ResourceLocation> stock) {
    public static final Attachments NONE = new Attachments(Optional.empty(), Optional.empty(), Optional.empty());

    public static final Codec<Attachments> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("scope").forGetter(Attachments::scope),
            ResourceLocation.CODEC.optionalFieldOf("barrel").forGetter(Attachments::barrel),
            ResourceLocation.CODEC.optionalFieldOf("stock").forGetter(Attachments::stock)
    ).apply(i, Attachments::new));

    public static final StreamCodec<ByteBuf, Attachments> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), Attachments::scope,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), Attachments::barrel,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), Attachments::stock,
            Attachments::new);

    public Optional<ResourceLocation> get(AttachmentType.Slot slot) {
        return switch (slot) {
            case SCOPE -> scope;
            case BARREL -> barrel;
            case STOCK -> stock;
        };
    }

    public Attachments with(AttachmentType.Slot slot, Optional<ResourceLocation> item) {
        return switch (slot) {
            case SCOPE -> new Attachments(item, barrel, stock);
            case BARREL -> new Attachments(scope, item, stock);
            case STOCK -> new Attachments(scope, barrel, item);
        };
    }

    public boolean isEmpty() {
        return scope.isEmpty() && barrel.isEmpty() && stock.isEmpty();
    }

    /** Items d'accessoires posés (ignore les identifiants inconnus). */
    public List<AttachmentItem> items() {
        List<AttachmentItem> items = new ArrayList<>();
        for (AttachmentType.Slot slot : AttachmentType.Slot.values()) {
            get(slot).map(BuiltInRegistries.ITEM::get).ifPresent(item -> {
                if (item instanceof AttachmentItem attachment) items.add(attachment);
            });
        }
        return items;
    }

    public WeaponDefinition applyTo(WeaponDefinition def) {
        WeaponDefinition result = def;
        for (AttachmentItem item : items()) result = item.type().apply(result);
        return result;
    }

    public static ItemStack stackOf(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        return new ItemStack(item);
    }
}
