package com.omni.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Positions approximatives du canon de l'arme, utilisées uniquement pour l'affichage : le tir
 * physique part de l'œil (précision au réticule), mais son rendu démarre au canon puis
 * rejoint la vraie trajectoire (voir {@link BoltEntity#visualOffset}).
 */
public final class MuzzlePositions {
    private MuzzlePositions() {}

    /** Repère de visée : avant, droite, haut (normalisés). */
    public record Frame(Vec3 forward, Vec3 right, Vec3 up) {
        public Vec3 at(Vec3 origin, double forwardDist, double rightDist, double upDist) {
            return origin.add(forward.scale(forwardDist)).add(right.scale(rightDist)).add(up.scale(upDist));
        }
    }

    public static Frame frame(LivingEntity entity) {
        Vec3 forward = entity.getViewVector(1.0F).normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-6) { // regard vertical : on se repère sur l'orientation du corps
            float yaw = (float) Math.toRadians(entity.getYRot());
            right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        }
        right = right.normalize();
        Vec3 up = right.cross(forward).normalize();
        return new Frame(forward, right, up);
    }

    /** Canon d'une arme épaulée vue de l'extérieur (3e personne : autres joueurs, soldats). */
    public static Vec3 thirdPerson(LivingEntity entity) {
        return frame(entity).at(entity.getEyePosition(), 0.9, 0.3, -0.3);
    }
}
