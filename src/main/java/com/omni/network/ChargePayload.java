package com.omni.network;

import com.omni.Omni;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → serveur : « je commence à charger un tir ». Le serveur mesure la durée jusqu'au tir. */
public record ChargePayload() implements CustomPacketPayload {
    public static final ChargePayload INSTANCE = new ChargePayload();
    public static final Type<ChargePayload> TYPE = new Type<>(Omni.id("charge"));
    public static final StreamCodec<ByteBuf, ChargePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
