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
 * Aura z cichą rotacją i większą czułością na krytyki.
 *
 * Tryby "Hit when": 0 Always, 1 On ground, 2 Can crit, 3 Ground or crit.
 *
 * Czułość krytyków: krytyk jest możliwy dopiero, gdy spadasz (fallDistance > 0). Aura nie czeka więc na sam
 * moment spadania, tylko PRZEWIDUJE go już w ostatnich tickach wznoszenia (prędkość pionowa bliska zera) i jest
 * gotowa uderzyć w pierwszym możliwym ticku. Im wyższa czułość, tym wcześniej się "napina". Dodatkowo
 * używa dokładnego progu ładowania ataku jak serwer (> 0.9) i może zwolnić sprint, który blokuje krytyki.
 *
 * START ticka: wybiera cel i ustawia obrót na cel (cicha rotacja).
 * END ticka: sprawdza prawdziwy stan, atakuje i przywraca Twój prawdziwy obrót.
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

    /** Warunki krytyka niezwiązane z samym spadaniem. */
    private static boolean critBase(LocalPlayer p) {
        return !p.onGround() && !p.onClimbable() && !p.isInWater()
                && !p.hasEffect(MobEffects.BLINDNESS) && !p.isPassenger() && !p.isSprinting();
    }

    /** Czy TY możesz teraz zadać krytyk (stan rzeczywisty — używany tuż przed atakiem). */
    private static boolean canCrit(LocalPlayer p) {
        return p.fallDistance > 0.0 && critBase(p);
    }

    /**
     * Przewidywanie na początku ticka: czy w tym ticku będziemy już spadać?
     * Tolerancja prędkości pionowej rośnie z czułością (1..5 => 0.02..0.10).
     */
    private static boolean canCritSoon(LocalPlayer p) {
        if (p.fallDistance > 0.0) return critBase(p);
        double tol = 0.02 * Math.max(1.0, StorageFinderConfig.auraCritSens);
        return critBase(p) && p.getDeltaMovement().y <= tol;
    }

    private static boolean modeOkStart(LocalPlayer p) {
        return switch (StorageFinderConfig.auraHitMode) {
            case 1 -> p.onGround();
            case 2 -> canCritSoon(p);
            case 3 -> p.onGround() || canCritSoon(p);
            default -> true;
        };
    }

    private static boolean modeOkEnd(LocalPlayer p) {
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

    private static LivingEntity findTarget(LocalPlayer p, ClientLevel level) {
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
        return best;
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
        int mode = StorageFinderConfig.auraHitMode;
        boolean critMode = mode == 2 || mode == 3;

        // sprint blokuje krytyki — w trybach z krytykiem można go zwolnić
        if (critMode && StorageFinderConfig.auraStopSprint && !p.onGround()) {
            mc.options.keySprint.setDown(false);
            p.setSprinting(false);
        }

        // przewidujemy ładowanie ataku na koniec tego ticka; serwer wymaga > 0.9
        if (StorageFinderConfig.auraCooldown && p.getAttackStrengthScale(1.5f) < 0.91f) return;

        if (!modeOkStart(p)) return;

        LivingEntity best = findTarget(p, level);
        if (best == null) return;
        pending = best;

        if (StorageFinderConfig.auraSilent && !Freecam.isActive()) {
            Vec3 eye = p.getEyePosition();
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
            if (pending != null && enabled && p != null && ready(mc) && modeOkEnd(p)
                    && (!StorageFinderConfig.auraCooldown || p.getAttackStrengthScale(0.5f) > 0.9f)) {
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
