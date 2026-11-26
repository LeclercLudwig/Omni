package com.omni.weapon;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum FireMode implements StringRepresentable {
    /** Un tir par clic. */
    SEMI("semi"),
    /** Tire tant que le bouton est maintenu, au rythme de fire_delay_ticks. */
    AUTO("auto"),
    /** Un clic = une rafale de N tirs espacés de "burst.interval_ticks", puis fire_delay_ticks. */
    BURST("burst"),
    /** Maintenir pour charger, relâcher pour tirer ; dégâts selon la charge (bloc "charge"). */
    CHARGED("charged");

    public static final Codec<FireMode> CODEC = StringRepresentable.fromEnum(FireMode::values);

    private final String name;

    FireMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
