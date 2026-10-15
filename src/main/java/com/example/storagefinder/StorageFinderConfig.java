package com.example.storagefinder;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

public final class StorageFinderConfig {
    public static boolean enabled = false;
    public static final boolean[] types = new boolean[StorageType.values().length];
    public static boolean players = false;
    public static boolean tracers = false;
    public static int radiusChunks = 8;

    public static double freecamSpeed = 3.0;
    public static double auraRange = 3.5;
    public static double auraSpeed = 8.0;
    public static boolean auraPlayersOnly = true;

    static {
        java.util.Arrays.fill(types, true);
    }

    private StorageFinderConfig() {}

    public static boolean isOn(StorageType t) {
        return types[t.ordinal()];
    }

    public static void setOn(StorageType t, boolean v) {
        types[t.ordinal()] = v;
    }

    public static void toggle(StorageType t) {
        types[t.ordinal()] = !types[t.ordinal()];
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("storagefinder.properties");
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(f)) {
                p.load(in);
            }
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "false"));
            for (StorageType t : StorageType.values()) {
                types[t.ordinal()] = Boolean.parseBoolean(p.getProperty("type." + t.name(), "true"));
            }
            players = Boolean.parseBoolean(p.getProperty("players", "false"));
            tracers = Boolean.parseBoolean(p.getProperty("tracers", "false"));
            radiusChunks = Math.max(2, Math.min(16, Integer.parseInt(p.getProperty("radius", "8"))));
            freecamSpeed = Double.parseDouble(p.getProperty("freecamSpeed", "3.0"));
            auraRange = Double.parseDouble(p.getProperty("auraRange", "3.5"));
            auraSpeed = Double.parseDouble(p.getProperty("auraSpeed", "8.0"));
            auraPlayersOnly = Boolean.parseBoolean(p.getProperty("auraPlayersOnly", "true"));
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        StorageScanner.requestRescan();
        try {
            Properties p = new Properties();
            p.setProperty("enabled", Boolean.toString(enabled));
            for (StorageType t : StorageType.values()) {
                p.setProperty("type." + t.name(), Boolean.toString(types[t.ordinal()]));
            }
            p.setProperty("players", Boolean.toString(players));
            p.setProperty("tracers", Boolean.toString(tracers));
            p.setProperty("radius", Integer.toString(radiusChunks));
            p.setProperty("freecamSpeed", Double.toString(freecamSpeed));
            p.setProperty("auraRange", Double.toString(auraRange));
            p.setProperty("auraSpeed", Double.toString(auraSpeed));
            p.setProperty("auraPlayersOnly", Boolean.toString(auraPlayersOnly));
            try (OutputStream out = Files.newOutputStream(file())) {
                p.store(out, "Storage Finder");
            }
        } catch (Exception ignored) {
        }
    }
}
