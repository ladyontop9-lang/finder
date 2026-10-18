package com.example.storagefinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Fullbright: pełna jasność (efekt widzenia w nocy nakładany tylko po stronie klienta). */
public final class Fullbright {
    private static boolean enabled = false;
    private static boolean added = false;

    private Fullbright() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean v) { enabled = v; }

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) {
            added = false;
            return;
        }
        if (enabled) {
            if (!p.hasEffect(MobEffects.NIGHT_VISION)) {
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0, false, false, false));
                added = true;
            }
        } else if (added) {
            p.removeEffect(MobEffects.NIGHT_VISION);
            added = false;
        }
    }
}
