package com.example.storagefinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;

/**
 * Skanowanie z optymalizacją:
 *  - block entity (skrzynie, hoppery, spawnery) co 2 ticki,
 *  - bloki szukane po stanie (pistony, ametyst) skanowane przyrostowo, chunk po chunku, z budżetem czasu
 *    ~1.5 ms na tick i cache per chunk,
 *  - encje (gracze / moby) co 2 ticki.
 */
public final class StorageScanner {
    public record Entry(BlockPos pos, StorageType type) {}

    /** Chunk z ogromną ilością wyrośniętego ametystu — rysowany na czerwono. */
    public record SusChunk(int cx, int cz, int count, int minY, int maxY) {}

    public enum Kind { PLAYER, HOSTILE, PASSIVE }

    public record Target(LivingEntity entity, Kind kind) {}

    private static final int N = StorageType.values().length;
    private static final long BUDGET_NANOS = 1_500_000L;

    private static volatile List<Entry> found = List.of();
    private static volatile List<Target> targets = List.of();
    private static volatile List<SusChunk> sus = List.of();
    private static final Set<Long> notified = new HashSet<>();
    private static boolean lastSusOn = false;
    private static int lastSusThr = -1;
    private static List<Entry> beEntries = List.of();
    private static List<Entry> blockEntries = List.of();
    private static int[] beCounts = new int[N];
    private static int[] blockCounts = new int[N];

    private static final Map<Long, List<Entry>> blockCache = new HashMap<>();
    private static Map<Block, StorageType> blockTypes = new HashMap<>();
    private static long[] queue = new long[0];
    private static int qIndex = 0;
    private static boolean startCycleNow = true;

    private static int tickCounter = 0;
    private static volatile boolean rescan = true;

    private StorageScanner() {}

    public static void requestRescan() { rescan = true; }

    public static List<Entry> found() { return found; }

    public static List<Target> targets() { return targets; }

    public static List<SusChunk> sus() { return sus; }

    public static int count(StorageType t) {
        return beCounts[t.ordinal()] + blockCounts[t.ordinal()];
    }

    private static void clearStorage() {
        found = List.of();
        sus = List.of();
        beEntries = List.of();
        blockEntries = List.of();
        beCounts = new int[N];
        blockCounts = new int[N];
        blockCache.clear();
        qIndex = queue.length;
        startCycleNow = true;
    }

    public static void tick(Minecraft mc) {
        if (!Krux.licensed()) return;
        tickCounter++;
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            if (!found.isEmpty() || !targets.isEmpty()) {
                clearStorage();
                targets = List.of();
            }
            notified.clear();
            return;
        }

        boolean force = rescan;
        rescan = false;

        // ---------------- Storage Finder + Sus Chunk (wspólny skaner bloków) ----------------
        boolean wantSf = StorageFinderConfig.enabled;
        boolean wantSus = StorageFinderConfig.susEnabled;
        if (wantSf || wantSus) {
            if (force) {
                blockCache.clear();
                startCycleNow = true;
                rebuildBlockTypes();
            }
            boolean changed = false;

            if (wantSf && (force || tickCounter % 2 == 0)) {
                scanBlockEntities(mc, level);
                changed = true;
            } else if (!wantSf && !beEntries.isEmpty()) {
                beEntries = List.of();
                beCounts = new int[N];
                changed = true;
            }
            boolean blocksChanged = false;
            if (!blockTypes.isEmpty()) {
                blocksChanged = stepBlocks(mc, level);
                changed |= blocksChanged;
            } else if (!blockEntries.isEmpty()) {
                blockEntries = List.of();
                blockCounts = new int[N];
                changed = true;
                blocksChanged = true;
            }

            boolean susDirty = StorageFinderConfig.susEnabled != lastSusOn || StorageFinderConfig.susThreshold != lastSusThr;
            if (blocksChanged || susDirty) rebuildSus(mc);

            if (changed) {
                if (wantSf) {
                    List<Entry> merged = new ArrayList<>(beEntries.size() + blockEntries.size());
                    merged.addAll(beEntries);
                    merged.addAll(blockEntries);
                    found = merged;
                } else {
                    found = List.of();
                }
            }
        } else if (!found.isEmpty() || !sus.isEmpty()) {
            clearStorage();
        }

