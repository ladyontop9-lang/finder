package com.example.storagefinder;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * KillAura - recode. Architektura:
 *
 *   SZUKAJ CELU (sticky - nie przelatuje miedzy celami co tick)
 *        |
 *   CELUJ - punkt celu dryfuje powoli wewnatrz hitboxa (jak prawdziwy gracz
 *           scigajacy cel DHSetem), obrot wygadzony z easingiem + limit kat/tick
 *        |
 *   ATAK - dopiero gdy celownik logicznie naklada sie na hitbox
 *           (kat liczony z dystansu, nie z stalej tolerancji),
 *           czas ataku z losowym rozrzutem, okazjonalne "potkniecie" (miss)
 *
 * Bypassy: GCD fix, jitter aim, sticky target, human timing.
 */
public final class Aura {
    private static boolean enabled = false;

    // ---- stan celowania ----
    private static LivingEntity target;
    private static int targetTicks;                 // jak dlugo trzymamy aktualny cel
    private static float curYaw, curPitch;          // wewnetrzny "idealny" celownik
    private static boolean hasAim;

    // punkt w hitboxie, do ktorego celujemy (0..1) - dryfuje, nie teleportuje sie
    private static float aimU = 0.5f, aimV = 0.55f, aimW = 0.5f;
    private static float tgtU = 0.5f, tgtV = 0.55f, tgtW = 0.5f;
    private static long nextDrift = 0;

    // ---- timing atakow ----
    private static long nextAttack = 0;
    private static long lastSwing = 0;

    private Aura() {}

    public static boolean isEnabled() { return enabled; }

    public static void setEnabled(boolean v) {
        enabled = v;
        target = null;
        hasAim = false;
        nextAttack = 0;
    }

    public static void tick(Minecraft mc) {
        if (!enabled) return;

        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        MultiPlayerGameMode gm = mc.gameMode;
        if (p == null || level == null || gm == null || mc.screen != null || !p.isAlive()) return;
        if (p.isUsingItem()) return;

        // ======================= 1. WYBOR CELU (sticky) =======================
        LivingEntity best = findTarget(p, level);

        if (best != target) {
            // cel znika / wychodzi z zasiegu -> zmienc, ale nie 
            // skacz miedzy celami o rimilarnym dystansie (sticky ~10 tickow)
            if (target == null || !isValid(p, target) || targetTicks > 10) {
                target = best;
                targetTicks = 0;
                hasAim = false;      // zaczynamy g ladkie dochodzenie do nowego celu
            }
        }
        if (target == null) { pickDrift(); return; }
        targetTicks++;

        // ======================= 2. CELUJ =======================
        boolean onTarget = true;
        if (StorageFinderConfig.auraRotate && !Freecam.isActive()) {
            onTarget = aim(mc, p, target);
        }

        if (!onTarget) return;                    // Polar: nie bij, jesli nie celujesz

        // ======================= 3. ATAK =======================
        long now = System.currentTimeMillis();

        if (StorageFinderConfig.auraCooldown && p.getAttackStrengthScale(0.0f) < 0.93f) return;

        if (now < nextAttack) return;

        // czlowiek czasami sie potknie - ok. 4% atakow to miss (maly dystans = rzadziej)
        double dist = Math.sqrt(target.getBoundingBox().distanceToSqr(p.getEyePosition()));
        double missChance = 0.04 - Math.min(0.02, dist * 0.005);
        boolean miss = ThreadLocalRandom.current().nextDouble() < Math.max(0.01, missChance);

        gm.attack(p, target);                     // respawn: sam attack wystarczy; miss robimy "celowaniem obok"
        p.swing(InteractionHand.MAIN_HAND);
        lastSwing = now;

        // kolejny atak: bazowe opoznienie + losowy rozrzut (Random delay)
        double aps = Math.max(1, StorageFinderConfig.auraSpeed);
        long delay = Math.round(1000.0 / aps);
        if (StorageFinderConfig.auraRandomDelay) {
            delay = Math.round(delay * (0.85 + ThreadLocalRandom.current().nextDouble(0.3)));
        }
        nextAttack = now + Math.max(50, delay);

        // miss = od razu lekko "zjezdzamy" celownikiem z hitboxa (serwer widzi pudlo)
        if (miss) {
            tgtU = (float) (0.5 + ThreadLocalRandom.current().nextDouble(-0.35, 0.35));
        }
    }

    // ======================================================================
    //  WYSZUKIWANIE
    // ======================================================================

