package com.omni.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Contenu du chargeur d'un blaster. Comme la chaleur, le rechargement se termine « à la
 * lecture » : une fois {@code reloadEnd} passé, le chargeur est plein, sans tâche planifiée.
 *
 * @param rounds    tirs restants (-1 = plein, valeur d'une arme neuve)
 * @param reloadEnd gameTime de fin du rechargement en cours (-1 si aucun)
 */
public record MagazineState(int rounds, long reloadEnd) {
    public static final MagazineState FULL = new MagazineState(-1, -1);
    private static final long CLOCK_RESET_TICKS = 200;

    public static final Codec<MagazineState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("rounds").forGetter(MagazineState::rounds),
            Codec.LONG.fieldOf("reload_end").forGetter(MagazineState::reloadEnd)
    ).apply(i, MagazineState::new));

    public static final StreamCodec<ByteBuf, MagazineState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MagazineState::rounds,
            ByteBufCodecs.VAR_LONG, MagazineState::reloadEnd,
            MagazineState::new);

    /** Rechargement lancé et pas encore terminé à {@code now}. */
    public boolean isReloading(WeaponDefinition def, long now) {
        return def.magazine().isPresent() && reloadEnd >= 0 && now < reloadEnd
                && reloadEnd - now <= def.magazine().get().reloadTicks() + CLOCK_RESET_TICKS;
    }

    /** Tirs disponibles ; illimité si l'arme n'a pas de chargeur. */
    public int roundsAt(WeaponDefinition def, long now) {
        if (def.magazine().isEmpty()) return Integer.MAX_VALUE;
        int capacity = def.magazine().get().capacity();
        if (reloadEnd >= 0 && !isReloading(def, now)) return capacity; // rechargement terminé
        if (rounds < 0) return capacity;
        return Math.min(rounds, capacity);
    }

    /** Avancement du rechargement entre 0 et 1 (1 si aucun rechargement). */
    public float reloadProgress(WeaponDefinition def, long now, float partialTick) {
        if (!isReloading(def, now)) return 1;
        int duration = def.magazine().get().reloadTicks();
        float remaining = reloadEnd - now - partialTick;
        return Math.max(0, Math.min(1, 1 - remaining / duration));
    }

    public boolean canStartReload(WeaponDefinition def, long now) {
        return def.magazine().isPresent() && !isReloading(def, now) && roundsAt(def, now) < def.magazine().get().capacity();
    }

    public MagazineState afterShot(WeaponDefinition def, long now) {
        if (def.magazine().isEmpty()) return this;
        return new MagazineState(roundsAt(def, now) - 1, -1);
    }

    public MagazineState startReload(WeaponDefinition def, long now) {
        return new MagazineState(roundsAt(def, now), now + def.magazine().orElseThrow().reloadTicks());
    }
}
