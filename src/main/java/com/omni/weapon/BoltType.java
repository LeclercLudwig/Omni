package com.omni.weapon;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Comportement du projectile à l'impact (voir BoltEntity#onHitEntity). */
public enum BoltType implements StringRepresentable {
    /** Dégâts classiques. */
    STANDARD("standard"),
    /** Dégâts faibles + ralentissement/faiblesse : pour les primes « vivant ». */
    STUN("stun"),
    /** Dégâts x4 sur le tag omni:ion_vulnerable, x0.25 sur le reste. */
    ION("ion");

    public static final Codec<BoltType> CODEC = StringRepresentable.fromEnum(BoltType::values);

    private final String name;

    BoltType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
