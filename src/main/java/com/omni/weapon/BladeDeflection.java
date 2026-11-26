package com.omni.weapon;

import com.omni.entity.BoltEntity;
import com.omni.registry.OmniComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Parade des tirs à la lame à énergie (côté serveur).
 * <ul>
 *   <li><b>Garde</b> : lame levée et tir arrivant de face (cône de {@link #GUARD_HALF_ANGLE_DEG}°)
 *       → le tir repart vers son point d'origine, avec une dispersion.</li>
 *   <li><b>Parade parfaite</b> : garde levée depuis moins de {@link #PERFECT_PARRY_TICKS} ticks
 *       → un joueur renvoie le tir là où il vise, une créature vers le tireur ; plus rapide et
 *       plus puissant.</li>
 *   <li><b>Endurance</b> ({@link GuardState}) : une parade normale en coûte, une parade parfaite
 *       en rend. Si elle tombe à zéro, la garde est brisée (le tir est quand même renvoyé).</li>
 * </ul>
 * Le tir change de propriétaire : il peut toucher son tireur d'origine et traverse les alliés
 * du défenseur.
 */
public final class BladeDeflection {
    public static final int PERFECT_PARRY_TICKS = 6;
    public static final double GUARD_HALF_ANGLE_DEG = 55;
    private static final double GUARD_COS = Math.cos(Math.toRadians(GUARD_HALF_ANGLE_DEG));
    private static final float NORMAL_DEFLECT_SPREAD = 12;
    private static final double PERFECT_SPEED_FACTOR = 1.15;
    private static final float PERFECT_DAMAGE_FACTOR = 1.25F;

    private BladeDeflection() {}

    public static boolean isGuarding(LivingEntity entity) {
        return entity.isUsingItem() && entity.getUseItem().getItem() instanceof EnergyBladeItem;
    }

    /** La garde couvre-t-elle une attaque venant de {@code source} ? */
    public static boolean isGuardingAgainst(LivingEntity defender, Vec3 source) {
        if (!isGuarding(defender)) return false;
        Vec3 toSource = source.subtract(defender.getEyePosition());
        if (toSource.lengthSqr() < 1.0E-6) return true;
        return defender.getViewVector(1.0F).normalize().dot(toSource.normalize()) >= GUARD_COS;
    }

    /**
     * Tente de parer {@code bolt} qui vient de toucher {@code defender}.
     * @return true si le tir a été renvoyé (il ne doit alors pas infliger de dégâts)
     */
    public static boolean tryDeflect(LivingEntity defender, BoltEntity bolt) {
        if (!(defender.level() instanceof ServerLevel level)) return false;
        Vec3 velocity = bolt.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0E-6) return false;

        // Le tir vient de l'opposé de sa vitesse.
        Vec3 incomingFrom = bolt.position().subtract(velocity.normalize().scale(8));
        if (!isGuardingAgainst(defender, incomingFrom)) return false;

        boolean perfect = defender.getTicksUsingItem() <= PERFECT_PARRY_TICKS;
        Entity shooter = bolt.getOwner();
        Vec3 direction;
        float spread;
        if (perfect) {
            direction = defender instanceof Player || shooter == null
                    ? defender.getLookAngle()
                    : shooter.getEyePosition().subtract(bolt.position());
            spread = 0;
        } else {
            direction = velocity.reverse();
            spread = NORMAL_DEFLECT_SPREAD;
        }

        bolt.redirect(defender, direction, perfect ? PERFECT_SPEED_FACTOR : 1.0, perfect ? PERFECT_DAMAGE_FACTOR : 1.0F, spread);

        ItemStack blade = defender.getUseItem();
        EquipmentSlot slot = defender.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        long now = level.getGameTime();
        blade.set(OmniComponents.GUARD.get(), EnergyBladeItem.guard(blade).afterParry(now, perfect));
        if (!perfect) EnergyBladeItem.tickGuard(defender, blade); // brise la garde si l'endurance est épuisée

        blade.hurtAndBreak(1, defender, slot);
        if (defender instanceof Player player) player.causeFoodExhaustion(0.3F);

        Vec3 at = bolt.position();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, perfect ? 14 : 6, 0.1, 0.1, 0.1, 0.25);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8F, perfect ? 1.6F : 1.1F);
        if (perfect) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.4F);
            if (defender instanceof ServerPlayer sp) {
                sp.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4F, 1.8F);
            }
        }
        return true;
    }
}
