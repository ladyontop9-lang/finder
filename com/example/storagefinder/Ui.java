package com.example.storagefinder;

import net.minecraft.class_327;
import net.minecraft.class_332;

/** Małe narzędzia do rysowania ładnego, zaokrąglonego interfejsu. */
final class Ui {
    static final int BG = 0xFF141821;
    static final int BORDER = 0xFF2B3243;
    static final int CARD = 0xFF1C2230;
    static final int CARD_HOVER = 0xFF252D40;
    static final int TEXT = 0xFFE8ECF4;
    static final int MUTED = 0xFF8A93A6;
    static final int ACCENT = 0xFF6C8CFF;
    static final int ACCENT_HOVER = 0xFF8AA3FF;
    static final int ON = 0xFF3DD68C;
    static final int OFF = 0xFF3A4152;
    static final int WHITE = 0xFFFFFFFF;
    static final int PLAYER = 0xFF3DD6FF;

    private Ui() {}

    static void rrect(class_332 g, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            g.method_25294(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.method_25294(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
        g.method_25294(x, y + r, x + w, y + h - r, color);
    }

    static void panel(class_332 g, int x, int y, int w, int h) {
        rrect(g, x - 1, y - 1, w + 2, h + 2, 8, BORDER);
        rrect(g, x, y, w, h, 7, BG);
    }

    static void toggle(class_332 g, int x, int y, boolean on) {
        rrect(g, x, y, 34, 16, 8, on ? ON : OFF);
        rrect(g, on ? x + 34 - 14 : x + 2, y + 2, 12, 12, 6, WHITE);
    }

    static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    static void text(class_332 g, class_327 font, String s, int x, int y, int color) {
        g.method_25303(font, s, x, y, color);
    }

    static void textRight(class_332 g, class_327 font, String s, int xRight, int y, int color) {
        g.method_25303(font, s, xRight - font.method_1727(s), y, color);
    }

    static void textCenter(class_332 g, class_327 font, String s, int cx, int y, int color) {
        g.method_25303(font, s, cx - font.method_1727(s) / 2, y, color);
    }
}
