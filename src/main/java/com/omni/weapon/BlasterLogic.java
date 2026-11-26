package com.omni.weapon;

import com.omni.entity.BoltEntity;
import com.omni.registry.OmniComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tir côté serveur, commun aux joueurs et aux créatures : c'est ici que tout est validé
 * (cadence, surchauffe) et que les projectiles apparaissent.
 */
public final class BlasterLogic {
    /** Ticks d'avance tolérés sur la cadence pour les joueurs (gigue réseau). */
    public static final int SERVER_JITTER_TOLERANCE = 2;
    /** Échelle vanilla de Projectile#shoot : 1 point d'imprécision ≈ 1 degré. */
    private static final double SPREAD_SCALE = 0.0172275;
    private static final long RESYNC_COOLDOWN_TICKS = 10;

    /** Dernier renvoi d'inventaire par joueur (thread serveur uniquement). */
    private static final Map<ServerPlayer, Long> LAST_RESYNC = new WeakHashMap<>();

    private BlasterLogic() {}

    /**
     * Directions des projectiles d'un tir. Elles dépendent uniquement de la graine : le
     * client qui prédit le tir et le serveur qui le valide obtiennent les mêmes trajectoires.
     */
    public static List<Vec3> shotDirections(Vec3 look, float spreadDegrees, int count, int seed) {
        RandomSource random = RandomSource.create(seed);
        double spread = SPREAD_SCALE * spreadDegrees;
        Vec3 forward = look.normalize();
        List<Vec3> directions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            directions.add(forward.add(random.triangle(0, spread), random.triangle(0, spread), random.triangle(0, spread)));
        }
        return directions;
    }

    public static List<Vec3> shotDirections(Vec3 look, WeaponDefinition def, boolean aiming, int seed) {
        return shotDirections(look, aiming ? def.spread().aim() : def.spread().hip(), def.projectilesPerShot(), seed);
    }

    /**
     * Tire avec l'arme {@code stack} tenue par {@code shooter}.
     *
     * @param direction       direction visée (avant dispersion)
     * @param spreadDegrees   dispersion totale en degrés
     * @param jitterTolerance avance tolérée sur la cadence (0 pour une créature)
     * @param soundExcluded   joueur qui a déjà entendu son tir (prédiction client), ou null
     * @return true si le tir est parti
     */
    public static boolean fire(LivingEntity shooter, ItemStack stack, Vec3 direction, float spreadDegrees,
                               int seed, int jitterTolerance, @Nullable Player soundExcluded) {
        if (!(shooter.level() instanceof ServerLevel level) || !shooter.isAlive()) return false;
        WeaponDefinition def = BlasterItem.definition(false, stack).orElse(null);
        if (def == null) return false;

        long now = level.getGameTime();
        BlasterState state = BlasterItem.state(stack);
        if (!state.canFire(def, now, jitterTolerance)) return false;

        for (Vec3 dir : shotDirections(direction, spreadDegrees, def.projectilesPerShot(), seed)) {
            BoltEntity bolt = new BoltEntity(level, shooter, def);
            bolt.shoot(dir.x, dir.y, dir.z, def.boltSpeed(), 0);
            level.addFreshEntity(bolt);
        }

        BlasterState next = state.afterShot(def, now);
        stack.set(OmniComponents.STATE.get(), next);

        SoundSource source = shooter instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
        float pitch = 0.9F + level.random.nextFloat() * 0.2F;
        level.playSound(soundExcluded, shooter.getX(), shooter.getEyeY(), shooter.getZ(),
                SoundEvent.createVariableRangeEvent(def.fireSound()), source, 1.0F, pitch);
        if (next.overheated()) {
            level.playSound(soundExcluded, shooter.getX(), shooter.getEyeY(), shooter.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, source, 0.8F, 1.2F);
        }
        return true;
    }

    /**
     * Tir d'un joueur (paquet {@code FirePayload}).
     * <p>
     * Note anti-triche : la graine vient du client. Elle ne fixe que le tirage de la
     * dispersion ; un client modifié pourrait choisir des graines favorables. Si ça devient
     * un problème, le serveur pourra imposer la suite de graines.
     */
    public static void tryFire(ServerPlayer player, boolean aiming, int seed) {
        if (player.isSpectator()) return;
        ItemStack stack = player.getMainHandItem();
        WeaponDefinition def = BlasterItem.definition(false, stack).orElse(null);
        if (def == null) return;

        float spread = aiming ? def.spread().aim() : def.spread().hip();
        // Le tireur a déjà vu et entendu son tir (prédiction client) : on l'exclut du son.
        if (!fire(player, stack, player.getLookAngle(), spread, seed, SERVER_JITTER_TOLERANCE, player)) {
            // Le client avait prédit ce tir : on lui renvoie l'état réel de l'arme (chaleur).
            resync(player, player.level().getGameTime());
        }
    }

    private static void resync(ServerPlayer player, long now) {
        Long last = LAST_RESYNC.get(player);
        if (last != null && now - last < RESYNC_COOLDOWN_TICKS && now >= last) return;
        LAST_RESYNC.put(player, now);
        player.containerMenu.sendAllDataToRemote();
    }
}
