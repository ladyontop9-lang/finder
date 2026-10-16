package com.example.storagefinder;

import java.util.ArrayList;
import java.util.List;

/** Rejestr modułów widocznych w Click GUI. */
public final class Modules {
    private static final int ON = 0xFF7DD3C0;
    private static final int PLAYER = 0xFF3DD6FF;
    private static final int MOB = 0xFFFF5555;
    private static final int PASSIVE = 0xFF58D68D;

    public static final List<String> CATEGORIES = List.of("Combat", "Render");
    public static final List<Module> ALL = new ArrayList<>();
    public static final Module ARRAY_LIST;

    private Modules() {}

    static {
        // ---- Combat ----
        ALL.add(new Module("Aura", "Combat", Aura::isEnabled, Aura::setEnabled, List.of(
                new Setting.Slider("Range", 3, 6, 0.1, false,
                        () -> StorageFinderConfig.auraRange, v -> StorageFinderConfig.auraRange = v),
                new Setting.Slider("Speed", 1, 20, 1, true,
                        () -> StorageFinderConfig.auraSpeed, v -> StorageFinderConfig.auraSpeed = v),
                new Setting.Bool("Players only", ON,
                        () -> StorageFinderConfig.auraPlayersOnly, v -> StorageFinderConfig.auraPlayersOnly = v),
                new Setting.Bool("Wait for cooldown", ON,
                        () -> StorageFinderConfig.auraCooldown, v -> StorageFinderConfig.auraCooldown = v),
                new Setting.Bool("Through walls", ON,
                        () -> StorageFinderConfig.auraWalls, v -> StorageFinderConfig.auraWalls = v),
                new Setting.Bool("Face target", ON,
                        () -> StorageFinderConfig.auraRotate, v -> StorageFinderConfig.auraRotate = v)
        )));

        // ---- Render ----
        List<Setting> sf = new ArrayList<>();
        for (StorageType t : StorageType.values()) {
            sf.add(new Setting.Bool(t.label, t.argb(),
                    () -> StorageFinderConfig.isOn(t), v -> StorageFinderConfig.setOn(t, v)));
        }
        sf.add(new Setting.Bool("Tracers", ON,
                () -> StorageFinderConfig.tracers, v -> StorageFinderConfig.tracers = v));
        sf.add(new Setting.Slider("Scan range", 2, 16, 1, true,
                () -> StorageFinderConfig.radiusChunks, v -> StorageFinderConfig.radiusChunks = (int) Math.round(v)));
        ALL.add(new Module("Storage Finder", "Render",
                () -> StorageFinderConfig.enabled, v -> StorageFinderConfig.enabled = v, sf));

        ALL.add(new Module("ESP", "Render",
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
        )));

        ALL.add(new Module("Tracers", "Render",
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

        ALL.add(new Module("Freecam", "Render", Freecam::isActive, Freecam::setActive, List.of(
                new Setting.Slider("Speed", 1, 10, 0.5, false,
                        () -> StorageFinderConfig.freecamSpeed, v -> StorageFinderConfig.freecamSpeed = v)
        )));

        ARRAY_LIST = new Module("ArrayList", "Render",
                () -> StorageFinderConfig.arrayList, v -> StorageFinderConfig.arrayList = v, List.of(
                new Setting.Bool("Background", ON,
                        () -> StorageFinderConfig.alBackground, v -> StorageFinderConfig.alBackground = v),
                new Setting.Bool("Side bar", ON,
                        () -> StorageFinderConfig.alBar, v -> StorageFinderConfig.alBar = v),
                new Setting.Bool("Title", ON,
                        () -> StorageFinderConfig.alTitle, v -> StorageFinderConfig.alTitle = v)
        ));
        ALL.add(ARRAY_LIST);
    }
}
