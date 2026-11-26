package com.omni.client;

import com.omni.registry.OmniSounds;
import com.omni.entity.BoltEntity;
import com.omni.entity.MuzzlePositions;
import com.omni.registry.OmniComponents;
import com.omni.weapon.BlasterItem;
import com.omni.weapon.BlasterLogic;
import com.omni.network.ReloadPayload;
import com.omni.salvage.Damage;
import com.omni.network.VentPayload;
import com.omni.weapon.BlasterState;
import com.omni.weapon.MagazineState;
import com.omni.weapon.WeaponDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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

    /** @return false si l'arme s'est enrayée (aucun tir, pas de recul) */
    public static boolean predictShot(LocalPlayer player, ClientLevel level, ItemStack stack, WeaponDefinition def,
                                      boolean aiming, int seed, float chargeFactor) {
        if (Damage.isDamaged(stack) && Damage.isMisfire(seed)) {
            long now = level.getGameTime();
            stack.set(OmniComponents.STATE.get(), BlasterItem.state(stack).afterShot(def, now));
            if (def.magazine().isPresent()) {
                stack.set(OmniComponents.MAGAZINE.get(), BlasterItem.magazine(stack).afterShot(def, now));
            }
            level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
                    OmniSounds.DRY_FIRE.get(), SoundSource.PLAYERS, 0.9F, 0.8F, false);
            return false;
        }
        for (Vec3 direction : BlasterLogic.shotDirections(player.getLookAngle(), def, aiming, seed)) {
            BoltEntity bolt = BoltEntity.predicted(level, player, def, chargeFactor);
            bolt.shoot(direction.x, direction.y, direction.z, def.boltSpeed(), 0);
            bolt.setVisualStart(muzzle(player, def));
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
                    OmniSounds.OVERHEAT.get(), SoundSource.PLAYERS, 0.8F, 1.0F, false);
        }
        return true;
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
                        OmniSounds.DRY_FIRE.get(), SoundSource.PLAYERS, 0.7F, 1.0F, false);
            }
            return;
        }

        stack.set(OmniComponents.MAGAZINE.get(), magazine.startReload(def, now));
        level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
                OmniSounds.RELOAD_START.get(), SoundSource.PLAYERS, 0.9F, 1.0F, false);
        PacketDistributor.sendToServer(ReloadPayload.INSTANCE);
    }

    /**
     * Position monde du canon tel qu'il apparaît à l'écran.
     * <p>
     * En 1re personne, l'arme est dessinée dans un repère « main » (champ de vision fixe de 70°)
     * à environ (0,56 ; -0,40 ; 1,4) de la caméra, ramené au centre quand on vise (voir
     * BlasterClientExtensions). Pour que le tir apparaisse au même endroit de l'écran alors que le
     * monde utilise le champ de vision du joueur (et le zoom de visée), on corrige les décalages
     * latéraux par le rapport des tangentes.
     */
    private static Vec3 muzzle(LocalPlayer player, WeaponDefinition def) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.getCameraType().isFirstPerson()) return MuzzlePositions.thirdPerson(player);

        float aim = ClientWeaponControls.aimProgress(1.0F);
        double worldFov = mc.options.fov().get() * Mth.lerp(aim, 1.0F, def.aimFovMultiplier());
        double k = Math.tan(Math.toRadians(worldFov) / 2) / Math.tan(Math.toRadians(70) / 2);

        double right = Mth.lerp(aim, 0.56, 0.0) * k;
        double up = Mth.lerp(aim, -0.40, -0.28) * k;
        double forward = Mth.lerp(aim, 1.4, 1.33);
        return MuzzlePositions.frame(player).at(player.getEyePosition(), forward, right, up);
    }

    /** Ventilation prédite localement (sans marge), puis confirmée ou corrigée par le serveur. */
    public static void requestVent(LocalPlayer player, ClientLevel level, ItemStack stack) {
        if (BlasterLogic.tryVent(player, stack, 0, null) != BlasterLogic.VentOutcome.NONE) {
            PacketDistributor.sendToServer(VentPayload.INSTANCE);
        }
    }
}
