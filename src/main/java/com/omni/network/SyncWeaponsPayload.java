package com.omni.network;

import com.omni.Omni;
import com.omni.weapon.WeaponDefinition;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Serveur → client : la liste complète des armes chargées. */
public record SyncWeaponsPayload(Map<ResourceLocation, WeaponDefinition> weapons) implements CustomPacketPayload {
    public static final Type<SyncWeaponsPayload> TYPE = new Type<>(Omni.id("sync_weapons"));
    public static final StreamCodec<ByteBuf, SyncWeaponsPayload> STREAM_CODEC =
            ByteBufCodecs.<ByteBuf, ResourceLocation, WeaponDefinition, Map<ResourceLocation, WeaponDefinition>>map(
                            HashMap::new, ResourceLocation.STREAM_CODEC, WeaponDefinition.STREAM_CODEC)
                    .map(SyncWeaponsPayload::new, SyncWeaponsPayload::weapons);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
