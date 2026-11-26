package com.omni.weapon;

import com.omni.registry.OmniSounds;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
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

    private static final int CHARGE_TOLERANCE_TICKS = 2;
    /** Début de charge par joueur (thread serveur uniquement, non persistant). */
    private static final Map<ServerPlayer, Long> CHARGE_START = new WeakHashMap<>();

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
        return fire(shooter, stack, direction, spreadDegrees, seed, jitterTolerance, soundExcluded, 1.0F);
    }

    /**
     * @param chargeFactor charge entre 0 et 1 pour une arme en mode "charged" (ignorée sinon) :
     *                     multiplie les dégâts et grossit le tir
     */
    public static boolean fire(LivingEntity shooter, ItemStack stack, Vec3 direction, float spreadDegrees,
                               int seed, int jitterTolerance, @Nullable Player soundExcluded, float chargeFactor) {
        if (!(shooter.level() instanceof ServerLevel level) || !shooter.isAlive()) return false;
        WeaponDefinition def = BlasterItem.definition(false, stack).orElse(null);
        if (def == null) return false;

        long now = level.getGameTime();
        BlasterState state = BlasterItem.state(stack);
        if (!state.canFire(def, now, jitterTolerance)) return false;
        MagazineState magazine = BlasterItem.magazine(stack);
        if (magazine.isReloading(def, now) || magazine.roundsAt(def, now) <= 0) return false;

        // Arme endommagée : enrayement (la munition et la chaleur sont quand même consommées).
        if (com.omni.salvage.Damage.isDamaged(stack) && com.omni.salvage.Damage.isMisfire(seed)) {
            stack.set(OmniComponents.STATE.get(), state.afterShot(def, now));
            if (def.magazine().isPresent()) stack.set(OmniComponents.MAGAZINE.get(), magazine.afterShot(def, now));
            level.playSound(soundExcluded, shooter.getX(), shooter.getEyeY(), shooter.getZ(),
                    OmniSounds.DRY_FIRE.get(), shooter instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE, 0.9F, 0.8F);
            return true;
        }

        for (Vec3 dir : shotDirections(direction, spreadDegrees, def.projectilesPerShot(), seed)) {
            BoltEntity bolt = new BoltEntity(level, shooter, def, chargeFactor);
            bolt.shoot(dir.x, dir.y, dir.z, def.boltSpeed(), 0);
            level.addFreshEntity(bolt);
        }

        BlasterState next = state.afterShot(def, now);
        stack.set(OmniComponents.STATE.get(), next);
        if (def.magazine().isPresent()) stack.set(OmniComponents.MAGAZINE.get(), magazine.afterShot(def, now));

        SoundSource source = shooter instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
        float pitch = 0.9F + level.random.nextFloat() * 0.2F;
        level.playSound(soundExcluded, shooter.getX(), shooter.getEyeY(), shooter.getZ(),
                SoundEvent.createVariableRangeEvent(def.fireSound()), source, 1.0F, pitch);
        if (next.overheated()) {
            level.playSound(soundExcluded, shooter.getX(), shooter.getEyeY(), shooter.getZ(),
                    OmniSounds.OVERHEAT.get(), source, 0.8F, 1.0F);
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
        tryFire(player, aiming, seed, 0);
    }

    /** @param chargeTicks durée de charge annoncée par le client (mode "charged" uniquement) */
    public static void tryFire(ServerPlayer player, boolean aiming, int seed, int chargeTicks) {
        if (player.isSpectator()) return;
        ItemStack stack = player.getMainHandItem();
        WeaponDefinition def = BlasterItem.definition(false, stack).orElse(null);
        if (def == null) return;

        float spread = aiming ? def.spread().aim() : def.spread().hip();
        // Le tireur a déjà vu et entendu son tir (prédiction client) : on l'exclut du son.
        float charge = def.fireMode() == FireMode.CHARGED ? serverChargeFactor(player, def, chargeTicks) : 1.0F;
        if (!fire(player, stack, player.getLookAngle(), spread, seed, SERVER_JITTER_TOLERANCE, player, charge)) {
            // Le client avait prédit ce tir : on lui renvoie l'état réel de l'arme (chaleur).
            resync(player, player.level().getGameTime());
        }
    }

    /**
     * Lance le rechargement de l'arme {@code stack}.
     *
     * @param ammoSource joueur dont on consomme une munition, ou null (créature : munitions illimitées)
     * @return true si le rechargement a commencé
     */
    public static boolean tryReload(LivingEntity shooter, ItemStack stack, @Nullable Player ammoSource,
                                    @Nullable Player soundExcluded) {
        WeaponDefinition def = BlasterItem.definition(shooter.level().isClientSide, stack).orElse(null);
        if (def == null || def.magazine().isEmpty()) return false;
        long now = shooter.level().getGameTime();
        MagazineState magazine = BlasterItem.magazine(stack);
        if (!magazine.canStartReload(def, now)) return false;
        if (ammoSource != null && !ammoSource.hasInfiniteMaterials() && !consumeAmmo(ammoSource, def.magazine().get())) {
            return false;
        }

        stack.set(OmniComponents.MAGAZINE.get(), magazine.startReload(def, now));
        SoundSource source = shooter instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
        shooter.level().playSound(soundExcluded, shooter.getX(), shooter.getEyeY(), shooter.getZ(),
                OmniSounds.RELOAD_START.get(), source, 0.9F, 1.0F);
        return true;
    }

    /** Début de charge d'un joueur (paquet {@code ChargePayload}) : le serveur mesure lui-même la durée. */
    public static void startCharge(ServerPlayer player) {
        WeaponDefinition def = BlasterItem.definition(false, player.getMainHandItem()).orElse(null);
        if (def == null || def.fireMode() != FireMode.CHARGED) return;
        CHARGE_START.put(player, player.level().getGameTime());
    }

    /**
     * Charge effective d'un tir chargé : le minimum entre ce que le client annonce et ce que le
     * serveur a mesuré depuis le début de charge (+ une petite marge réseau). Sans début de
     * charge connu, le tir part à charge minimale.
     */
    private static float serverChargeFactor(ServerPlayer player, WeaponDefinition def, int claimedTicks) {
        Long start = CHARGE_START.remove(player);
        if (start == null) return 0;
        long measured = player.level().getGameTime() - start + CHARGE_TOLERANCE_TICKS;
        return def.firing().charge().factor((int) Math.max(0, Math.min(claimedTicks, measured)));
    }

    public enum VentOutcome { NONE, VENTED, JAMMED }

    /** Marge de la fenêtre de ventilation côté serveur (latence : la chaleur a un peu baissé). */
    private static final float SERVER_VENT_TOLERANCE = 0.04F;

    /**
     * Ventilation (refroidissement actif) d'une arme en surchauffe : dans la fenêtre, l'arme est
     * froide tout de suite ; en dehors, elle s'enraye (chaleur au maximum).
     */
    public static VentOutcome tryVent(LivingEntity shooter, ItemStack stack, float tolerance, @Nullable Player soundExcluded) {
        WeaponDefinition def = BlasterItem.definition(shooter.level().isClientSide, stack).orElse(null);
        if (def == null) return VentOutcome.NONE;
        long now = shooter.level().getGameTime();
        BlasterState state = BlasterItem.state(stack);
        if (!state.overheatedAt(def, now)) return VentOutcome.NONE;

        boolean success = state.inVentWindow(def, now, tolerance);
        stack.set(OmniComponents.STATE.get(), success ? state.vented(now) : state.jammed(def, now));
        SoundSource source = shooter instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
        shooter.level().playSound(soundExcluded, shooter.getX(), shooter.getEyeY(), shooter.getZ(),
                (success ? OmniSounds.VENT : OmniSounds.VENT_FAIL).get(), source, 0.9F, 1.0F);
        return success ? VentOutcome.VENTED : VentOutcome.JAMMED;
    }

    /** Ventilation demandée par un joueur (paquet {@code VentPayload}). */
    public static void tryVent(ServerPlayer player) {
        if (player.isSpectator()) return;
        if (tryVent(player, player.getMainHandItem(), SERVER_VENT_TOLERANCE, player) == VentOutcome.NONE) {
            resync(player, player.level().getGameTime());
        }
    }

    /** Rechargement demandé par un joueur (paquet {@code ReloadPayload}). */
    public static void tryReload(ServerPlayer player) {
        if (player.isSpectator()) return;
        if (!tryReload(player, player.getMainHandItem(), player, player)) {
            resync(player, player.level().getGameTime()); // le client avait prédit le rechargement
        }
    }

    public static int countAmmo(Player player, WeaponDefinition.Magazine magazine) {
        Item ammo = BuiltInRegistries.ITEM.get(magazine.ammo());
        int count = 0;
        for (ItemStack stack : player.getInventory().items) if (stack.is(ammo)) count += stack.getCount();
        for (ItemStack stack : player.getInventory().offhand) if (stack.is(ammo)) count += stack.getCount();
        return count;
    }

    private static boolean consumeAmmo(Player player, WeaponDefinition.Magazine magazine) {
        Item ammo = BuiltInRegistries.ITEM.get(magazine.ammo());
        for (var list : java.util.List.of(player.getInventory().offhand, player.getInventory().items)) {
            for (ItemStack stack : list) {
                if (stack.is(ammo)) {
                    stack.shrink(1);
                    return true;
                }
            }
        }
        return false;
    }

    private static void resync(ServerPlayer player, long now) {
        Long last = LAST_RESYNC.get(player);
        if (last != null && now - last < RESYNC_COOLDOWN_TICKS && now >= last) return;
        LAST_RESYNC.put(player, now);
        player.containerMenu.sendAllDataToRemote();
    }
}
