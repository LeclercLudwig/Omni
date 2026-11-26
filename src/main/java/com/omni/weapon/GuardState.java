package com.omni.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * Endurance de garde de la lame à énergie, stockée sur l'item (comme la chaleur des blasters :
 * calculée à la lecture, écrite seulement aux changements d'état).
 * <ul>
 *   <li>Garde levée : l'endurance baisse de {@link #DRAIN_PER_TICK} par tick.</li>
 *   <li>Parade normale : coûte {@link #NORMAL_PARRY_COST} ; parade parfaite : rend {@link #PERFECT_PARRY_REFUND}.</li>
 *   <li>À zéro : garde brisée, impossible de la relever pendant {@link #BREAK_COOLDOWN_TICKS} ticks.</li>
 *   <li>Garde baissée : l'endurance remonte de {@link #REGEN_PER_TICK} par tick.</li>
 * </ul>
 *
 * @param stamina     endurance au moment {@code lastUpdate}
 * @param lastUpdate  gameTime de la dernière écriture
 * @param guarding    garde levée depuis {@code lastUpdate}
 * @param brokenUntil gameTime de fin de garde brisée (-1 si aucune)
 */
public record GuardState(float stamina, long lastUpdate, boolean guarding, long brokenUntil) {
    public static final float MAX = 100;
    public static final float DRAIN_PER_TICK = 1.5F;      // garde tenue ~3,3 s au maximum
    public static final float REGEN_PER_TICK = 2.0F;      // plein en 2,5 s
    public static final float NORMAL_PARRY_COST = 20;
    public static final float PERFECT_PARRY_REFUND = 10;
    public static final float MIN_TO_RAISE = 15;
    public static final int BREAK_COOLDOWN_TICKS = 40;
    private static final long CLOCK_RESET_TICKS = 200;

    public static final GuardState FULL = new GuardState(MAX, 0, false, -1);

    public static final Codec<GuardState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("stamina").forGetter(GuardState::stamina),
            Codec.LONG.fieldOf("last_update").forGetter(GuardState::lastUpdate),
            Codec.BOOL.fieldOf("guarding").forGetter(GuardState::guarding),
            Codec.LONG.fieldOf("broken_until").forGetter(GuardState::brokenUntil)
    ).apply(i, GuardState::new));

    public static final StreamCodec<ByteBuf, GuardState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, GuardState::stamina,
            ByteBufCodecs.VAR_LONG, GuardState::lastUpdate,
            ByteBufCodecs.BOOL, GuardState::guarding,
            ByteBufCodecs.VAR_LONG, GuardState::brokenUntil,
            GuardState::new);

    public float staminaAt(long now) {
        if (lastUpdate - now > CLOCK_RESET_TICKS) return MAX; // item venu d'un autre monde
        if (guarding) {
            return Math.max(0, stamina - DRAIN_PER_TICK * Math.max(0, now - lastUpdate));
        }
        long regenFrom = Math.max(lastUpdate, brokenUntil);
        return Math.min(MAX, stamina + REGEN_PER_TICK * Math.max(0, now - regenFrom));
    }

    public boolean isBrokenAt(long now) {
        return brokenUntil >= 0 && now < brokenUntil && brokenUntil - now <= BREAK_COOLDOWN_TICKS + CLOCK_RESET_TICKS;
    }

    public boolean canRaise(long now) {
        return !isBrokenAt(now) && staminaAt(now) >= MIN_TO_RAISE;
    }

    public GuardState raise(long now) {
        return new GuardState(staminaAt(now), now, true, brokenUntil);
    }

    public GuardState lower(long now) {
        return new GuardState(staminaAt(now), now, false, brokenUntil);
    }

    public GuardState afterParry(long now, boolean perfect) {
        float delta = perfect ? PERFECT_PARRY_REFUND : -NORMAL_PARRY_COST;
        return new GuardState(Mth.clamp(staminaAt(now) + delta, 0, MAX), now, guarding, brokenUntil);
    }

    public GuardState broken(long now) {
        return new GuardState(0, now, false, now + BREAK_COOLDOWN_TICKS);
    }
}
