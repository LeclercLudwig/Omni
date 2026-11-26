package com.omni.network;

import com.omni.Omni;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → serveur : « je presse la détente ». Le serveur décide si le tir part. */
public record FirePayload(boolean aiming) implements CustomPacketPayload {
    public static final Type<FirePayload> TYPE = new Type<>(Omni.id("fire"));
    public static final StreamCodec<ByteBuf, FirePayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(FirePayload::new, FirePayload::aiming);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
