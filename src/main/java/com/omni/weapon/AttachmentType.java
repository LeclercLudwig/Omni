package com.omni.weapon;

import java.util.function.UnaryOperator;

/** Accessoires d'arme : un emplacement chacun, et une modification des statistiques. */
public enum AttachmentType {
    /** Zoom plus fort en visée et dispersion en visée divisée par deux. */
    SCOPE(Slot.SCOPE, def -> def
            .withAimFovMultiplier(Math.max(0.1F, def.aimFovMultiplier() * 0.6F))
            .withSpread(new WeaponDefinition.Spread(def.spread().hip(), def.spread().aim() * 0.5F))),
    /** Dispersion -30 %, recul -15 %. */
    STABILIZER(Slot.BARREL, def -> def
            .withSpread(new WeaponDefinition.Spread(def.spread().hip() * 0.7F, def.spread().aim() * 0.7F))
            .withRecoil(new WeaponDefinition.Recoil(def.recoil().pitch() * 0.85F, def.recoil().yawJitter() * 0.85F))),
    /** Chaleur par tir -30 %, refroidissement +25 %. */
    HEAT_SINK(Slot.BARREL, def -> def
            .withHeat(new WeaponDefinition.Heat(def.heat().perShot() * 0.7F, def.heat().max(), def.heat().coolingPerTick() * 1.25F))),
    /** Recul -40 %. */
    STOCK(Slot.STOCK, def -> def
            .withRecoil(new WeaponDefinition.Recoil(def.recoil().pitch() * 0.6F, def.recoil().yawJitter() * 0.6F)));

    public enum Slot { SCOPE, BARREL, STOCK }

    private final Slot slot;
    private final UnaryOperator<WeaponDefinition> modifier;

    AttachmentType(Slot slot, UnaryOperator<WeaponDefinition> modifier) {
        this.slot = slot;
        this.modifier = modifier;
    }

    public Slot slot() {
        return slot;
    }

    public WeaponDefinition apply(WeaponDefinition def) {
        return modifier.apply(def);
    }

    public String translationKey() {
        return "attachment.omni." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
