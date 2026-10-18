package com.example.storagefinder;

import net.minecraft.client.Minecraft;

/** Zoom: zmniejsza FOV na czas działania modułu i przywraca poprzednią wartość. */
public final class Zoom {
    private static boolean enabled = false;
    private static boolean applied = false;
    private static int savedFov = 70;

    private Zoom() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean v) { enabled = v; }

    public static void tick(Minecraft mc) {
        if (mc.options == null) return;
        if (enabled) {
            if (!applied) {
                savedFov = mc.options.fov().get();
                applied = true;
            }
            if (mc.options.fov().get() != StorageFinderConfig.zoomFov) {
                mc.options.fov().set(StorageFinderConfig.zoomFov);
            }
        } else {
            restore();
        }
    }

    /** Przywraca FOV (wywoływane też przy zamykaniu gry, żeby zoom się nie zapisał). */
    public static void restore() {
        Minecraft mc = Minecraft.getInstance();
        if (applied && mc.options != null) {
            mc.options.fov().set(savedFov);
        }
        applied = false;
    }
}
