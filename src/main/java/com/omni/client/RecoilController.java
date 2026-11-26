package com.omni.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Recul lissé, appliqué image par image (et non par tick) :
 * <ol>
 *   <li><b>Coup</b> : la caméra monte rapidement mais sans à-coup (constante de temps ~30 ms).</li>
 *   <li><b>Retour</b> : une fois le coup absorbé et après une courte pause, une partie du recul
 *       redescend doucement (~180 ms), pour que le réticule ne dérive pas indéfiniment en rafale.</li>
 * </ol>
 * Le joueur garde la main : ses mouvements de souris s'ajoutent simplement par-dessus.
 */
public final class RecoilController {
    private static final double KICK_TIME_CONSTANT = 0.030;
    private static final double RECOVERY_TIME_CONSTANT = 0.180;
    private static final double RECOVERY_DELAY = 0.080;
    /** Fraction du recul vertical rendue au joueur. */
    private static final float RECOVERY_RATIO = 0.6F;
    private static final float EPSILON = 0.001F;

    private static float pendingPitch;
    private static float pendingYaw;
    private static float pendingRecovery;
    private static long lastFrameNanos = -1;
    private static long lastKickNanos;

    private RecoilController() {}

    /** Ajoute un recul en degrés (pitch vers le haut, yaw signé). */
    public static void kick(float pitchDegrees, float yawDegrees) {
        pendingPitch += pitchDegrees;
        pendingYaw += yawDegrees;
        pendingRecovery += pitchDegrees * RECOVERY_RATIO;
        lastKickNanos = System.nanoTime();
    }

    public static void reset() {
        pendingPitch = pendingYaw = pendingRecovery = 0;
    }

    static void onRenderFrame(RenderFrameEvent.Pre event) {
        long nowNanos = System.nanoTime();
        double dt = lastFrameNanos < 0 ? 0 : Math.min((nowNanos - lastFrameNanos) / 1.0E9, 0.1);
        lastFrameNanos = nowNanos;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            reset();
            return;
        }
        if (mc.isPaused() || dt <= 0) return;

        float pitchStep = 0, yawStep = 0;

        if (Math.abs(pendingPitch) > EPSILON || Math.abs(pendingYaw) > EPSILON) {
            float k = (float) (1 - Math.exp(-dt / KICK_TIME_CONSTANT));
            pitchStep = pendingPitch * k;
            yawStep = pendingYaw * k;
            pendingPitch -= pitchStep;
            pendingYaw -= yawStep;
        } else {
            pendingPitch = pendingYaw = 0;
            double sinceKick = (nowNanos - lastKickNanos) / 1.0E9;
            if (pendingRecovery > EPSILON && sinceKick > RECOVERY_DELAY) {
                float r = (float) (1 - Math.exp(-dt / RECOVERY_TIME_CONSTANT));
                float recoveryStep = pendingRecovery * r;
                pendingRecovery -= recoveryStep;
                pitchStep -= recoveryStep;
            } else if (pendingRecovery <= EPSILON) {
                pendingRecovery = 0;
            }
        }

        if (pitchStep != 0 || yawStep != 0) {
            // turn() multiplie par 0.15, borne le pitch et décale aussi la rotation précédente
            // (pas d'interpolation parasite).
            player.turn(yawStep / 0.15, -pitchStep / 0.15);
        }
    }
}
