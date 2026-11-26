package com.omni.client;

import com.omni.entity.BoltEntity;
import com.omni.registry.OmniComponents;
import com.omni.weapon.BlasterItem;
import com.omni.weapon.BlasterLogic;
import com.omni.network.ReloadPayload;
import com.omni.weapon.BlasterState;
import com.omni.weapon.MagazineState;
import com.omni.weapon.WeaponDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Prédiction du tir chez le tireur : projectiles, son et chaleur apparaissent dès le clic,
 * sans attendre l'aller-retour réseau. Le serveur reste seul juge des dégâts.
 * <ul>
 *   <li>Les projectiles prédits sont des {@link BoltEntity} purement locales (identifiants
 *       négatifs, jamais utilisés par le serveur). Ils suivent les mêmes trajectoires que ceux
 *       du serveur grâce à la graine de dispersion partagée.</li>
 *   <li>Les vrais projectiles du joueur local sont masqués par {@link BoltRenderer}.</li>
 *   <li>La chaleur est appliquée tout de suite sur l'item local ; la synchro serveur la corrige
 *       ensuite (et force un renvoi si le serveur a refusé le tir).</li>
 * </ul>
 */
public final class ClientPrediction {
    private static int nextClientEntityId = -1_000_000;

    private ClientPrediction() {}

    public static void predictShot(LocalPlayer player, ClientLevel level, ItemStack stack, WeaponDefinition def,
                                   boolean aiming, int seed) {
        for (Vec3 direction : BlasterLogic.shotDirections(player.getLookAngle(), def, aiming, seed)) {
            BoltEntity bolt = BoltEntity.predicted(level, player, def);
            bolt.shoot(direction.x, direction.y, direction.z, def.boltSpeed(), 0);
            bolt.setId(nextClientEntityId--);
            level.addEntity(bolt);
        }

        long now = level.getGameTime();
        BlasterState next = BlasterItem.state(stack).afterShot(def, now);
        stack.set(OmniComponents.STATE.get(), next);
        if (def.magazine().isPresent()) {
            stack.set(OmniComponents.MAGAZINE.get(), BlasterItem.magazine(stack).afterShot(def, now));
        }

        float pitch = 0.9F + player.getRandom().nextFloat() * 0.2F;
        level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
                SoundEvent.createVariableRangeEvent(def.fireSound()), SoundSource.PLAYERS, 1.0F, pitch, false);
        if (next.overheated()) {
            level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.2F, false);
        }
    }

    /**
     * Rechargement demandé par le joueur (touche ou chargeur vide). Prédit localement puis
     * envoyé au serveur, qui consomme la munition (et corrige l'item s'il refuse).
     *
     * @param feedback afficher « plus de munitions » et un clic à vide si impossible
     */
    public static void requestReload(LocalPlayer player, ClientLevel level, ItemStack stack, WeaponDefinition def, boolean feedback) {
        long now = level.getGameTime();
        MagazineState magazine = BlasterItem.magazine(stack);
        if (!magazine.canStartReload(def, now)) return;

        boolean hasAmmo = player.hasInfiniteMaterials() || BlasterLogic.countAmmo(player, def.magazine().get()) > 0;
        if (!hasAmmo) {
            if (feedback) {
                player.displayClientMessage(Component.translatable("message.omni.no_ammo"), true);
                level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
                        SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.6F, false);
            }
            return;
        }

        stack.set(OmniComponents.MAGAZINE.get(), magazine.startReload(def, now));
        level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
                SoundEvents.CROSSBOW_LOADING_START.value(), SoundSource.PLAYERS, 0.9F, 0.8F, false);
        PacketDistributor.sendToServer(ReloadPayload.INSTANCE);
    }
}
