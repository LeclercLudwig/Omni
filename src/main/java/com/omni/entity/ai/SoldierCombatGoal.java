package com.omni.entity.ai;

import com.omni.entity.SoldierEntity;
import com.omni.weapon.BladeDeflection;
import com.omni.weapon.BlasterItem;
import com.omni.weapon.BlasterLogic;
import com.omni.weapon.BlasterState;
import com.omni.weapon.FireMode;
import com.omni.weapon.MagazineState;
import com.omni.weapon.WeaponDefinition;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Combat à distance d'un soldat. Deux modes :
 * <ul>
 *   <li><b>ENGAGE</b> : se place entre {@link #MIN_RANGE} et {@link #MAX_ENGAGE_RANGE} blocs,
 *       se déplace latéralement et tire par rafales dès qu'il voit la cible (après un temps de
 *       réaction). Face à une garde (lame à énergie), il retient son tir 1 à 2 s en se décalant
 *       vite pour la contourner, puis tire quand même pour l'épuiser (chaque parade coûte de
 *       l'endurance à la cible).</li>
 *   <li><b>COVER</b> : arme en surchauffe, chargeur vide ou blessure → cherche une position hors
 *       de vue de la cible (à défaut, recule), recharge et laisse refroidir, puis repart à l'assaut.</li>
 * </ul>
 */
public class SoldierCombatGoal extends Goal {
    private static final double MIN_RANGE = 6;
    private static final double MAX_ENGAGE_RANGE = 18;
    private static final int REACTION_TICKS = 10;
    private static final int COVER_SEARCH_ATTEMPTS = 12;
    private static final int COVER_MIN_TICKS = 40;
    private static final int COVER_MAX_TICKS = 200;
    private static final int HURT_COVER_COOLDOWN = 200;
    private static final float HOT_RATIO = 0.85F;
    private static final float COOLED_RATIO = 0.25F;

    private enum Mode { ENGAGE, COVER }

    private final SoldierEntity soldier;
    private Mode mode = Mode.ENGAGE;
    private int seeTime;
    private int burstShotsLeft;
    private int burstCooldown;
    private int strafeTicks;
    private float strafeDirection = 1;
    private int coverTicks;
    private int hurtCoverCooldown;
    private int repathCooldown;
    private boolean coverBecauseHot;
    private int guardHoldTicks;
    private int guardHoldLimit = 30;

