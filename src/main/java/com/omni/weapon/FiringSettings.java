package com.omni.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;

import java.util.Optional;

/**
 * Mode de tir et ses réglages. Sérialisé « en ligne » dans le JSON de l'arme (champs
 * "fire_mode", "burst", "charge" au même niveau que les autres).
 */
public record FiringSettings(FireMode mode, Optional<Burst> burstBlock, Optional<Charge> chargeBlock) {
    /** Rafale : {@code count} tirs espacés de {@code intervalTicks}. */
    public record Burst(int count, int intervalTicks) {
        public static final Burst DEFAULT = new Burst(3, 2);
        public static final Codec<Burst> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(2, 20).optionalFieldOf("count", DEFAULT.count).forGetter(Burst::count),
                Codec.intRange(1, 20).optionalFieldOf("interval_ticks", DEFAULT.intervalTicks).forGetter(Burst::intervalTicks)
        ).apply(i, Burst::new));
    }

    /** Tir chargé : charge pleine en {@code ticks}, dégâts × de {@code minMultiplier} à {@code maxMultiplier}. */
    public record Charge(int ticks, float minMultiplier, float maxMultiplier) {
        public static final Charge DEFAULT = new Charge(30, 0.5F, 2.0F);
        public static final Codec<Charge> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 400).optionalFieldOf("ticks", DEFAULT.ticks).forGetter(Charge::ticks),
                Codec.floatRange(0, 10).optionalFieldOf("min_multiplier", DEFAULT.minMultiplier).forGetter(Charge::minMultiplier),
                Codec.floatRange(0, 10).optionalFieldOf("max_multiplier", DEFAULT.maxMultiplier).forGetter(Charge::maxMultiplier)
        ).apply(i, Charge::new));

        /** Charge entre 0 et 1 pour {@code heldTicks} ticks de maintien. */
        public float factor(int heldTicks) {
            return Mth.clamp(heldTicks / (float) ticks, 0, 1);
        }

        public float damageMultiplier(float factor) {
            return Mth.lerp(factor, minMultiplier, maxMultiplier);
        }
    }

    public static final MapCodec<FiringSettings> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            FireMode.CODEC.optionalFieldOf("fire_mode", FireMode.SEMI).forGetter(FiringSettings::mode),
            Burst.CODEC.optionalFieldOf("burst").forGetter(FiringSettings::burstBlock),
            Charge.CODEC.optionalFieldOf("charge").forGetter(FiringSettings::chargeBlock)
    ).apply(i, FiringSettings::new));

    /** Réglages de rafale (valeurs par défaut si le bloc est absent). */
    public Burst burst() {
        return burstBlock.orElse(Burst.DEFAULT);
    }

    public Charge charge() {
        return chargeBlock.orElse(Charge.DEFAULT);
    }
}
