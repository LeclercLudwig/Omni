package com.omni.weapon;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.omni.Omni;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Charge les armes depuis data/&lt;namespace&gt;/omni_weapons/*.json.
 * <p>
 * Le serveur fait autorité ; le client reçoit une copie via {@code SyncWeaponsPayload}
 * (connexion et /reload). Les deux copies sont séparées pour que le solo
 * (serveur intégré + client dans le même processus) se comporte comme le multi.
 */
public class WeaponManager extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "omni_weapons";

    private static Map<ResourceLocation, WeaponDefinition> serverWeapons = Map.of();
    private static Map<ResourceLocation, WeaponDefinition> clientWeapons = Map.of();

    public WeaponManager() {
        super(new Gson(), DIRECTORY);
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new WeaponManager());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, WeaponDefinition> loaded = new TreeMap<>();
        files.forEach((id, json) -> WeaponDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(err -> Omni.LOGGER.error("Arme {} invalide : {}", id, err))
                .ifPresent(def -> loaded.put(id, def)));
        serverWeapons = Collections.unmodifiableMap(loaded);
        Omni.LOGGER.info("{} arme(s) chargée(s)", loaded.size());
    }

    public static Map<ResourceLocation, WeaponDefinition> all(boolean clientSide) {
        return clientSide ? clientWeapons : serverWeapons;
    }

    public static Optional<WeaponDefinition> get(boolean clientSide, ResourceLocation id) {
        return Optional.ofNullable(id).map(all(clientSide)::get);
    }

    public static Optional<WeaponDefinition> get(Level level, ResourceLocation id) {
        return get(level.isClientSide, id);
    }

    public static void setClientWeapons(Map<ResourceLocation, WeaponDefinition> weapons) {
        clientWeapons = Collections.unmodifiableMap(new TreeMap<>(weapons));
    }
}