    public SoldierCombatGoal(SoldierEntity soldier) {
        this.soldier = soldier;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public boolean isInCover() {
        return mode == Mode.COVER;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = soldier.getTarget();
        return target != null && target.isAlive() && definition() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        soldier.setAggressive(true);
        mode = Mode.ENGAGE;
        seeTime = 0;
        burstCooldown = 0;
        burstShotsLeft = 0;
        LivingEntity target = soldier.getTarget();
        if (target != null) soldier.alertSquad(target);
    }

    @Override
    public void stop() {
        soldier.setAggressive(false);
        soldier.getNavigation().stop();
        mode = Mode.ENGAGE;
    }

    @Nullable
    private WeaponDefinition definition() {
        return BlasterItem.definition(false, soldier.weapon()).orElse(null);
    }

    @Override
    public void tick() {
        LivingEntity target = soldier.getTarget();
        WeaponDefinition def = definition();
        if (target == null || def == null) return;

        boolean sees = soldier.getSensing().hasLineOfSight(target);
        seeTime = sees ? seeTime + 1 : 0;
        soldier.getLookControl().setLookAt(target, 30, 30);
        if (burstCooldown > 0) burstCooldown--;
        if (hurtCoverCooldown > 0) hurtCoverCooldown--;
        if (repathCooldown > 0) repathCooldown--;

        long now = soldier.level().getGameTime();
        BlasterState state = BlasterItem.state(soldier.weapon());
        float heatRatio = state.heatAt(def, now) / def.heat().max();

        // Chargeur vide : recharge (munitions illimitées pour les créatures) et se met à couvert.
        MagazineState magazine = BlasterItem.magazine(soldier.weapon());
        if (magazine.roundsAt(def, now) <= 0) BlasterLogic.tryReload(soldier, soldier.weapon(), null, null);
        boolean reloading = BlasterItem.magazine(soldier.weapon()).isReloading(def, now);

        // "hot" = arme indisponible pour l'instant (surchauffe ou rechargement).
        boolean hot = state.overheatedAt(def, now) || heatRatio >= HOT_RATIO || reloading;

        if (mode == Mode.ENGAGE) {
            boolean hurt = soldier.getHealth() < soldier.getMaxHealth() * 0.4F && hurtCoverCooldown == 0;
            if (hot || hurt) {
                enterCover(target, hot);
                return;
            }
            engage(target, def, sees, now);
        } else {
            cover(target, def, sees, hot, heatRatio, now);
        }
    }

    // --- Mode ENGAGE -------------------------------------------------------------------------

    private void engage(LivingEntity target, WeaponDefinition def, boolean sees, long now) {
        double distance = soldier.distanceTo(target);

        if (!sees || distance > MAX_ENGAGE_RANGE) {
            if (repathCooldown == 0) {
                soldier.getNavigation().moveTo(target, 1.0);
                repathCooldown = 10;
            }
        } else {
            soldier.getNavigation().stop();
            if (--strafeTicks <= 0) {
                strafeTicks = 20 + soldier.getRandom().nextInt(40);
                if (soldier.getRandom().nextFloat() < 0.4F) strafeDirection = -strafeDirection;
            }
            float forward = distance < MIN_RANGE ? -0.5F : 0;
            float sideways = guardedAgainst(target) ? 0.8F : 0.4F; // contourner une garde
            soldier.getMoveControl().strafe(forward, strafeDirection * sideways);
        }

        tryShoot(target, def, sees, distance, now);
    }

    private void tryShoot(LivingEntity target, WeaponDefinition def, boolean sees, double distance, long now) {
        if (!sees || seeTime < REACTION_TICKS || burstCooldown > 0) return;
        if (distance > def.range() * 0.9) return;
        if (guardedAgainst(target)) {
            // Retient son tir le temps de tenter un contournement, puis met la pression sur la garde.
            if (guardHoldTicks < guardHoldLimit) {
                guardHoldTicks++;
                return;
            }
        } else {
            guardHoldTicks = 0;
        }

        if (burstShotsLeft <= 0) {
            burstShotsLeft = def.fireMode() == FireMode.AUTO
                    ? 3 + soldier.getRandom().nextInt(4)
                    : 1 + soldier.getRandom().nextInt(3);
        }

        Vec3 from = new Vec3(soldier.getX(), soldier.getEyeY() - 0.1, soldier.getZ());
        Vec3 aimPoint = target.position().add(0, target.getBbHeight() * 0.55, 0);
        float spread = (def.spread().hip() + npcInaccuracy());
        boolean fired = BlasterLogic.fire(soldier, soldier.weapon(), aimPoint.subtract(from), spread,
                soldier.getRandom().nextInt(), 0, null);
        if (fired && --burstShotsLeft <= 0) {
            burstCooldown = 20 + soldier.getRandom().nextInt(25);
            guardHoldTicks = 0;
            guardHoldLimit = 20 + soldier.getRandom().nextInt(21);
        }
    }

    private boolean guardedAgainst(LivingEntity target) {
        return BladeDeflection.isGuardingAgainst(target, soldier.getEyePosition());
    }

    /** Imprécision ajoutée selon la difficulté, en degrés. */
    private float npcInaccuracy() {
        return switch (soldier.level().getDifficulty()) {
            case PEACEFUL, EASY -> 7.0F;
            case NORMAL -> 4.5F;
            case HARD -> 2.5F;
        };
    }

    // --- Mode COVER --------------------------------------------------------------------------

    private void enterCover(LivingEntity target, boolean becauseHot) {
        mode = Mode.COVER;
        coverTicks = 0;
        coverBecauseHot = becauseHot;
        burstShotsLeft = 0;
        if (!becauseHot) hurtCoverCooldown = HURT_COVER_COOLDOWN;

        Vec3 spot = findCover(target);
        if (spot != null) soldier.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.3);
    }

    private void cover(LivingEntity target, WeaponDefinition def, boolean sees, boolean hot, float heatRatio, long now) {
        coverTicks++;

        // À l'abri, il en profite pour recharger un chargeur à moitié vide.
        if (!sees && def.magazine().isPresent()
                && BlasterItem.magazine(soldier.weapon()).roundsAt(def, now) <= def.magazine().get().capacity() / 2) {
            BlasterLogic.tryReload(soldier, soldier.weapon(), null, null);
        }

        // Pas de couvert trouvé (ou atteint) et toujours exposé : on recule en se décalant.
        if (soldier.getNavigation().isDone() && sees) {
            soldier.getMoveControl().strafe(-0.6F, strafeDirection * 0.3F);
        }

        // Replié pour blessure (arme utilisable) : tir de couverture si la cible est en vue.
        if (!coverBecauseHot && !hot) {
            tryShoot(target, def, sees, soldier.distanceTo(target), now);
        }

        boolean cooled = !hot && heatRatio <= COOLED_RATIO;
        if ((coverTicks >= COVER_MIN_TICKS && (coverBecauseHot ? cooled : !hot)) || coverTicks >= COVER_MAX_TICKS) {
            mode = Mode.ENGAGE;
            soldier.getNavigation().stop();
        }
    }

    /** Cherche une position hors de vue de la cible ; à défaut, une position qui l'éloigne. */
    @Nullable
    private Vec3 findCover(LivingEntity target) {
        Vec3 fallback = null;
        for (int i = 0; i < COVER_SEARCH_ATTEMPTS; i++) {
            Vec3 candidate = DefaultRandomPos.getPosAway(soldier, 12, 5, target.position());
            if (candidate == null) continue;
            if (fallback == null) fallback = candidate;
            if (isHiddenFrom(candidate, target)) return candidate;
        }
        return fallback;
    }

    private boolean isHiddenFrom(Vec3 position, LivingEntity target) {
        Vec3 eye = position.add(0, soldier.getEyeHeight(), 0);
        HitResult hit = soldier.level().clip(new ClipContext(target.getEyePosition(), eye,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
        return hit.getType() == HitResult.Type.BLOCK;
    }
}
