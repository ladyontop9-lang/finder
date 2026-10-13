package com.example.storagefinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/** Freecam: kamera leci swobodnie, a postać stoi w miejscu. */
public final class StorageFreeCam {
    private static boolean active = false;
    private static Vec3d camPos = Vec3d.ZERO;
    private static Vec3d playerPos = Vec3d.ZERO;

    private static final double SPEED = 0.35;
    private static final double SPRINT_MULT = 2.5;

    private StorageFreeCam() {}

    public static boolean isActive() {
        return active;
    }

    public static Vec3d cameraPos() {
        return camPos;
    }

    /** Włącza/wyłącza freecam klawiszem. */
    public static void toggle(MinecraftClient mc) {
        if (mc.player == null || mc.gameRenderer == null) return;
        active = !active;
        if (active) {
            camPos = mc.gameRenderer.getCamera().getPos();
            playerPos = mc.player.getPos();
        }
    }

    public static void tick(MinecraftClient mc) {
        if (!active) return;
        if (mc.player == null || mc.world == null) {
            active = false;
            return;
        }

        // Postać stoi w miejscu: zerujemy prędkość i trzymamy ją na zapamiętanej pozycji.
        mc.player.setVelocity(Vec3d.ZERO);
        mc.player.setPosition(playerPos.x, playerPos.y, playerPos.z);

        // Kierunek według obrotu gracza (mysz obraca widok).
        Vec3d forward = mc.player.getRotationVector();
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();

        Vec3d move = Vec3d.ZERO;
        if (mc.options.forwardKey.isPressed()) move = move.add(forward);
        if (mc.options.backKey.isPressed())    move = move.subtract(forward);
        if (mc.options.rightKey.isPressed())   move = move.add(right);
        if (mc.options.leftKey.isPressed())    move = move.subtract(right);
        if (mc.options.jumpKey.isPressed())    move = move.add(0, 1, 0);
        if (mc.options.sneakKey.isPressed())   move = move.subtract(0, 1, 0);

        if (move.lengthSquared() > 0) {
            double speed = SPEED * (mc.options.sprintKey.isPressed() ? SPRINT_MULT : 1.0);
            camPos = camPos.add(move.normalize().multiply(speed));
        }
    }
}
