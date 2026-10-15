package com.example.storagefinder;

import java.util.ArrayList;
import java.util.List;

/** Rejestr modułów widocznych w Click GUI. */
public final class Modules {
    private static final int Ui_ON = 0xFF7DD3C0;

    public static final List<String> CATEGORIES = List.of("Combat", "Render");
    public static final List<Module> ALL = new ArrayList<>();

    private Modules() {}

    static {
        // ---- Combat ----
        ALL.add(new Module("Aura", "Combat", Aura::isEnabled, Aura::setEnabled, List.of(
                new Setting.Slider("Range", 3, 6, 0.1, false,
                        () -> StorageFinderConfig.auraRange, v -> StorageFinderConfig.auraRange = v),
                new Setting.Slider("Speed", 1, 20, 1, true,
                        () -> StorageFinderConfig.auraSpeed, v -> StorageFinderConfig.auraSpeed = v),
                new Setting.Bool("Players only", Ui_ON,
                        () -> StorageFinderConfig.auraPlayersOnly, v -> StorageFinderConfig.auraPlayersOnly = v)
        )));

        // ---- Render ----
        List<Setting> sf = new ArrayList<>();
        for (StorageType t : StorageType.values()) {
            sf.add(new Setting.Bool(t.label, t.argb(),
                    () -> StorageFinderConfig.isOn(t), v -> StorageFinderConfig.setOn(t, v)));
        }
        sf.add(new Setting.Bool("Players", Ui.PLAYER,
                () -> StorageFinderConfig.players, v -> StorageFinderConfig.players = v));
        sf.add(new Setting.Bool("Tracers", Ui_ON,
                () -> StorageFinderConfig.tracers, v -> StorageFinderConfig.tracers = v));
        sf.add(new Setting.Slider("Scan range", 2, 16, 1, true,
                () -> StorageFinderConfig.radiusChunks, v -> StorageFinderConfig.radiusChunks = (int) Math.round(v)));

        ALL.add(new Module("Storage Finder", "Render",
                () -> StorageFinderConfig.enabled, v -> StorageFinderConfig.enabled = v, sf));

        ALL.add(new Module("Freecam", "Render", Freecam::isActive, Freecam::setActive, List.of(
                new Setting.Slider("Speed", 1, 10, 0.5, false,
                        () -> StorageFinderConfig.freecamSpeed, v -> StorageFinderConfig.freecamSpeed = v)
        )));
    }

}
