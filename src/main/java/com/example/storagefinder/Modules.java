package com.example.storagefinder;

import java.util.ArrayList;
import java.util.List;

/** Rejestr modułów widocznych w Click GUI. */
public final class Modules {
    private static final int NONE = 0;                 // brak kolorowej kropki w GUI
    private static final int PLAYER = 0xFF3DD6FF;
    private static final int MOB = 0xFFFF5555;
    private static final int PASSIVE = 0xFF58D68D;

    public static final String[] ACCENT_NAMES = {"Violet", "Blue", "Redstone", "Diamond", "Emerald", "Gold"};
    public static final int[] ACCENT_COLORS = {0xA78BFA, 0x3D8BFF, 0xFF4B4B, 0x45E0E6, 0x3FE07D, 0xFFC73A};

    public static final List<String> CATEGORIES = List.of("Combat", "Movement", "Misc", "Render");
    public static final List<Module> ALL = new ArrayList<>();
    public static final Module ARRAY_LIST;

    private Modules() {}

    private static String f1(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    static {
        // ---------------- Combat ----------------
        ALL.add(new Module("Aura", "Combat",
                "Attacks the closest target in range on its own.",
                new String[]{"Aim", "Legit"},
                Aura::isEnabled, Aura::setEnabled, List.of(
                new Setting.Slider("Range", 3, 6, 0.1, false,
                        () -> StorageFinderConfig.auraRange, v -> StorageFinderConfig.auraRange = v),
                new Setting.Slider("Speed", 1, 20, 1, true,
                        () -> StorageFinderConfig.auraSpeed, v -> StorageFinderConfig.auraSpeed = v),
                new Setting.Mode("Hit when", new String[]{"Always", "On ground", "Can crit", "Ground or crit"},
                        () -> StorageFinderConfig.auraHitMode, v -> StorageFinderConfig.auraHitMode = v),
                new Setting.Slider("Crit sensitivity", 1, 5, 1, true,
                        () -> StorageFinderConfig.auraCritSens, v -> StorageFinderConfig.auraCritSens = v),
                new Setting.Bool("Stop sprint for crits", NONE,
                        () -> StorageFinderConfig.auraStopSprint, v -> StorageFinderConfig.auraStopSprint = v),
                new Setting.Bool("Players only", NONE,
                        () -> StorageFinderConfig.auraPlayersOnly, v -> StorageFinderConfig.auraPlayersOnly = v),
                new Setting.Bool("Wait for cooldown", NONE,
                        () -> StorageFinderConfig.auraCooldown, v -> StorageFinderConfig.auraCooldown = v),
                new Setting.Bool("Through walls", NONE,
                        () -> StorageFinderConfig.auraWalls, v -> StorageFinderConfig.auraWalls = v),
                new Setting.Bool("Silent rotation", NONE,
                        () -> StorageFinderConfig.auraSilent, v -> StorageFinderConfig.auraSilent = v),
                new Setting.Bool("Random delay", NONE,
                        () -> StorageFinderConfig.auraRandomDelay, v -> StorageFinderConfig.auraRandomDelay = v),
                new Setting.Slider("Aim jitter", 0.0, 0.5, 0.05, false,
                        () -> StorageFinderConfig.auraJitter, v -> StorageFinderConfig.auraJitter = v)
        )).withSuffix(() -> StorageFinderConfig.auraPlayersOnly ? "Players" : "All"));

        // ---------------- Movement ----------------
        ALL.add(new Module("Sprint", "Movement",
                "Sprints automatically while you walk forward.",
                new String[]{"Basics", "Utility"},
                Sprint::isEnabled, Sprint::setEnabled, List.of()));

        ALL.add(new Module("Freecam", "Movement",
                "Fly the camera freely while your character stays in place.",
                new String[]{"Camera", "Utility"},
                Freecam::isActive, Freecam::setActive, List.of(
                new Setting.Slider("Speed", 1, 10, 0.5, false,
                        () -> StorageFinderConfig.freecamSpeed, v -> StorageFinderConfig.freecamSpeed = v)
        )).withSuffix(() -> f1(StorageFinderConfig.freecamSpeed)));

        // ---------------- Render ----------------
        List<Setting> sf = new ArrayList<>();
        for (StorageType t : StorageType.values()) {
            sf.add(new Setting.Bool(t.label, t.argb(),
                    () -> StorageFinderConfig.isOn(t), v -> StorageFinderConfig.setOn(t, v)));
        }
        sf.add(new Setting.Bool("Tracers", NONE,
                () -> StorageFinderConfig.tracers, v -> StorageFinderConfig.tracers = v));
        sf.add(new Setting.Slider("Scan range", 2, 16, 1, true,
                () -> StorageFinderConfig.radiusChunks, v -> StorageFinderConfig.radiusChunks = (int) Math.round(v)));
        ALL.add(new Module("Storage Finder", "Render",
                "Finds chests, barrels, spawners, hoppers, pistons and amethyst through walls.",
                new String[]{"Search", "Blocks"},
                () -> StorageFinderConfig.enabled, v -> StorageFinderConfig.enabled = v, sf)
                .withSuffix(() -> String.valueOf(StorageScanner.found().size())));

        ALL.add(new Module("ESP", "Render",
                "Boxes around players and mobs, visible through walls.",
                new String[]{"Entities", "Visual"},
                () -> StorageFinderConfig.espEnabled, v -> StorageFinderConfig.espEnabled = v, List.of(
                new Setting.Bool("Players", PLAYER,
                        () -> StorageFinderConfig.espPlayers, v -> StorageFinderConfig.espPlayers = v),
                new Setting.Bool("Hostile mobs", MOB,
                        () -> StorageFinderConfig.espHostile, v -> StorageFinderConfig.espHostile = v),
                new Setting.Bool("Passive mobs", PASSIVE,
                        () -> StorageFinderConfig.espPassive, v -> StorageFinderConfig.espPassive = v),
                new Setting.ColorPick("Player color",
                        () -> StorageFinderConfig.espPlayerColor, v -> StorageFinderConfig.espPlayerColor = v),
                new Setting.ColorPick("Mob color",
                        () -> StorageFinderConfig.espMobColor, v -> StorageFinderConfig.espMobColor = v),
                new Setting.Slider("Opacity", 10, 100, 5, true,
                        () -> StorageFinderConfig.espOpacity, v -> StorageFinderConfig.espOpacity = (int) Math.round(v))
        )).withSuffix(() -> StorageFinderConfig.espPlayers ? "Players" : "Mobs"));

        ALL.add(new Module("Tracers", "Render",
                "Lines from your crosshair to players and mobs. Uses the ESP colors.",
                new String[]{"Lines", "Visual"},
                () -> StorageFinderConfig.tracersEnabled, v -> StorageFinderConfig.tracersEnabled = v, List.of(
                new Setting.Bool("Players", PLAYER,
                        () -> StorageFinderConfig.trPlayers, v -> StorageFinderConfig.trPlayers = v),
                new Setting.Bool("Hostile mobs", MOB,
                        () -> StorageFinderConfig.trHostile, v -> StorageFinderConfig.trHostile = v),
                new Setting.Bool("Passive mobs", PASSIVE,
                        () -> StorageFinderConfig.trPassive, v -> StorageFinderConfig.trPassive = v),
                new Setting.Slider("Thickness", 0.5, 4, 0.5, false,
                        () -> StorageFinderConfig.tracerThickness, v -> StorageFinderConfig.tracerThickness = v)
        )));

        ALL.add(new Module("Fullbright", "Render",
                "Full brightness everywhere, even in caves and at night.",
                new String[]{"Light"},
                Fullbright::isEnabled, Fullbright::setEnabled, List.of()));

        ALL.add(new Module("Zoom", "Render",
                "Zooms the view in. The previous FOV is restored when you turn it off.",
                new String[]{"FOV"},
                Zoom::isEnabled, Zoom::setEnabled, List.of(
                new Setting.Slider("Zoom FOV", 30, 90, 1, true,
                        () -> StorageFinderConfig.zoomFov, v -> StorageFinderConfig.zoomFov = (int) Math.round(v))
        )));

        // ---------------- Misc ----------------
        ARRAY_LIST = new Module("ArrayList", "Misc",
                "Lists enabled modules in the top right corner. The accent color applies to the whole client.",
                new String[]{"HUD"},
                () -> StorageFinderConfig.arrayList, v -> StorageFinderConfig.arrayList = v, List.of(
                new Setting.Swatches("Accent", ACCENT_NAMES, ACCENT_COLORS,
                        () -> StorageFinderConfig.accent, v -> StorageFinderConfig.accent = v),
                new Setting.Mode("Color mode", new String[]{"Static", "Rainbow", "Gradient"},
                        () -> StorageFinderConfig.alColorMode, v -> StorageFinderConfig.alColorMode = v),
                new Setting.Mode("Sort", new String[]{"Length", "A-Z"},
                        () -> StorageFinderConfig.alSort, v -> StorageFinderConfig.alSort = v),
                new Setting.Bool("Suffixes", NONE,
                        () -> StorageFinderConfig.alSuffix, v -> StorageFinderConfig.alSuffix = v),
                new Setting.Bool("Background", NONE,
                        () -> StorageFinderConfig.alBackground, v -> StorageFinderConfig.alBackground = v)
        ));
        ALL.add(ARRAY_LIST);

        ALL.add(new Module("Name Protect", "Misc",
                "Replaces your real name with a fake one in chat, tab list and nametags (client side only).",
                new String[]{"Privacy"},
                () -> StorageFinderConfig.npEnabled, v -> StorageFinderConfig.npEnabled = v, List.of(
                new Setting.Text("Fake name", 16,
                        () -> StorageFinderConfig.npName, v -> StorageFinderConfig.npName = v)
        )));

        ALL.add(new Module("Info", "Misc",
                "Shows coordinates, FPS and facing direction in the top left corner.",
                new String[]{"HUD", "Info"},
                () -> StorageFinderConfig.infoEnabled, v -> StorageFinderConfig.infoEnabled = v, List.of(
                new Setting.Bool("Coordinates", NONE,
                        () -> StorageFinderConfig.infoCoords, v -> StorageFinderConfig.infoCoords = v),
                new Setting.Bool("FPS", NONE,
                        () -> StorageFinderConfig.infoFps, v -> StorageFinderConfig.infoFps = v),
                new Setting.Bool("Direction", NONE,
                        () -> StorageFinderConfig.infoDir, v -> StorageFinderConfig.infoDir = v)
        )));
    }
}
