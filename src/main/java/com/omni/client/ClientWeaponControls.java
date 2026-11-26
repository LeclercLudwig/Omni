package com.omni.client;

import com.omni.network.ChargePayload;
import com.omni.network.FirePayload;
import com.omni.weapon.BlasterItem;
import com.omni.weapon.BlasterState;
import com.omni.weapon.FireMode;
import com.omni.weapon.FiringSettings;
import com.omni.weapon.MagazineState;
import com.omni.weapon.WeaponDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

/**
 * Entrées du joueur : clic gauche = tirer, clic droit (maintenu) = viser.
 * Gère aussi le zoom, la sensibilité réduite en visée et le recul de la caméra.
 */
public final class ClientWeaponControls {
    /** Temps pour passer de la hanche à la visée complète : 1 / AIM_STEP ticks. */
    private static final float AIM_STEP = 0.25F;
    private static final RandomSource RANDOM = RandomSource.create();

    private static float aimProgress;
    private static float aimProgressO;
    private static boolean attackWasDown;

    private ClientWeaponControls() {}

    private static Optional<WeaponDefinition> heldDefinition() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? Optional.empty() : BlasterItem.definition(true, player.getMainHandItem());
    }

    public static float aimProgress(float partialTick) {
        return Mth.lerp(partialTick, aimProgressO, aimProgress);
    }

    /** Annule l'attaque/le minage et l'utilisation vanilla quand on tient un blaster. */
    static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(player.getMainHandItem().getItem() instanceof BlasterItem)) return;
        if (event.isAttack() || event.isUseItem()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        aimProgressO = aimProgress;
        if (player == null || mc.level == null) {
            aimProgress = 0;
            attackWasDown = false;
            localNextShot = Long.MIN_VALUE / 2;
            burstActive = false;
            cancelCharge();
            return;
        }

        ItemStack stack = player.getMainHandItem();
        Optional<WeaponDefinition> held = BlasterItem.definition(true, stack);
        boolean canAct = held.isPresent() && mc.screen == null && !player.isSpectator();

        boolean aiming = canAct && mc.options.keyUse.isDown();
        aimProgress = Mth.clamp(aimProgress + (aiming ? AIM_STEP : -AIM_STEP), 0, 1);

        long now = mc.level.getGameTime();
        trackReloadCompletion(player, stack, held.orElse(null), now);

        while (OmniKeys.RELOAD.consumeClick()) {
            if (canAct) ClientPrediction.requestReload(player, mc.level, stack, held.get(), true);
        }

        boolean attackDown = canAct && mc.options.keyAttack.isDown();
        boolean justPressed = attackDown && !attackWasDown;
        attackWasDown = attackDown;

        if (!canAct) {
            cancelCharge();
            burstActive = false;
            return;
        }
        WeaponDefinition def = held.get();
        if (charging && def.fireMode() != FireMode.CHARGED) cancelCharge(); // arme changée en pleine charge

        switch (def.fireMode()) {
            case SEMI -> {
                if (justPressed) tryShot(player, mc, stack, def, aiming, now, true);
            }
            case AUTO -> {
                if (attackDown) tryShot(player, mc, stack, def, aiming, now, justPressed);
            }
            case BURST -> {
                // Un clic lance la rafale ; elle continue seule, même bouton relâché.
                // Décompte local (et non l'état synchronisé, exprimé en temps serveur).
                if (justPressed && !burstActive) {
                    burstFired = 0;
                    burstActive = true;
                }
                if (burstActive) {
                    Shot shot = tryShot(player, mc, stack, def, aiming, now, burstFired == 0);
                    if (shot == Shot.BLOCKED || shot == Shot.FIRED && burstFired >= def.firing().burst().count()) {
                        burstActive = false;
                    }
                }
            }
            case CHARGED -> tickCharge(player, mc, stack, def, aiming, now, attackDown, justPressed);
        }
    }

    // --- Tir ---------------------------------------------------------------------------------

    private enum Shot { FIRED, WAIT, BLOCKED }

    private static boolean burstActive;
    private static int burstFired;
    private static long localNextShot = Long.MIN_VALUE / 2;

    /**
     * Vérifie que l'arme peut tirer (chargeur, rechargement, surchauffe).
     * Chargeur vide → lance le rechargement (avec retour « plus de munitions » si {@code feedback}).
     */
    private static boolean weaponReady(LocalPlayer player, Minecraft mc, ItemStack stack, WeaponDefinition def,
                                       long now, boolean feedback) {
        MagazineState magazine = BlasterItem.magazine(stack);
        if (magazine.isReloading(def, now)) return false;
        if (magazine.roundsAt(def, now) <= 0) {
            ClientPrediction.requestReload(player, mc.level, stack, def, feedback);
            return false;
        }
        return !BlasterItem.state(stack).overheatedAt(def, now);
    }

    private static Shot tryShot(LocalPlayer player, Minecraft mc, ItemStack stack, WeaponDefinition def,
                                boolean aiming, long now, boolean feedback) {
        // Pré-filtrage local (le serveur revérifie tout) : évite de spammer des paquets
        // et de déclencher le recul pour des tirs qui seraient refusés.
        if (!weaponReady(player, mc, stack, def, now, feedback)) return Shot.BLOCKED;
        if (now < localNextShot && localNextShot - now < 1200) return Shot.WAIT;
        fireNow(player, mc, stack, def, aiming, now, 0, 1.0F);
        return Shot.FIRED;
    }

    private static void fireNow(LocalPlayer player, Minecraft mc, ItemStack stack, WeaponDefinition def,
                                boolean aiming, long now, int chargeTicks, float chargeFactor) {
        // Même graine des deux côtés : le tir prédit suit exactement la trajectoire du vrai.
        int seed = RANDOM.nextInt();
        PacketDistributor.sendToServer(new FirePayload(aiming, seed, chargeTicks));
        ClientPrediction.predictShot(player, mc.level, stack, def, aiming, seed, chargeFactor);
        // Prochaine échéance, calculée en temps client : intervalle court au sein d'une rafale.
        long delay = def.fireDelayTicks();
        if (def.fireMode() == FireMode.BURST && ++burstFired < def.firing().burst().count()) {
            delay = def.firing().burst().intervalTicks();
        }
        localNextShot = now + delay;
        float recoilFactor = def.fireMode() == FireMode.CHARGED ? 0.5F + chargeFactor : 1.0F;
        applyRecoil(def, aiming, recoilFactor);
    }

    // --- Tir chargé --------------------------------------------------------------------------

    private static boolean charging;
    private static long chargeStart;
    private static boolean fullChargeCuePlayed;

    public static boolean isCharging() {
        return charging;
    }

    /** Charge entre 0 et 1 de l'arme en main (0 si on ne charge pas). */
    public static float chargeProgress(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!charging || mc.level == null) return 0;
        return heldDefinition().map(def -> Math.min(1F,
                (mc.level.getGameTime() - chargeStart + partialTick) / def.firing().charge().ticks())).orElse(0F);
    }

    private static void tickCharge(LocalPlayer player, Minecraft mc, ItemStack stack, WeaponDefinition def,
                                   boolean aiming, long now, boolean attackDown, boolean justPressed) {
        if (justPressed && !charging) {
            if (!weaponReady(player, mc, stack, def, now, true)) return;
            if (now < localNextShot && localNextShot - now < 1200) return;
            charging = true;
            chargeStart = now;
            fullChargeCuePlayed = false;
            PacketDistributor.sendToServer(ChargePayload.INSTANCE); // le serveur mesure la durée lui-même
            player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.35F, 1.6F, false);
        }
        if (!charging) return;

        int heldTicks = (int) (now - chargeStart);
        FiringSettings.Charge charge = def.firing().charge();
        if (!fullChargeCuePlayed && heldTicks >= charge.ticks()) {
            fullChargeCuePlayed = true;
            player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.8F, false);
        }
        if (!attackDown) { // relâché : le tir part
            charging = false;
            if (weaponReady(player, mc, stack, def, now, false)) {
                fireNow(player, mc, stack, def, aiming, now, heldTicks, charge.factor(heldTicks));
            }
        }
    }

    private static void cancelCharge() {
        charging = false;
    }

    private static boolean wasReloading;

    /** Son local de fin de rechargement (le rechargement se termine « à la lecture », sans paquet). */
    private static void trackReloadCompletion(LocalPlayer player, ItemStack stack, WeaponDefinition def, long now) {
        boolean reloading = def != null && BlasterItem.magazine(stack).isReloading(def, now);
        if (wasReloading && !reloading && def != null) {
            player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
                    SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.9F, 1.1F, false);
        }
        wasReloading = reloading;
    }

    private static void applyRecoil(WeaponDefinition def, boolean aiming, float multiplier) {
        float factor = (aiming ? 0.5F : 1.0F) * multiplier;
        float pitch = def.recoil().pitch() * factor;
        float yaw = (RANDOM.nextFloat() * 2 - 1) * def.recoil().yawJitter() * factor;
        RecoilController.kick(pitch, yaw);
    }

    /** Zoom en visée (seulement le FOV du monde, pas celui de la main). */
    static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) return;
        float partial = (float) event.getPartialTick();
        float progress = aimProgress(partial);
        float charge = chargeProgress(partial);
        if (progress <= 0 && charge <= 0) return;
        heldDefinition().ifPresent(def -> event.setFOV(event.getFOV()
                * Mth.lerp(progress, 1.0F, def.aimFovMultiplier())
                * Mth.lerp(charge, 1.0F, 0.9F))); // la charge resserre légèrement la vue
    }

    /** Sensibilité réduite proportionnellement au zoom (indispensable pour la lunette). */
    static void onPlayerTurn(CalculatePlayerTurnEvent event) {
        float progress = aimProgress(1.0F);
        if (progress <= 0) return;
        heldDefinition().ifPresent(def ->
                event.setMouseSensitivity(event.getMouseSensitivity() * Mth.lerp(progress, 1.0F, def.aimFovMultiplier())));
    }
}
