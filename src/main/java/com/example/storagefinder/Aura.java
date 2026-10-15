package com.example.storagefinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Automatycznie atakuje najbliższego gracza w zasięgu. */
public final class Aura {
    private static boolean enabled = false;
    private static int cooldown = 0;

    private Aura() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean v) { enabled = v; cooldown = 0; }

    public static void tick(Minecraft mc) {
        if (!enabled) return;

        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        MultiPlayerGameMode gm = mc.gameMode;
        if (p == null || level == null || gm == null || mc.screen != null || !p.isAlive()) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (p.getAttackStrengthScale(0.5f) < 0.9f) return;

        double range = StorageFinderConfig.auraRange;
        boolean playersOnly = StorageFinderConfig.auraPlayersOnly;

        LivingEntity best = null;
        float bestDist = Float.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range))) {
            if (e == p || !e.isAlive() || e.isSpectator() || !e.isAttackable()) continue;
            if (playersOnly && !(e instanceof Player)) continue;
            float d = p.distanceTo(e);
            if (d > range || d >= bestDist) continue;
            best = e;
            bestDist = d;
        }
        if (best == null) return;

        gm.attack(p, best);
        p.swing(InteractionHand.MAIN_HAND);
        cooldown = Math.max(0, Math.round(20f / (float) StorageFinderConfig.auraSpeed) - 1);
    }
}
