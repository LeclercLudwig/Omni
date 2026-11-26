package com.omni.entity;

import com.omni.registry.OmniEntities;
import com.omni.registry.OmniTags;
import com.omni.weapon.BoltType;
import com.omni.weapon.WeaponDefinition;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Tir de blaster : projectile sans gravité, visible et assez lent pour être esquivé
 * ou dévié (vanilla gère déjà la déflexion via {@link ProjectileDeflection} : c'est
 * le point d'accroche idéal pour un futur sabre).
 */
public class BoltEntity extends Projectile {
    private static final EntityDataAccessor<Integer> COLOR =
            SynchedEntityData.defineId(BoltEntity.class, EntityDataSerializers.INT);

    private float damage = 5.0F;
    private float headshotMultiplier = 1.5F;
    private BoltType boltType = BoltType.STANDARD;
    private int maxLifeTicks = 60;
    /** Copie locale créée par le client du tireur pour afficher le tir sans attendre le serveur. */
    private boolean predicted;

    public BoltEntity(EntityType<? extends BoltEntity> type, Level level) {
        super(type, level);
    }

    public BoltEntity(Level level, LivingEntity shooter, WeaponDefinition def) {
        this(OmniEntities.BOLT.get(), level);
        setOwner(shooter);
        setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
        this.damage = def.damage();
        this.headshotMultiplier = def.headshotMultiplier();
        this.boltType = def.boltType();
        this.maxLifeTicks = Mth.ceil(def.range() / def.boltSpeed());
        this.entityData.set(COLOR, def.boltColor());
    }

    /**
     * Tir prédit : n'existe que dans le monde client du tireur, n'inflige aucun dégât
     * (c'est le tir du serveur qui compte) et disparaît à l'impact comme le vrai.
     */
    public static BoltEntity predicted(Level clientLevel, LivingEntity shooter, WeaponDefinition def) {
        BoltEntity bolt = new BoltEntity(clientLevel, shooter, def);
        bolt.predicted = true;
        return bolt;
    }

    public boolean isPredicted() {
        return predicted;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(COLOR, 0xFF3B30);
    }

    public int getColor() {
        return entityData.get(COLOR);
    }

    @Override
    public void tick() {
        super.tick(); // gère "a quitté le tireur" (pour ne pas se toucher soi-même)

        if ((!level().isClientSide || predicted) && tickCount > maxLifeTicks) {
            discard();
            return;
        }

        // Rayon entre la position actuelle et la suivante : pas d'effet tunnel à haute vitesse.
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit)) {
            ProjectileDeflection deflection = hitTargetOrDeflectSelf(hit);
            if (deflection != ProjectileDeflection.NONE) return; // renvoyé : on continue au tick suivant
        }
        if (isRemoved()) return;

        Vec3 motion = getDeltaMovement();
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        updateRotation();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide) {
            // Côté client, simple effet visuel : les dégâts sont décidés par le serveur.
            if (predicted) impactEffects(impactPoint(result.getEntity()));
            discard();
            return;
        }

        Entity target = result.getEntity();
        Vec3 impact = impactPoint(target);
        float amount = damage;

        boolean headshot = target instanceof LivingEntity living
                && impact.y >= living.getEyeY() - 0.25;
        if (headshot) amount *= headshotMultiplier;

        if (boltType == BoltType.ION) {
            amount *= target.getType().is(OmniTags.ION_VULNERABLE) ? 4.0F : 0.25F;
        }

        DamageSource source = new DamageSource(
                level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(OmniTags.BLASTER_DAMAGE),
                this, getOwner());

        // Les frames d'invulnérabilité vanilla (10 ticks) mangeraient les tirs automatiques.
        target.invulnerableTime = 0;
        boolean hurt = target.hurt(source, amount);
        target.invulnerableTime = 0;

        if (hurt && boltType == BoltType.STUN && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3), getOwner());
            living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1), getOwner());
        }

        // Petit retour sonore pour le tireur (hitmarker).
        if (hurt && getOwner() instanceof ServerPlayer shooter) {
            shooter.playNotifySound(headshot ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.ARROW_HIT_PLAYER,
                    SoundSource.PLAYERS, 0.5F, headshot ? 1.6F : 1.2F);
        }

        impactEffects(impact);
        discard();
    }

    /**
     * Vanilla place la position d'un EntityHitResult aux pieds de la cible : on recalcule
     * le vrai point d'entrée du tir dans la hitbox (même marge 0.3 que ProjectileUtil).
     */
    private Vec3 impactPoint(Entity target) {
        Vec3 start = position();
        Vec3 end = start.add(getDeltaMovement());
        return target.getBoundingBox().inflate(0.3).clip(start, end)
                .orElse(target.getBoundingBox().getCenter());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result); // déclenche les blocs réactifs (cible, cloche…)
        impactEffects(result.getLocation());
        discard();
    }

    private void impactEffects(Vec3 pos) {
        if (level() instanceof ServerLevel server) {
            // Le tireur a déjà vu l'impact de son tir prédit : on ne lui renvoie pas les particules.
            Entity owner = getOwner();
            for (ServerPlayer viewer : server.players()) {
                if (viewer == owner) continue;
                server.sendParticles(viewer, ParticleTypes.ELECTRIC_SPARK, false, pos.x, pos.y, pos.z, 6, 0.05, 0.05, 0.05, 0.15);
                server.sendParticles(viewer, ParticleTypes.SMOKE, false, pos.x, pos.y, pos.z, 2, 0.02, 0.02, 0.02, 0.01);
            }
        } else if (predicted) {
            for (int i = 0; i < 6; i++) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z,
                        (random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.3);
            }
            level().addParticle(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 0, 0.01, 0);
        }
    }

    /** Les tirs ne sont pas sauvegardés avec le chunk. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
