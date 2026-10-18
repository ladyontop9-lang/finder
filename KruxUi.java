package com.example.storagefinder;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Wspólny styl okien Krux (licencja, narzędzia dewelopera). */
final class KruxUi {
    static final int WINBG = 0xFF111114, BORDER = 0xFF26262B, FIELD = 0xFF202025, TEXT = 0xFFF0F0F5,
            MUT = 0xFF8A8A94, DIM = 0xFF55555E, GREEN = 0xFF3DD68C, RED = 0xFFF0707A, VEIL = 0x90000000;

    private KruxUi() {}

    static int accent() { return 0xFF000000 | StorageFinderConfig.accent; }

    static void box(GuiGraphics g, int x, int y, int w, int h, int r, int fill, int border) {
        Ui.rrect(g, x - 1, y - 1, w + 2, h + 2, r + 1, border);
        Ui.rrect(g, x, y, w, h, r, fill);
    }

    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        box(g, x, y, w, h, 6, WINBG, BORDER);
    }

    static void logo(GuiGraphics g, Font font, int x, int y) {
        Ui.rrect(g, x, y, 18, 18, 5, accent());
        g.drawString(font, "K", x + 9 - font.width("K") / 2, y + 5, 0xFFFFFFFF);
    }

    static void button(GuiGraphics g, Font font, int x, int y, int w, int h, String label, boolean hover, boolean primary) {
        int bg = primary ? (hover ? lighten(accent()) : accent()) : (hover ? 0xFF2A2A31 : FIELD);
        box(g, x, y, w, h, 4, bg, primary ? bg : BORDER);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, primary ? 0xFFFFFFFF : TEXT);
    }

    static void field(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
        box(g, x, y, w, h, 4, FIELD, focused ? accent() : BORDER);
    }

    static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 28), gr = Math.min(255, ((c >> 8) & 255) + 28), b = Math.min(255, (c & 255) + 28);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }
}
