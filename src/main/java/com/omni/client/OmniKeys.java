package com.omni.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Touches du mod (modifiables dans Options → Commandes). */
public final class OmniKeys {
    public static final KeyMapping RELOAD = new KeyMapping("key.omni.reload", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.omni");

    private OmniKeys() {}

    static void register(RegisterKeyMappingsEvent event) {
        event.register(RELOAD);
    }
}