    private static LivingEntity findTarget(LocalPlayer p, ClientLevel level) {
        double range = Math.min(StorageFinderConfig.auraRange, 3.0);
        boolean playersOnly = StorageFinderConfig.auraPlayersOnly;
        boolean walls = StorageFinderConfig.auraWalls;
        Vec3 eye = p.getEyePosition();

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(range + 1.0))) {
            if (!isValid(p, e)) continue;
            if (playersOnly && !(e instanceof Player)) continue;
            if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;

            double d2 = e.getBoundingBox().distanceToSqr(eye);
            if (d2 > range * range) continue;
            if (!walls && !p.hasLineOfSight(e)) continue;

            // priorytet: aktualny cel dostaje bonus (sticky), zeby nie flickowac
            double score = d2;
            if (e == target) score *= 0.6;
            if (d2 < bestScore) {
                best = e;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean isValid(LocalPlayer p, LivingEntity e) {
        if (e == p || !e.isAlive() || e.isSpectator() || !e.isAttackable()) return false;
        if (e.hurtTime > 1) return false;          // i-frames - nie traconimy atakow
        return !e.isRemoved();
    }

    // ======================================================================
    //  CELUJ - gladki dryfujacy celownik
    // ======================================================================

    /** @return true gdy celownik "siedzi" na hiboksie celu. */
    private static boolean aim(Minecraft mc, LocalPlayer p, LivingEntity t) {
        pickDrift();

        // punkt celu w hitboxie (interpolowany - dryfuje gladko)
        float k = 0.12f;
        aimU += (tgtU - aimU) * k;
        aimV += (tgtV - aimV) * k;
        aimW += (tgtW - aimW) * k;

        AABB box = t.getBoundingBox();
        double px = box.minX + aimU * (box.maxX - box.minX);
        double py = box.minY + aimV * (box.maxY - box.minY);
        double pz = box.minZ + aimW * (box.maxZ - box.minZ);

        Vec3 eye = p.getEyePosition();
        double dx = px - eye.x, dy = py - eye.y, dz = pz - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float wantYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float wantPitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));

        if (!hasAim) {                             // pierwszy tick na nowym celu: start od aktualnego obrotu
            curYaw = p.getYRot();
            curPitch = p.getXRot();
            hasAim = true;
        }

        // wygadzenie: kawalek drogi na tick, ale zawsze conajmniej kilka stopni
        // (ludzie odbugowuja celu szybko i potem drobne poprawki - nie ladujemy idealnie)
        float maxTurn = (float) StorageFinderConfig.auraRotSpeed;
        float dYaw = wrapDegrees(wantYaw - curYaw);
        float dPitch = wantPitch - curPitch;

        float stepYaw = clampStep(dYaw, maxTurn);
        float stepPitch = clampStep(dPitch, maxTurn);

        // GCD fix: zaokraglenie do fizycznego kroku myszki (Grim: Timer/bleActivated na "niemozliwe" obroty)
        float step = mouseStep(mc);
        if (step > 0.0001f) {
            stepYaw = Math.round(stepYaw / step) * step;
            stepPitch = Math.round(stepPitch / step) * step;
        }

        curYaw = wrapDegrees(curYaw + stepYaw);
        curPitch = Mth.clamp(curPitch + stepPitch, -90f, 90f);
        p.setYRot(curYaw);
        p.setXRot(curPitch);

        // czy celownik NAKRYWA hitbox? tolerancja liczona z katowego rozmiaru hitboxa
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double halfW = Math.max(box.getXsize(), box.getZsize()) / 2.0 + 0.15;
        float tYaw = (float) Math.toDegrees(Math.atan2(halfW, Math.max(0.3, dist))) + 4f;
        float tPitch = tYaw + 4f;

        return Math.abs(dYaw) < Math.max(6f, tYaw) && Math.abs(dPitch) < Math.max(6f, tPitch);
    }

    /** Wybiera nowy punkt dryfu w hitboxie co 150-500 ms (zaleznie od Randomness). */
    private static void pickDrift() {
        long now = System.currentTimeMillis();
        if (now < nextDrift) return;
        nextDrift = now + 120 + ThreadLocalRandom.current().nextInt(350);

        double j = Math.max(0.02, StorageFinderConfig.auraJitter);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        tgtU = (float) clamp01(0.5 + r.nextDouble(-j, j));
        tgtV = (float) clamp01(0.35 + r.nextDouble(-j * 0.7, j * 0.7));  // cel lekko powyzej pasa
        tgtW = (float) clamp01(0.5 + r.nextDouble(-j, j));
    }

    private static double clamp01(double v) { return Math.max(0.2, Math.min(0.8, v)); }

    /** Ruch w strone celu: duze obroty sz blko (>30% drogi), male - wyg adnie wyhamowuja. */
    private static float clampStep(float delta, float maxTurn) {
        float a = Math.abs(delta);
        if (a <= 0.001f) return 0f;
        float step;
        if (a > maxTurn) {
            step = Math.min(maxTurn, a * 0.45f + maxTurn * 0.3f);   // szybkie dochodzenie do celu
        } else {
            step = Math.max(1.2f, a * 0.25f);                          // wyhamowanie koscia (drobne poprawki)
        }
        return Math.copySign(Math.min(step, maxTurn), delta);
    }

    private static float mouseStep(Minecraft mc) {
        double s = mc.options.sensitivity().get();
        if (s > 1.0) s /= 100.0;
        double f = s * 0.6 + 0.2;
        return (float) (f * f * f * 8.0 * 0.15);
    }

    private static float wrapDegrees(float a) {
        a %= 360f;
        if (a >= 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }
}
