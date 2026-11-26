package com.omni.registry;

import com.omni.Omni;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sons du mod (fichiers dans assets/omni/sounds, déclarés dans sounds.json).
 * Les sons de tir sont référencés par les JSON d'armes ("fire_sound": "omni:weapon.<arme>.fire").
 */
public final class OmniSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Omni.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> OVERHEAT = register("weapon.overheat");
    public static final DeferredHolder<SoundEvent, SoundEvent> VENT = register("weapon.vent");
    public static final DeferredHolder<SoundEvent, SoundEvent> VENT_FAIL = register("weapon.vent_fail");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELOAD_START = register("weapon.reload_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> RELOAD_END = register("weapon.reload_end");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRY_FIRE = register("weapon.dry_fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE_START = register("weapon.charge_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> CHARGE_FULL = register("weapon.charge_full");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMPACT = register("weapon.impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> PARRY = register("blade.parry");
    public static final DeferredHolder<SoundEvent, SoundEvent> PARRY_PERFECT = register("blade.parry_perfect");
    public static final DeferredHolder<SoundEvent, SoundEvent> GUARD_BREAK = register("blade.guard_break");

    static {
        for (String weapon : new String[]{"blaster_pistol", "blaster_rifle", "burst_carbine", "heavy_repeater",
                "scatter_blaster", "marksman_rifle", "stun_pistol", "ion_carbine"}) {
            register("weapon." + weapon + ".fire");
        }
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Omni.id(name)));
    }

    private OmniSounds() {}
}
