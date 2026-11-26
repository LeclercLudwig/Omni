package com.omni.network;

import com.omni.weapon.BlasterLogic;
import com.omni.weapon.WeaponManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

public final class OmniNetwork {
    private OmniNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(FirePayload.TYPE, FirePayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        BlasterLogic.tryFire(player, payload.aiming(), payload.seed(), payload.chargeTicks());
                    }
                }));

        registrar.playToServer(ChargePayload.TYPE, ChargePayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        BlasterLogic.startCharge(player);
                    }
                }));

        registrar.playToServer(VentPayload.TYPE, VentPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        BlasterLogic.tryVent(player);
                    }
                }));

        registrar.playToServer(ReloadPayload.TYPE, ReloadPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        BlasterLogic.tryReload(player);
                    }
                }));

        registrar.playToClient(SyncWeaponsPayload.TYPE, SyncWeaponsPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> WeaponManager.setClientWeapons(payload.weapons())));
    }

    /** Envoyé à la connexion d'un joueur et à chaque /reload. */
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        SyncWeaponsPayload payload = new SyncWeaponsPayload(WeaponManager.all(false));
        List<ServerPlayer> targets = event.getPlayer() != null
                ? List.of(event.getPlayer())
                : event.getPlayerList().getPlayers();
        // Ignore les connexions qui n'ont pas négocié le canal (joueurs factices, clients sans le mod).
        targets.stream()
                .filter(player -> player.connection.hasChannel(SyncWeaponsPayload.TYPE))
                .forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }
}
