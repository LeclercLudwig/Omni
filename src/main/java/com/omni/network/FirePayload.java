package com.omni.network;

import com.omni.Omni;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → serveur : « je presse la détente ». Le serveur décide si le tir part.
 *
 * @param seed graine de la dispersion, la même que celle utilisée par le client pour prédire le tir
 */
public record FirePayload(boolean aiming, int seed) implements CustomPacketPayload {
    public static final Type<FirePayload> TYPE = new Type<>(Omni.id("fire"));
    public static final StreamCodec<ByteBuf, FirePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, FirePayload::aiming,
            ByteBufCodecs.INT, FirePayload::seed,
            FirePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
