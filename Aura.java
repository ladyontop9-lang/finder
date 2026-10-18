package com.example.storagefinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Aura z cichą rotacją.
 * START ticka: wybiera cel i ustawia obrót gracza na cel (serwer dostanie go w pakiecie pozycji z tego ticka).
 * END ticka: atakuje i natychmiast przywraca Twój prawdziwy obrót. Ponieważ klatki rysują się między tickami,
 * kamera nigdy nie widzi tego obrotu — nie musisz patrzeć na przeciwnika.
 */
public final class Aura {
    private static boolean enabled = false;
    private static int cooldown = 0;

    private static LivingEntity pending = null;
    private static boolean rotated = false;
    private static float savedYaw, savedPitch, savedYawO, savedPitchO;

    private Aura() {}

    public static boolean isEnabled() { return enabled; }

    public static void setEnabled(boolean v) {
        enabled = v;
        cooldown = 0;
        pending = null;
    }

    private static boolean ready(Minecraft mc) {
        LocalPlayer p = mc.player;
        return p != null && mc.level != null && mc.gameMode != null && mc.screen == null
                && p.isAlive() && !p.isUsingItem();
    }

    /** Czy TY możesz teraz zadać krytyk (spadasz, nie sprintujesz, nie jesteś w wodzie itd.). */
    private static boolean canCrit(LocalPlayer p) {
        return p.fallDistance > 0.0 && !p.onGround() && !p.onClimbable() && !p.isInWater()
                && !p.hasEffect(MobEffects.BLINDNESS) && !p.isPassenger() && !p.isSprinting();
    }

    /** Warunek "Hit when": zawsze / na ziemi / gdy może być krytyk / ziemia lub krytyk. */
    private static boolean modeOk(LocalPlayer p) {
        return switch (StorageFinderConfig.auraHitMode) {
            case 1 -> p.onGround();
            case 2 -> canCrit(p);
            case 3 -> p.onGround() || canCrit(p);
            default -> true;
        };
    }

    private static boolean validTarget(LocalPlayer p, LivingEntity e, Vec3 eye, double r2, boolean playersOnly, boolean walls) {
        if (e == p || !e.isAlive() || e.isSpectator() || !e.isAttackable()) return false;
        if (e.hurtTime > 1) return false;                                   // jeszcze ma i-frames
        if (playersOnly && !(e instanceof Player)) return false;
        if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) return false;
        if (e.getBoundingBox().distanceToSqr(eye) > r2) return false;       // dystans jak na serwerze
        return walls || p.hasLineOfSight(e);
    }

    // ---------------------------------------------------------------- START ticka

    public static void tickStart(Minecraft mc) {
        pending = null;
        rotated = false;
        if (!enabled || !ready(mc)) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (!modeOk(p)) return;

        // na początku ticka licznik ataku jest jeszcze o 1 tick do tyłu -> przewidujemy (+1.0)
        if (StorageFinderConfig.auraCooldown && p.getAttackStrengthScale(1.0f) < 0.95f) return;

        double range = StorageFinderConfig.auraRange;
        double r2 = range * range;
        boolean playersOnly = StorageFinderConfig.auraPlayersOnly;
        boolean walls = StorageFinderConfig.auraWalls;
        Vec3 eye = p.getEyePosition();

        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range + 1.0))) {
            if (!validTarget(p, e, eye, r2, playersOnly, walls)) continue;
            double d2 = e.getBoundingBox().distanceToSqr(eye);
            if (d2 < bestD) {
                best = e;
                bestD = d2;
            }
        }
        if (best == null) return;
        pending = best;

        if (StorageFinderConfig.auraSilent && !Freecam.isActive()) {
            AABB bb = best.getBoundingBox();
            Vec3 c = bb.getCenter();
            double j = Math.max(0.0, StorageFinderConfig.auraJitter);
            double ax = c.x + (Math.random() * 2 - 1) * j * bb.getXsize() * 0.5;
            double ay = c.y + (Math.random() * 2 - 1) * j * bb.getYsize() * 0.5;
            double az = c.z + (Math.random() * 2 - 1) * j * bb.getZsize() * 0.5;

            double dx = ax - eye.x, dy = ay - eye.y, dz = az - eye.z;
            double horiz = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));

            savedYaw = p.getYRot();
            savedPitch = p.getXRot();
            savedYawO = p.yRotO;
            savedPitchO = p.xRotO;
            rotated = true;

            p.setYRot(yaw);
            p.setXRot(pitch);
            p.yRotO = yaw;
            p.xRotO = pitch;
        }
    }

    // ---------------------------------------------------------------- END ticka

    public static void tickEnd(Minecraft mc) {
        LocalPlayer p = mc.player;
        try {
            if (pending != null && enabled && p != null && ready(mc) && modeOk(p)) {
                Vec3 eye = p.getEyePosition();
                double range = StorageFinderConfig.auraRange;
                if (validTarget(p, pending, eye, range * range,
                        StorageFinderConfig.auraPlayersOnly, StorageFinderConfig.auraWalls)) {
                    MultiPlayerGameMode gm = mc.gameMode;
                    gm.attack(p, pending);
                    p.swing(InteractionHand.MAIN_HAND);

                    int base = Math.max(0, Math.round(20f / (float) StorageFinderConfig.auraSpeed) - 1);
                    int extra = StorageFinderConfig.auraRandomDelay ? (int) (Math.random() * 3) : 0;
                    cooldown = base + extra;
                }
            }
        } finally {
            if (rotated && p != null) {
                p.setYRot(savedYaw);
                p.setXRot(savedPitch);
                p.yRotO = savedYawO;
                p.xRotO = savedPitchO;
            }
            pending = null;
            rotated = false;
        }
    }
}
