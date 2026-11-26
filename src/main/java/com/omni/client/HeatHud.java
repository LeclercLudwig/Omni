package com.omni.client;

import com.omni.weapon.BlasterItem;
import com.omni.weapon.BlasterState;
import com.omni.weapon.EnergyBladeItem;
import com.omni.weapon.GuardState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;

/** Jauges sous le réticule : chaleur du blaster, ou endurance de garde de la lame. */
public final class HeatHud {
    private static final int WIDTH = 32;
    private static final int HEIGHT = 3;

    private HeatHud() {}

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) return;

        long now = mc.level.getGameTime();
        ItemStack stack = mc.player.getMainHandItem();

        if (stack.getItem() instanceof EnergyBladeItem) {
            renderGuard(graphics, EnergyBladeItem.guard(stack), now);
            return;
        }

        BlasterItem.definition(true, stack).ifPresent(def -> {
            BlasterState state = BlasterItem.state(stack);
            float ratio = state.heatAt(def, now) / def.heat().max();
            boolean overheated = state.overheatedAt(def, now);
            if (ratio <= 0 && !overheated) return;

            int color = overheated
                    ? (now / 4 % 2 == 0 ? 0xFFFF2020 : 0xFF801010)
                    : FastColor.ARGB32.lerp(ratio, 0xFFFFD040, 0xFFFF4020);
            bar(graphics, ratio, color);
        });
    }

    private static void renderGuard(GuiGraphics graphics, GuardState guard, long now) {
        float ratio = guard.staminaAt(now) / GuardState.MAX;
        boolean broken = guard.isBrokenAt(now);
        if (ratio >= 1 && !broken) return;

        int color;
        if (broken) color = now / 4 % 2 == 0 ? 0xFFFF2020 : 0xFF801010;
        else if (guard.staminaAt(now) < GuardState.MIN_TO_RAISE) color = 0xFFFF9020;
        else color = 0xFF8FE8FF;
        bar(graphics, ratio, color);
    }

    private static void bar(GuiGraphics graphics, float ratio, int color) {
        int x = graphics.guiWidth() / 2 - WIDTH / 2;
        int y = graphics.guiHeight() / 2 + 10;
        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, 0x90000000);
        graphics.fill(x, y, x + Math.round(WIDTH * Math.max(0, ratio)), y + HEIGHT, color);
    }
}
