package com.example.storagefinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.class_1657;
import net.minecraft.class_1923;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2586;
import net.minecraft.class_2595;
import net.minecraft.class_2611;
import net.minecraft.class_2614;
import net.minecraft.class_2627;
import net.minecraft.class_2806;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_310;
import net.minecraft.class_3719;
import net.minecraft.class_638;

/** Skanuje załadowane chunki co pół sekundy i zapamiętuje znalezione bloki. */
public final class StorageScanner {
    public record Entry(class_2338 pos, StorageType type) {}

    private static volatile List<Entry> found = List.of();
    private static final int[] counts = new int[StorageType.values().length];
    private static volatile int playerCount = 0;
    private static int tickCounter = 0;

    private StorageScanner() {}

    public static List<Entry> found() {
        return found;
    }

    public static int count(StorageType t) {
        return counts[t.ordinal()];
    }

    public static int playerCount() {
        return playerCount;
    }

    public static void tick(class_310 mc) {
        if (!StorageFinderConfig.enabled) {
            if (!found.isEmpty()) {
                found = List.of();
                Arrays.fill(counts, 0);
            }
            playerCount = 0;
            return;
        }
        class_638 level = mc.field_1687;
        class_1657 player = mc.field_1724;
        if (level == null || player == null) return;
        if (tickCounter++ % 10 != 0) return;

        playerCount = Math.max(0, level.method_18456().size() - 1);

        List<Entry> list = new ArrayList<>();
        int[] newCounts = new int[counts.length];

        Map<class_2248, StorageType> blockTypes = new HashMap<>();
        for (StorageType t : StorageType.values()) {
            class_2248 b = blockFor(t);
            if (b != null && StorageFinderConfig.isOn(t)) blockTypes.put(b, t);
        }

        class_1923 center = player.method_31476();
        int r = Math.min(StorageFinderConfig.radiusChunks, mc.field_1690.method_42503().method_41753());
        int minSection = level.method_32891();

        for (int cx = center.field_9181 - r; cx <= center.field_9181 + r; cx++) {
            for (int cz = center.field_9180 - r; cz <= center.field_9180 + r; cz++) {
                if (!(level.method_8402(cx, cz, class_2806.field_12803, false) instanceof class_2818 chunk)) continue;

                for (class_2586 be : chunk.method_12214().values()) {
                    StorageType t = typeOf(be);
                    if (t == null || !StorageFinderConfig.isOn(t)) continue;
                    list.add(new Entry(be.method_11016(), t));
                    newCounts[t.ordinal()]++;
                }

                if (!blockTypes.isEmpty()) {
                    scanBlocks(chunk, minSection, blockTypes, list, newCounts);
                }
            }
        }

        System.arraycopy(newCounts, 0, counts, 0, counts.length);
        found = list;
    }

    private static StorageType typeOf(class_2586 be) {
        if (be instanceof class_2627) return StorageType.SHULKER;
        if (be instanceof class_2611) return StorageType.ENDER_CHEST;
        if (be instanceof class_2595) return StorageType.CHEST;
        if (be instanceof class_3719) return StorageType.BARREL;
        if (be instanceof class_2614) return StorageType.HOPPER;
        return null;
    }

    /** Typy, które nie są block entity — szukamy ich po samym bloku. */
    private static class_2248 blockFor(StorageType t) {
        return switch (t) {
            case PISTON -> class_2246.field_10560;
            case STICKY_PISTON -> class_2246.field_10615;
            case AMETHYST -> class_2246.field_27161;
            default -> null;
        };
    }

    private static void scanBlocks(class_2818 chunk, int minSection, Map<class_2248, StorageType> blockTypes,
                                   List<Entry> list, int[] newCounts) {
        class_2826[] sections = chunk.method_12006();
        int baseX = chunk.method_12004().method_8326();
        int baseZ = chunk.method_12004().method_8328();

        for (int i = 0; i < sections.length; i++) {
            class_2826 s = sections[i];
            if (s == null || s.method_38292()) continue;
            if (!s.method_19523(state -> blockTypes.containsKey(state.method_26204()))) continue;

            int baseY = (minSection + i) << 4;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        StorageType t = blockTypes.get(s.method_12254(x, y, z).method_26204());
                        if (t != null) {
                            list.add(new Entry(new class_2338(baseX + x, baseY + y, baseZ + z), t));
                            newCounts[t.ordinal()]++;
                        }
                    }
                }
            }
        }
    }
}
