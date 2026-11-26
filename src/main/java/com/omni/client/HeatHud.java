package com.omni.client;

import com.omni.weapon.BlasterItem;
import com.omni.weapon.BlasterState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;

/** Jauge de chaleur sous le réticule. */
public final class HeatHud {
    private static final int WIDTH = 32;
    private static final int HEIGHT = 3;

    private HeatHud() {}

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) return;

        ItemStack stack = mc.player.getMainHandItem();
        BlasterItem.definition(true, stack).ifPresent(def -> {
            long now = mc.level.getGameTime();
            BlasterState state = BlasterItem.state(stack);
            float ratio = state.heatAt(def, now) / def.heat().max();
            boolean overheated = state.overheatedAt(def, now);
            if (ratio <= 0 && !overheated) return;

            int x = graphics.guiWidth() / 2 - WIDTH / 2;
            int y = graphics.guiHeight() / 2 + 10;
            int color = overheated
                    ? (now / 4 % 2 == 0 ? 0xFFFF2020 : 0xFF801010)
                    : FastColor.ARGB32.lerp(ratio, 0xFFFFD040, 0xFFFF4020);

            graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, 0x90000000);
            graphics.fill(x, y, x + Math.round(WIDTH * ratio), y + HEIGHT, color);
        });
    }
}
