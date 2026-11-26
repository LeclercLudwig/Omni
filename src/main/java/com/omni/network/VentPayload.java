package com.omni.network;

import com.omni.Omni;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → serveur : « je ventile mon arme en surchauffe » (refroidissement actif). */
public record VentPayload() implements CustomPacketPayload {
    public static final VentPayload INSTANCE = new VentPayload();
    public static final Type<VentPayload> TYPE = new Type<>(Omni.id("vent"));
    public static final StreamCodec<ByteBuf, VentPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
