package com.example.storagefinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Auto Sprint: sprint włącza się sam, gdy idziesz do przodu. */
public final class Sprint {
    private static boolean enabled = false;

    private Sprint() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean v) { enabled = v; }

    public static void tick(Minecraft mc) {
        if (!enabled || Freecam.isActive()) return;
        LocalPlayer p = mc.player;
        if (p == null || mc.screen != null) return;
        if (mc.options.keyUp.isDown() && !mc.options.keyShift.isDown()) {
            mc.options.keySprint.setDown(true);
        }
    }
}
