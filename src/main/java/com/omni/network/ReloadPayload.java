package com.omni.network;

import com.omni.Omni;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → serveur : « je recharge l'arme en main ». Le serveur vérifie et consomme la munition. */
public record ReloadPayload() implements CustomPacketPayload {
    public static final ReloadPayload INSTANCE = new ReloadPayload();
    public static final Type<ReloadPayload> TYPE = new Type<>(Omni.id("reload"));
    public static final StreamCodec<ByteBuf, ReloadPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
