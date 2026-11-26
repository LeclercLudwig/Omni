package com.omni.weapon;

import com.omni.entity.BoltEntity;
import com.omni.registry.OmniComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Tir côté serveur : c'est ici que tout est validé (cadence, surchauffe). */
public final class BlasterLogic {
    private BlasterLogic() {}

    public static void tryFire(ServerPlayer player, boolean aiming) {
        if (player.isSpectator() || !player.isAlive()) return;

        ItemStack stack = player.getMainHandItem();
        WeaponDefinition def = BlasterItem.definition(false, stack).orElse(null);
        if (def == null) return;

        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        BlasterState state = BlasterItem.state(stack);
        if (!state.canFire(def, now)) return;

        Vec3 look = player.getLookAngle();
        float spread = aiming ? def.spread().aim() : def.spread().hip();
        for (int i = 0; i < def.projectilesPerShot(); i++) {
            BoltEntity bolt = new BoltEntity(level, player, def);
            bolt.shoot(look.x, look.y, look.z, def.boltSpeed(), spread);
            level.addFreshEntity(bolt);
        }

        BlasterState next = state.afterShot(def, now);
        stack.set(OmniComponents.STATE.get(), next);

        float pitch = 0.9F + level.random.nextFloat() * 0.2F;
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                SoundEvent.createVariableRangeEvent(def.fireSound()), SoundSource.PLAYERS, 1.0F, pitch);
        if (next.overheated()) {
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.2F);
        }
    }
}