        // ---------------- ESP / Tracers (encje) ----------------
        if (StorageFinderConfig.espEnabled || StorageFinderConfig.tracersEnabled) {
            if (tickCounter % 2 == 0) scanEntities(mc, level);
        } else if (!targets.isEmpty()) {
            targets = List.of();
        }
    }

    // ------------------------------------------------------------------ block entities

    private static void scanBlockEntities(Minecraft mc, ClientLevel level) {
        List<Entry> list = new ArrayList<>();
        int[] cnt = new int[N];

        ChunkPos center = mc.player.chunkPosition();
        int r = Math.min(StorageFinderConfig.radiusChunks, mc.options.renderDistance().get());

        for (int cx = center.x - r; cx <= center.x + r; cx++) {
            for (int cz = center.z - r; cz <= center.z + r; cz++) {
                if (!(level.getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    StorageType t = typeOf(be);
                    if (t == null || !StorageFinderConfig.isOn(t)) continue;
                    list.add(new Entry(be.getBlockPos(), t));
                    cnt[t.ordinal()]++;
                }
            }
        }
        beEntries = list;
        beCounts = cnt;
    }

    private static StorageType typeOf(BlockEntity be) {
        if (be instanceof ShulkerBoxBlockEntity) return StorageType.SHULKER;
        if (be instanceof EnderChestBlockEntity) return StorageType.ENDER_CHEST;
        if (be instanceof ChestBlockEntity) return StorageType.CHEST;
        if (be instanceof BarrelBlockEntity) return StorageType.BARREL;
        if (be instanceof HopperBlockEntity) return StorageType.HOPPER;
        if (be instanceof SpawnerBlockEntity) return StorageType.SPAWNER;
        return null;
    }

    // ------------------------------------------------------------------ incremental block scan

    private static Block blockFor(StorageType t) {
        return switch (t) {
            case PISTON -> Blocks.PISTON;
            case STICKY_PISTON -> Blocks.STICKY_PISTON;
            case AMETHYST -> Blocks.AMETHYST_CLUSTER;
            default -> null;
        };
    }

    /** Promień skanowania bloków: większy z zasięgów włączonych modułów (Storage Finder / Sus Chunk). */
    private static int scanRadius() {
        int r = 2;
        if (StorageFinderConfig.enabled) r = Math.max(r, StorageFinderConfig.radiusChunks);
        if (StorageFinderConfig.susEnabled) r = Math.max(r, StorageFinderConfig.susRange);
        return r;
    }

    private static void rebuildBlockTypes() {
        Map<Block, StorageType> m = new HashMap<>();
        for (StorageType t : StorageType.values()) {
            Block b = blockFor(t);
            boolean need = (StorageFinderConfig.enabled && StorageFinderConfig.isOn(t))
                    || (t == StorageType.AMETHYST && StorageFinderConfig.susEnabled);
            if (b != null && need) m.put(b, t);
        }
        blockTypes = m;
    }

    private static long[] buildQueue(ChunkPos center, int r) {
        List<Long> list = new ArrayList<>();
        for (int cx = center.x - r; cx <= center.x + r; cx++) {
            for (int cz = center.z - r; cz <= center.z + r; cz++) {
                list.add(ChunkPos.asLong(cx, cz));
            }
        }
        list.sort((a, b) -> {
            long da = (long) (ChunkPos.getX(a) - center.x) * (ChunkPos.getX(a) - center.x)
                    + (long) (ChunkPos.getZ(a) - center.z) * (ChunkPos.getZ(a) - center.z);
            long db = (long) (ChunkPos.getX(b) - center.x) * (ChunkPos.getX(b) - center.x)
                    + (long) (ChunkPos.getZ(b) - center.z) * (ChunkPos.getZ(b) - center.z);
            return Long.compare(da, db);
        });
        long[] arr = new long[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }

    /** Zwraca true, jeśli lista wyników się zmieniła. */
    private static boolean stepBlocks(Minecraft mc, ClientLevel level) {
        boolean changed = false;

        if (qIndex >= queue.length) {
            if (!startCycleNow && tickCounter % 10 != 0) return false;   // przerwa między cyklami
            startCycleNow = false;

            ChunkPos center = mc.player.chunkPosition();
            int r = Math.min(scanRadius(), mc.options.renderDistance().get());
            queue = buildQueue(center, r);
            qIndex = 0;
            rebuildBlockTypes();

            Set<Long> keep = new HashSet<>();
            for (long k : queue) keep.add(k);
            changed |= blockCache.keySet().removeIf(k -> !keep.contains(k));
        }

        long deadline = System.nanoTime() + BUDGET_NANOS;
        int minSection = level.getMinSectionY();

        while (qIndex < queue.length) {
            long key = queue[qIndex++];
            int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);

            if (!(level.getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) {
                if (blockCache.remove(key) != null) changed = true;
            } else {
                List<Entry> res = new ArrayList<>();
                scanChunkBlocks(chunk, minSection, res);
                List<Entry> old = blockCache.put(key, res.isEmpty() ? List.of() : res);
                boolean same = (old == null) ? res.isEmpty() : old.equals(res);
                if (!same) changed = true;
            }
            if (System.nanoTime() > deadline) break;
        }

        if (changed) {
            List<Entry> merged = new ArrayList<>();
            int[] cnt = new int[N];
            for (List<Entry> l : blockCache.values()) {
                for (Entry e : l) {
                    if (e.type() == StorageType.AMETHYST
                            && !(StorageFinderConfig.enabled && StorageFinderConfig.isOn(StorageType.AMETHYST))) continue;
                    merged.add(e);
                    cnt[e.type().ordinal()]++;
                }
            }
            blockEntries = merged;
            blockCounts = cnt;
        }
        return changed;
    }

    private static void scanChunkBlocks(LevelChunk chunk, int minSection, List<Entry> out) {
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        Map<Block, StorageType> types = blockTypes;

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection s = sections[i];
            if (s == null || s.hasOnlyAir()) continue;
            if (!s.maybeHas(state -> types.containsKey(state.getBlock()))) continue;

            int baseY = (minSection + i) << 4;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        StorageType t = types.get(s.getBlockState(x, y, z).getBlock());
                        if (t != null) out.add(new Entry(new BlockPos(baseX + x, baseY + y, baseZ + z), t));
                    }
                }
            }
        }
    }

    /** Liczy wyrośnięte kryształy ametystu na chunk i wyznacza "sus" chunki (próg z ustawień). */
    private static void rebuildSus(Minecraft mc) {
        lastSusOn = StorageFinderConfig.susEnabled;
        lastSusThr = StorageFinderConfig.susThreshold;
        if (!lastSusOn) {
            sus = List.of();
            return;
        }

        List<SusChunk> out = new ArrayList<>();
        for (Map.Entry<Long, List<Entry>> en : blockCache.entrySet()) {
            int n = 0, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            for (Entry e : en.getValue()) {
                if (e.type() != StorageType.AMETHYST) continue;
                n++;
                int y = e.pos().getY();
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
            if (n >= lastSusThr) {
                long k = en.getKey();
                out.add(new SusChunk(ChunkPos.getX(k), ChunkPos.getZ(k), n, minY, maxY));
            }
        }
        sus = out;

        if (StorageFinderConfig.susNotify && mc.player != null) {
            for (SusChunk c : out) {
                if (notified.add(ChunkPos.asLong(c.cx(), c.cz()))) {
                    mc.player.displayClientMessage(Component.literal("§c[Krux] Sus chunk: X " + (c.cx() * 16)
                            + " Z " + (c.cz() * 16) + "  (" + c.count() + " amethyst clusters)"), false);
                }
            }
        }
    }

    // ------------------------------------------------------------------ entities

    private static void scanEntities(Minecraft mc, ClientLevel level) {
        boolean esp = StorageFinderConfig.espEnabled;
        boolean tr = StorageFinderConfig.tracersEnabled;
        boolean wantPlayers = (esp && StorageFinderConfig.espPlayers) || (tr && StorageFinderConfig.trPlayers);
        boolean wantHostile = (esp && StorageFinderConfig.espHostile) || (tr && StorageFinderConfig.trHostile);
        boolean wantPassive = (esp && StorageFinderConfig.espPassive) || (tr && StorageFinderConfig.trPassive);
        if (!wantPlayers && !wantHostile && !wantPassive) {
            targets = List.of();
            return;
        }

        Entity center = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        double d = mc.options.renderDistance().get() * 16.0;
        AABB box = new AABB(center.getX() - d, center.getY() - d, center.getZ() - d,
                center.getX() + d, center.getY() + d, center.getZ() + d);

        List<Target> list = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e == mc.player || e.isRemoved() || e.isSpectator()) continue;
            if (e instanceof Player) {
                if (wantPlayers) list.add(new Target(e, Kind.PLAYER));
            } else if (e instanceof Enemy) {
                if (wantHostile) list.add(new Target(e, Kind.HOSTILE));
            } else if (e instanceof Mob && e.isAttackable()) {
                if (wantPassive) list.add(new Target(e, Kind.PASSIVE));
            }
        }
        targets = list;
    }
}
