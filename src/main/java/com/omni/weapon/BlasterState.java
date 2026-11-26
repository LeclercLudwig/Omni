package com.omni.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * État d'un blaster, stocké dans un data component.
 * <p>
 * La chaleur n'est PAS décrémentée à chaque tick : on stocke la valeur au moment
 * {@code lastUpdate} et on calcule le refroidissement à la lecture. Ça évite de
 * réécrire l'item (et de le renvoyer au client) 20 fois par seconde.
 *
 * @param heat       chaleur au moment {@code lastUpdate}
 * @param lastUpdate gameTime de la dernière écriture
 * @param overheated verrouillé jusqu'à refroidissement complet
 * @param lastShot   gameTime du dernier tir, -1 si jamais tiré
 */
public record BlasterState(float heat, long lastUpdate, boolean overheated, long lastShot) {
    public static final BlasterState EMPTY = new BlasterState(0, 0, false, -1);

    public static final Codec<BlasterState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("heat").forGetter(BlasterState::heat),
            Codec.LONG.fieldOf("last_update").forGetter(BlasterState::lastUpdate),
            Codec.BOOL.fieldOf("overheated").forGetter(BlasterState::overheated),
            Codec.LONG.fieldOf("last_shot").forGetter(BlasterState::lastShot)
    ).apply(i, BlasterState::new));

    public static final StreamCodec<ByteBuf, BlasterState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, BlasterState::heat,
            ByteBufCodecs.VAR_LONG, BlasterState::lastUpdate,
            ByteBufCodecs.BOOL, BlasterState::overheated,
            ByteBufCodecs.VAR_LONG, BlasterState::lastShot,
            BlasterState::new);

    public float heatAt(WeaponDefinition def, long now) {
        // now < lastUpdate : l'item vient d'un autre monde/serveur → on le considère froid.
        if (now < lastUpdate) return 0;
        return Math.max(0, heat - def.heat().coolingPerTick() * (now - lastUpdate));
    }

    public boolean overheatedAt(WeaponDefinition def, long now) {
        return overheated && heatAt(def, now) > 0;
    }

    public boolean canFire(WeaponDefinition def, long now) {
        if (overheatedAt(def, now)) return false;
        return lastShot < 0 || now < lastShot || now - lastShot >= def.fireDelayTicks();
    }

    public BlasterState afterShot(WeaponDefinition def, long now) {
        float newHeat = heatAt(def, now) + def.heat().perShot();
        boolean overheat = newHeat >= def.heat().max();
        return new BlasterState(Math.min(newHeat, def.heat().max()), now, overheat, now);
    }
}
