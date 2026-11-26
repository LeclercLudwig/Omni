package com.omni.client;

import com.omni.network.FirePayload;
import com.omni.weapon.BlasterItem;
import com.omni.weapon.BlasterState;
import com.omni.weapon.FireMode;
import com.omni.weapon.WeaponDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
    private static long lastLocalShot = Long.MIN_VALUE / 2;

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
            return;
        }

        ItemStack stack = player.getMainHandItem();
        Optional<WeaponDefinition> held = BlasterItem.definition(true, stack);
        boolean canAct = held.isPresent() && mc.screen == null && !player.isSpectator();

        boolean aiming = canAct && mc.options.keyUse.isDown();
        aimProgress = Mth.clamp(aimProgress + (aiming ? AIM_STEP : -AIM_STEP), 0, 1);

        boolean attackDown = canAct && mc.options.keyAttack.isDown();
        boolean justPressed = attackDown && !attackWasDown;
        attackWasDown = attackDown;
        if (!attackDown) return;

        WeaponDefinition def = held.get();
        if (def.fireMode() == FireMode.SEMI && !justPressed) return;

        // Pré-filtrage local (le serveur revérifie tout) : évite de spammer des paquets
        // et de déclencher le recul pour des tirs qui seraient refusés.
        long now = mc.level.getGameTime();
        BlasterState state = BlasterItem.state(stack);
        if (state.overheatedAt(def, now)) return;
        if (now >= lastLocalShot && now - lastLocalShot < def.fireDelayTicks()) return;
        lastLocalShot = now;

        PacketDistributor.sendToServer(new FirePayload(aiming));
        applyRecoil(player, def, aiming);
    }

    private static void applyRecoil(LocalPlayer player, WeaponDefinition def, boolean aiming) {
        float factor = aiming ? 0.5F : 1.0F;
        float pitch = def.recoil().pitch() * factor;
        float yaw = (RANDOM.nextFloat() * 2 - 1) * def.recoil().yawJitter() * factor;
        // turn() multiplie par 0.15 et borne le pitch : on compense.
        player.turn(yaw / 0.15, -pitch / 0.15);
    }

    /** Zoom en visée (seulement le FOV du monde, pas celui de la main). */
    static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) return;
        float progress = aimProgress((float) event.getPartialTick());
        if (progress <= 0) return;
        heldDefinition().ifPresent(def ->
                event.setFOV(event.getFOV() * Mth.lerp(progress, 1.0F, def.aimFovMultiplier())));
    }

    /** Sensibilité réduite proportionnellement au zoom (indispensable pour la lunette). */
    static void onPlayerTurn(CalculatePlayerTurnEvent event) {
        float progress = aimProgress(1.0F);
        if (progress <= 0) return;
        heldDefinition().ifPresent(def ->
                event.setMouseSensitivity(event.getMouseSensitivity() * Mth.lerp(progress, 1.0F, def.aimFovMultiplier())));
    }
}
