package com.example.storagefinder;

public enum StorageType {
    CHEST("Chests", 0xFFA21A),
    ENDER_CHEST("Ender chests", 0x6C5CFF),
    SHULKER("Shulker boxes", 0xFF4FD8),
    BARREL("Barrels", 0xC8955A),
    AMETHYST("Amethyst clusters", 0xC9A0FF);

    public final String label;
    public final int rgb;

    StorageType(String label, int rgb) {
        this.label = label;
        this.rgb = rgb;
    }

    public int argb() {
        return 0xFF000000 | rgb;
    }

    public float[] rgbFloats() {
        return new float[]{((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f};
    }
}
