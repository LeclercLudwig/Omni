package com.omni.client;

import com.omni.weapon.BlasterItem;
import com.omni.weapon.BlasterLogic;
import com.omni.weapon.BlasterState;
import com.omni.weapon.EnergyBladeItem;
import com.omni.weapon.GuardState;
import com.omni.weapon.MagazineState;
import com.omni.weapon.WeaponDefinition;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;

/** Sous le réticule : chaleur et munitions du blaster, ou endurance de garde de la lame. */
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
            float partialTick = delta.getGameTimeDeltaPartialTick(false);
            renderAmmo(graphics, mc, stack, def, now, partialTick);
            renderCharge(graphics, now, partialTick);

            BlasterState state = BlasterItem.state(stack);
            float ratio = state.heatAt(def, now) / def.heat().max();
            boolean overheated = state.overheatedAt(def, now);
            if (ratio <= 0 && !overheated) return;

            int color = overheated
                    ? (now / 4 % 2 == 0 ? 0xFFFF2020 : 0xFF801010)
                    : FastColor.ARGB32.lerp(ratio, 0xFFFFD040, 0xFFFF4020);
            bar(graphics, ratio, color);
            if (overheated) renderVentWindow(graphics, mc, state.inVentWindow(def, now, 0));
        });
    }

    /** Munitions sous la jauge : « 24/30 ×5 » (×N = munitions en réserve), ou barre de rechargement. */
    private static void renderAmmo(GuiGraphics graphics, Minecraft mc, ItemStack stack, WeaponDefinition def,
                                   long now, float partialTick) {
        if (def.magazine().isEmpty()) return;
        WeaponDefinition.Magazine magazine = def.magazine().get();
        MagazineState state = BlasterItem.magazine(stack);
        int y = graphics.guiHeight() / 2 + 16;

        if (state.isReloading(def, now)) {
            int x = graphics.guiWidth() / 2 - WIDTH / 2;
            graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + 2, 0x90000000);
            graphics.fill(x, y, x + Math.round(WIDTH * state.reloadProgress(def, now, partialTick)), y + 1, 0xFFE8E8E8);
            return;
        }

        int rounds = state.roundsAt(def, now);
        String text = rounds + "/" + magazine.capacity();
        if (!mc.player.hasInfiniteMaterials()) text += "  ×" + BlasterLogic.countAmmo(mc.player, magazine);
        int color = rounds == 0 ? 0xFFFF5050 : rounds <= magazine.capacity() / 4 ? 0xFFFFB040 : 0xFFE8E8E8;
        graphics.drawString(mc.font, text, graphics.guiWidth() / 2 - mc.font.width(text) / 2, y, color, true);
    }

    /** Charge du tir (mode "charged") au-dessus du réticule ; clignote quand elle est pleine. */
    private static void renderCharge(GuiGraphics graphics, long now, float partialTick) {
        if (!ClientWeaponControls.isCharging()) return;
        float charge = ClientWeaponControls.chargeProgress(partialTick);
        int x = graphics.guiWidth() / 2 - WIDTH / 2;
        int y = graphics.guiHeight() / 2 - 14;
        int color = charge >= 1 ? (now / 3 % 2 == 0 ? 0xFFFFFFFF : 0xFF9FE8FF) : FastColor.ARGB32.lerp(charge, 0xFF5FA8FF, 0xFF9FE8FF);
        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, 0x90000000);
        graphics.fill(x, y, x + Math.round(WIDTH * charge), y + HEIGHT, color);
    }

    /**
     * Surchauffe : cadre de la fenêtre de ventilation sur la jauge (vert quand la chaleur y est)
     * et rappel de la touche.
     */
    private static void renderVentWindow(GuiGraphics graphics, Minecraft mc, boolean inWindow) {
        int x = graphics.guiWidth() / 2 - WIDTH / 2;
        int y = graphics.guiHeight() / 2 + 10;
        int x0 = x + Math.round(WIDTH * BlasterState.VENT_WINDOW_MIN);
        int x1 = x + Math.round(WIDTH * BlasterState.VENT_WINDOW_MAX);
        int frame = inWindow ? 0xFF40FF70 : 0xFFF0F0F0;
        graphics.fill(x0, y - 2, x1, y - 1, frame);
        graphics.fill(x0, y + HEIGHT + 1, x1, y + HEIGHT + 2, frame);
        graphics.fill(x0 - 1, y - 2, x0, y + HEIGHT + 2, frame);
        graphics.fill(x1, y - 2, x1 + 1, y + HEIGHT + 2, frame);

        Component hint = Component.translatable("hud.omni.vent", OmniKeys.RELOAD.getTranslatedKeyMessage());
        graphics.drawString(mc.font, hint, graphics.guiWidth() / 2 - mc.font.width(hint) / 2, y + 17, // sous les munitions
                inWindow ? 0xFF40FF70 : 0xFFF0F0F0, true);
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
