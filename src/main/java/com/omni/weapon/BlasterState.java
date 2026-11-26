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
 * @param nextShot   gameTime à partir duquel le prochain tir est autorisé, -1 si aucun
 * @param burstShots tirs déjà partis dans la rafale en cours (mode burst), 0 hors rafale
 */
public record BlasterState(float heat, long lastUpdate, boolean overheated, long lastShot, long nextShot, int burstShots) {
    public static final BlasterState EMPTY = new BlasterState(0, 0, false, -1, -1, 0);

    /** Marge (ticks) au-delà de laquelle une rafale interrompue est considérée terminée. */
    private static final int BURST_GRACE_TICKS = 2;

    public BlasterState(float heat, long lastUpdate, boolean overheated, long lastShot, long nextShot) {
        this(heat, lastUpdate, overheated, lastShot, nextShot, 0);
    }

    /**
     * Écart d'horloge au-delà duquel on considère que l'item vient d'un autre monde ou
     * serveur (gameTime incohérent) et on le remet à froid. En dessous, un léger décalage
     * entre l'horloge du client et celle du serveur est normal.
     */
    private static final long CLOCK_RESET_TICKS = 200;

    public static final Codec<BlasterState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("heat").forGetter(BlasterState::heat),
            Codec.LONG.fieldOf("last_update").forGetter(BlasterState::lastUpdate),
            Codec.BOOL.fieldOf("overheated").forGetter(BlasterState::overheated),
            Codec.LONG.fieldOf("last_shot").forGetter(BlasterState::lastShot),
            Codec.LONG.optionalFieldOf("next_shot", -1L).forGetter(BlasterState::nextShot),
            Codec.INT.optionalFieldOf("burst_shots", 0).forGetter(BlasterState::burstShots)
    ).apply(i, BlasterState::new));

    public static final StreamCodec<ByteBuf, BlasterState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, BlasterState::heat,
            ByteBufCodecs.VAR_LONG, BlasterState::lastUpdate,
            ByteBufCodecs.BOOL, BlasterState::overheated,
            ByteBufCodecs.VAR_LONG, BlasterState::lastShot,
            ByteBufCodecs.VAR_LONG, BlasterState::nextShot,
            ByteBufCodecs.VAR_INT, BlasterState::burstShots,
            BlasterState::new);

    public float heatAt(WeaponDefinition def, long now) {
        if (lastUpdate - now > CLOCK_RESET_TICKS) return 0;
        long elapsed = Math.max(0, now - lastUpdate);
        return Math.max(0, heat - def.heat().coolingPerTick() * elapsed);
    }

    public boolean overheatedAt(WeaponDefinition def, long now) {
        return overheated && heatAt(def, now) > 0;
    }

    /**
     * @param jitterTolerance ticks d'avance acceptés sur la cadence. Le serveur en tolère
     *                        un peu, parce que le réseau peut rapprocher deux paquets envoyés
     *                        à intervalle régulier. La cadence moyenne reste garantie par
     *                        {@link #afterShot}.
     */
    public boolean canFire(WeaponDefinition def, long now, int jitterTolerance) {
        if (overheatedAt(def, now)) return false;
        if (nextShot < 0) return true;
        if (nextShot - now > def.fireDelayTicks() + CLOCK_RESET_TICKS) return true;
        return now >= nextShot - jitterTolerance;
    }

    public BlasterState afterShot(WeaponDefinition def, long now) {
        float newHeat = heatAt(def, now) + def.heat().perShot();
        boolean overheat = newHeat >= def.heat().max();
        // Le prochain tir est calculé depuis l'échéance précédente (et non depuis "now") :
        // un tir accepté en avance ne fait pas gagner de cadence sur la durée.
        long base = nextShot < 0 || nextShot - now > def.fireDelayTicks() + CLOCK_RESET_TICKS
                ? now : Math.max(now, nextShot);

        // Rafale : intervalle court entre les tirs d'une même rafale, fire_delay_ticks après le dernier.
        long delay = def.fireDelayTicks();
        int shotsInBurst = 0;
        if (def.fireMode() == FireMode.BURST) {
            FiringSettings.Burst burst = def.firing().burst();
            boolean continuing = burstShots > 0 && lastShot >= 0 && now - lastShot <= burst.intervalTicks() + BURST_GRACE_TICKS;
            shotsInBurst = continuing ? burstShots + 1 : 1;
            if (shotsInBurst >= burst.count()) {
                shotsInBurst = 0;
            } else {
                delay = burst.intervalTicks();
            }
        }
        return new BlasterState(Math.min(newHeat, def.heat().max()), now, overheat, now, base + delay, shotsInBurst);
    }
}
