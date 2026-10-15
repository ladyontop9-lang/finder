package com.example.storagefinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
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

/**
 * Skanuje załadowane chunki.
 * Block entity (skrzynie, hoppery, spawnery...) co 2 ticki, bloki szukane po stanie (pistony, ametyst) co 10 ticków.
 * Każda zmiana ustawień wymusza natychmiastowe skanowanie.
 */
public final class StorageScanner {
    public record Entry(BlockPos pos, StorageType type) {}

    private static final int N = StorageType.values().length;

    private static volatile List<Entry> found = List.of();
    private static List<Entry> beEntries = List.of();
    private static List<Entry> blockEntries = List.of();
    private static int[] beCounts = new int[N];
    private static int[] blockCounts = new int[N];
    private static volatile int playerCount = 0;
    private static int tickCounter = 0;
    private static volatile boolean rescan = true;

    private StorageScanner() {}

    public static void requestRescan() {
        rescan = true;
    }

    public static List<Entry> found() {
        return found;
    }

    public static int count(StorageType t) {
        return beCounts[t.ordinal()] + blockCounts[t.ordinal()];
    }

    public static int playerCount() {
        return playerCount;
    }

    public static void tick(Minecraft mc) {
        if (!StorageFinderConfig.enabled) {
            if (!found.isEmpty()) {
                found = List.of();
                beEntries = List.of();
                blockEntries = List.of();
                beCounts = new int[N];
                blockCounts = new int[N];
            }
            playerCount = 0;
            rescan = true;
            return;
        }
        ClientLevel level = mc.level;
        Player player = mc.player;
        if (level == null || player == null) return;

        boolean force = rescan;
        boolean doBe = force || tickCounter % 2 == 0;
        boolean doBlocks = force || tickCounter % 10 == 0;
        tickCounter++;
        rescan = false;
        if (!doBe && !doBlocks) return;

        if (doBe) playerCount = Math.max(0, level.players().size() - 1);

        List<Entry> newBe = new ArrayList<>();
        List<Entry> newBlocks = new ArrayList<>();
        int[] newBeCounts = new int[N];
        int[] newBlockCounts = new int[N];

        Map<Block, StorageType> blockTypes = new HashMap<>();
        for (StorageType t : StorageType.values()) {
            Block b = blockFor(t);
            if (b != null && StorageFinderConfig.isOn(t)) blockTypes.put(b, t);
        }

        ChunkPos center = player.chunkPosition();
        int r = Math.min(StorageFinderConfig.radiusChunks, mc.options.renderDistance().get());
        int minSection = level.getMinSectionY();

        for (int cx = center.x - r; cx <= center.x + r; cx++) {
            for (int cz = center.z - r; cz <= center.z + r; cz++) {
                if (!(level.getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) continue;

                if (doBe) {
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        StorageType t = typeOf(be);
                        if (t == null || !StorageFinderConfig.isOn(t)) continue;
                        newBe.add(new Entry(be.getBlockPos(), t));
                        newBeCounts[t.ordinal()]++;
                    }
                }

                if (doBlocks && !blockTypes.isEmpty()) {
                    scanBlocks(chunk, minSection, blockTypes, newBlocks, newBlockCounts);
                }
            }
        }

        if (doBe) {
            beEntries = newBe;
            beCounts = newBeCounts;
        }
        if (doBlocks) {
            blockEntries = newBlocks;
            blockCounts = newBlockCounts;
        }

        List<Entry> merged = new ArrayList<>(beEntries.size() + blockEntries.size());
        merged.addAll(beEntries);
        merged.addAll(blockEntries);
        found = merged;
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

    /** Typy, które nie są block entity — szukamy ich po samym bloku. */
    private static Block blockFor(StorageType t) {
        return switch (t) {
            case PISTON -> Blocks.PISTON;
            case STICKY_PISTON -> Blocks.STICKY_PISTON;
            case AMETHYST -> Blocks.AMETHYST_CLUSTER;
            default -> null;
        };
    }

    private static void scanBlocks(LevelChunk chunk, int minSection, Map<Block, StorageType> blockTypes,
                                   List<Entry> list, int[] counts) {
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection s = sections[i];
            if (s == null || s.hasOnlyAir()) continue;
            if (!s.maybeHas(state -> blockTypes.containsKey(state.getBlock()))) continue;

            int baseY = (minSection + i) << 4;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        StorageType t = blockTypes.get(s.getBlockState(x, y, z).getBlock());
                        if (t != null) {
                            list.add(new Entry(new BlockPos(baseX + x, baseY + y, baseZ + z), t));
                            counts[t.ordinal()]++;
                        }
                    }
                }
            }
        }
    }
}
