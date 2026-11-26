package com.omni.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Une arme, telle que décrite dans data/&lt;namespace&gt;/omni_weapons/&lt;nom&gt;.json.
 * Tous les champs sauf {@code damage} ont une valeur par défaut.
 */
public record WeaponDefinition(
        FiringSettings firing,
        BoltType boltType,
        float damage,
        float boltSpeed,
        float range,
        int fireDelayTicks,
        int projectilesPerShot,
        Spread spread,
        Recoil recoil,
        Heat heat,
        float aimFovMultiplier,
        float headshotMultiplier,
        int boltColor,
        ResourceLocation fireSound,
        Optional<Magazine> magazine
) {
    /**
     * Chargeur : {@code capacity} tirs, rechargé en {@code reload_ticks} en consommant un
     * objet {@code ammo}. Sans bloc "magazine", l'arme a des munitions illimitées.
     */
    public record Magazine(int capacity, int reloadTicks, ResourceLocation ammo) {
        public static final ResourceLocation DEFAULT_AMMO = ResourceLocation.fromNamespaceAndPath("omni", "gas_cartridge");
        public static final Codec<Magazine> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 1000).fieldOf("capacity").forGetter(Magazine::capacity),
                Codec.intRange(1, 1200).optionalFieldOf("reload_ticks", 40).forGetter(Magazine::reloadTicks),
                ResourceLocation.CODEC.optionalFieldOf("ammo", DEFAULT_AMMO).forGetter(Magazine::ammo)
        ).apply(i, Magazine::new));
    }

    /** Dispersion en degrés (approximatifs), à la hanche et en visée. */
    public record Spread(float hip, float aim) {
        public static final Spread DEFAULT = new Spread(3.0F, 0.75F);
        public static final Codec<Spread> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 45).optionalFieldOf("hip", DEFAULT.hip).forGetter(Spread::hip),
                Codec.floatRange(0, 45).optionalFieldOf("aim", DEFAULT.aim).forGetter(Spread::aim)
        ).apply(i, Spread::new));
    }

    /** Recul en degrés : vers le haut (pitch) et aléatoire gauche/droite (yaw_jitter). */
    public record Recoil(float pitch, float yawJitter) {
        public static final Recoil DEFAULT = new Recoil(1.5F, 0.5F);
        public static final Codec<Recoil> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 45).optionalFieldOf("pitch", DEFAULT.pitch).forGetter(Recoil::pitch),
                Codec.floatRange(0, 45).optionalFieldOf("yaw_jitter", DEFAULT.yawJitter).forGetter(Recoil::yawJitter)
        ).apply(i, Recoil::new));
    }

    /** Chaleur ajoutée par tir, seuil de surchauffe, refroidissement par tick. */
    public record Heat(float perShot, float max, float coolingPerTick) {
        public static final Heat DEFAULT = new Heat(10, 100, 1.5F);
        public static final Codec<Heat> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 1000).optionalFieldOf("per_shot", DEFAULT.perShot).forGetter(Heat::perShot),
                Codec.floatRange(1, 1000).optionalFieldOf("max", DEFAULT.max).forGetter(Heat::max),
                Codec.floatRange(0.01F, 1000).optionalFieldOf("cooling_per_tick", DEFAULT.coolingPerTick).forGetter(Heat::coolingPerTick)
        ).apply(i, Heat::new));
    }

    /** Couleur au format "#RRGGBB". */
    private static final Codec<Integer> HEX_COLOR = Codec.STRING.comapFlatMap(
            s -> {
                try {
                    return DataResult.success(Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16) & 0xFFFFFF);
                } catch (NumberFormatException e) {
                    return DataResult.error(() -> "Couleur invalide : " + s);
                }
            },
            c -> String.format("#%06X", c & 0xFFFFFF));

    public static final Codec<WeaponDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            FiringSettings.MAP_CODEC.forGetter(WeaponDefinition::firing),
            BoltType.CODEC.optionalFieldOf("bolt_type", BoltType.STANDARD).forGetter(WeaponDefinition::boltType),
            Codec.floatRange(0, 1000).fieldOf("damage").forGetter(WeaponDefinition::damage),
            // 3.9 max : au-delà, le paquet d'apparition vanilla tronque la vitesse côté client.
            Codec.floatRange(0.1F, 3.9F).optionalFieldOf("bolt_speed", 2.5F).forGetter(WeaponDefinition::boltSpeed),
            Codec.floatRange(1, 512).optionalFieldOf("range", 96F).forGetter(WeaponDefinition::range),
            Codec.intRange(1, 1200).optionalFieldOf("fire_delay_ticks", 5).forGetter(WeaponDefinition::fireDelayTicks),
            Codec.intRange(1, 32).optionalFieldOf("projectiles_per_shot", 1).forGetter(WeaponDefinition::projectilesPerShot),
            Spread.CODEC.optionalFieldOf("spread", Spread.DEFAULT).forGetter(WeaponDefinition::spread),
            Recoil.CODEC.optionalFieldOf("recoil", Recoil.DEFAULT).forGetter(WeaponDefinition::recoil),
            Heat.CODEC.optionalFieldOf("heat", Heat.DEFAULT).forGetter(WeaponDefinition::heat),
            Codec.floatRange(0.1F, 1F).optionalFieldOf("aim_fov_multiplier", 0.8F).forGetter(WeaponDefinition::aimFovMultiplier),
            Codec.floatRange(1F, 10F).optionalFieldOf("headshot_multiplier", 1.5F).forGetter(WeaponDefinition::headshotMultiplier),
            HEX_COLOR.optionalFieldOf("bolt_color", 0xFF3B30).forGetter(WeaponDefinition::boltColor),
            ResourceLocation.CODEC.optionalFieldOf("fire_sound", ResourceLocation.withDefaultNamespace("entity.firework_rocket.blast"))
                    .forGetter(WeaponDefinition::fireSound),
            Magazine.CODEC.optionalFieldOf("magazine").forGetter(WeaponDefinition::magazine)
    ).apply(i, WeaponDefinition::new));

    public FireMode fireMode() {
        return firing.mode();
    }

    /** Pour la synchro serveur → client (passe par NBT, largement suffisant pour quelques armes). */
    public static final StreamCodec<ByteBuf, WeaponDefinition> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
}
