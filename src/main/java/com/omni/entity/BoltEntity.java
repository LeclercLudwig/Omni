package com.omni.entity;

import com.omni.registry.OmniEntities;
import com.omni.registry.OmniSounds;
import com.omni.registry.OmniTags;
import com.omni.weapon.BladeDeflection;
import com.omni.weapon.BoltType;
import com.omni.weapon.FireMode;
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
import net.minecraft.world.entity.player.Player;
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
    /** Synchronisé : le propriétaire, lui, n'est envoyé au client qu'à l'apparition du tir. */
    private static final EntityDataAccessor<Boolean> DEFLECTED =
            SynchedEntityData.defineId(BoltEntity.class, EntityDataSerializers.BOOLEAN);
    /** Épaisseur du rendu (1 = normal ; jusqu'à 2 pour un tir chargé à fond). */
    private static final EntityDataAccessor<Float> SCALE =
            SynchedEntityData.defineId(BoltEntity.class, EntityDataSerializers.FLOAT);

    private float damage = 5.0F;
    private float headshotMultiplier = 1.5F;
    private BoltType boltType = BoltType.STANDARD;
    private int maxLifeTicks = 60;
    /** Copie locale créée par le client du tireur pour afficher le tir sans attendre le serveur. */
    private boolean predicted;

    /** Ticks pendant lesquels le rendu rejoint la vraie trajectoire (environ 8 à 12 blocs). */
    private static final float VISUAL_CONVERGENCE_TICKS = 4;
    /**
     * Client uniquement : décalage d'affichage au départ du tir (canon de l'arme − position réelle).
     * Le tir physique part de l'œil pour viser exactement au réticule ; seul son rendu démarre
     * au canon, puis le décalage s'estompe en {@link #VISUAL_CONVERGENCE_TICKS} ticks.
     */
    private Vec3 visualOffset = Vec3.ZERO;
    private boolean visualOffsetInitialized;

    public BoltEntity(EntityType<? extends BoltEntity> type, Level level) {
        super(type, level);
    }

    public BoltEntity(Level level, LivingEntity shooter, WeaponDefinition def) {
        this(level, shooter, def, 1.0F);
    }

    /** @param chargeFactor charge entre 0 et 1 (armes en mode "charged" uniquement) */
    public BoltEntity(Level level, LivingEntity shooter, WeaponDefinition def, float chargeFactor) {
        this(OmniEntities.BOLT.get(), level);
        setOwner(shooter);
        setPos(shooter.getX(), shooter.getEyeY(), shooter.getZ()); // exactement l'œil : le tir va au réticule
        this.damage = def.damage();
        this.headshotMultiplier = def.headshotMultiplier();
        this.boltType = def.boltType();
        this.maxLifeTicks = Mth.ceil(def.range() / def.boltSpeed());
        this.entityData.set(COLOR, def.boltColor());
        if (def.fireMode() == FireMode.CHARGED) {
            this.damage *= def.firing().charge().damageMultiplier(chargeFactor);
            this.entityData.set(SCALE, 1.0F + chargeFactor);
        }
    }

    /**
     * Tir prédit : n'existe que dans le monde client du tireur, n'inflige aucun dégât
     * (c'est le tir du serveur qui compte) et disparaît à l'impact comme le vrai.
     */
    public static BoltEntity predicted(Level clientLevel, LivingEntity shooter, WeaponDefinition def, float chargeFactor) {
        BoltEntity bolt = new BoltEntity(clientLevel, shooter, def, chargeFactor);
        bolt.predicted = true;
        return bolt;
    }

    public boolean isPredicted() {
        return predicted;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(COLOR, 0xFF3B30);
        builder.define(DEFLECTED, false);
        builder.define(SCALE, 1.0F);
    }

    public int getColor() {
        return entityData.get(COLOR);
    }

    /** Fixe le point de départ visuel (canon) du tir ; à appeler juste après sa création côté client. */
    public void setVisualStart(Vec3 muzzle) {
        visualOffset = muzzle.subtract(position());
        visualOffsetInitialized = true;
    }

    /** Décalage d'affichage à l'instant {@code partialTick} (zéro une fois la trajectoire rejointe). */
    public Vec3 visualOffset(float partialTick) {
        float fade = 1 - (tickCount + partialTick) / VISUAL_CONVERGENCE_TICKS;
        return fade <= 0 ? Vec3.ZERO : visualOffset.scale(fade);
    }

    /** Direction affichée : la vitesse, corrigée tant que le rendu converge vers la vraie trajectoire. */
    public Vec3 visualDirection(float partialTick) {
        boolean converging = tickCount + partialTick < VISUAL_CONVERGENCE_TICKS;
        return converging ? getDeltaMovement().subtract(visualOffset.scale(1 / VISUAL_CONVERGENCE_TICKS)) : getDeltaMovement();
    }

    public float getScale() {
        return entityData.get(SCALE);
    }

    public boolean isDeflected() {
        return entityData.get(DEFLECTED);
    }

    /**
     * Renvoie le tir (parade) : nouveau propriétaire, nouvelle direction, durée de vie
     * réinitialisée. Côté serveur uniquement ; la nouvelle trajectoire est synchronisée.
     */
    public void redirect(Entity newOwner, Vec3 direction, double speedFactor, float damageFactor, float spreadDegrees) {
        double speed = getDeltaMovement().length() * speedFactor;
        setOwner(newOwner);
        damage *= damageFactor;
        shoot(direction.x, direction.y, direction.z, (float) speed, spreadDegrees);
        maxLifeTicks = tickCount + maxLifeTicks;
        entityData.set(DEFLECTED, true);
        hasImpulse = true;
    }

    @Override
    public void tick() {
        super.tick(); // gère "a quitté le tireur" (pour ne pas se toucher soi-même)

        // Tirs des autres (joueurs, soldats) : le rendu part du canon de leur arme.
        if (level().isClientSide && !visualOffsetInitialized) {
            visualOffsetInitialized = true;
            if (!predicted && !isDeflected() && getOwner() instanceof LivingEntity owner && owner.distanceToSqr(this) < 64) {
                visualOffset = MuzzlePositions.thirdPerson(owner).subtract(position());
            }
        }

        if ((!level().isClientSide || predicted) && tickCount > maxLifeTicks) {
            discard();
            return;
        }

        // Rayon entre la position actuelle et la suivante : pas d'effet tunnel à haute vitesse.
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        // Parade à la lame (serveur) : le tir repart au lieu de toucher.
        if (!level().isClientSide && hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity defender
                && BladeDeflection.tryDeflect(defender, this)) {
            return;
        }
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
            // Côté client, simple effet visuel : les dégâts (ou une parade) sont décidés par le
            // serveur. Seul le tir prédit disparaît tout de suite ; les copies des vrais tirs
            // attendent le serveur, qui peut les avoir renvoyés.
            if (predicted) {
                impactEffects(impactPoint(result.getEntity()));
                discard();
            }
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
            server.playSound(owner instanceof Player p ? p : null, pos.x, pos.y, pos.z,
                    OmniSounds.IMPACT.get(), SoundSource.NEUTRAL, 0.6F, 0.9F + random.nextFloat() * 0.2F);
            for (ServerPlayer viewer : server.players()) {
                if (viewer == owner) continue;
                server.sendParticles(viewer, ParticleTypes.ELECTRIC_SPARK, false, pos.x, pos.y, pos.z, 6, 0.05, 0.05, 0.05, 0.15);
                server.sendParticles(viewer, ParticleTypes.SMOKE, false, pos.x, pos.y, pos.z, 2, 0.02, 0.02, 0.02, 0.01);
            }
        } else if (predicted) {
            level().playLocalSound(pos.x, pos.y, pos.z, OmniSounds.IMPACT.get(), SoundSource.NEUTRAL,
                    0.6F, 0.9F + random.nextFloat() * 0.2F, false);
            for (int i = 0; i < 6; i++) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z,
                        (random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.3);
            }
            level().addParticle(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 0, 0.01, 0);
        }
    }

    /** Pas de tir ami : un projectile traverse les alliés de son tireur (même escouade, même équipe). */
    @Override
    protected boolean canHitEntity(Entity target) {
        Entity owner = getOwner();
        if (owner != null && (owner == target || owner.isAlliedTo(target))) return false;
        return super.canHitEntity(target);
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
