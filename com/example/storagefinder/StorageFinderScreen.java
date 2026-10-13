package com.example.storagefinder;

import net.minecraft.class_11909;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_437;

/**
 * Menu w stylu ClickGUI: okno kategorii Render z modułami.
 * LPM na module = włącz/wyłącz, PPM na Storage Finder = rozwiń ustawienia.
 */
public class StorageFinderScreen extends class_437 {
    private static final int X = 16, Y = 16, W = 240;
    private static final int HEAD_H = 20, ROW_H = 22;
    private static final int SET_H = 186;
    private static final int MIN_R = 2, MAX_R = 16;

    private static final int C_BG = 0xF012141A;
    private static final int C_HEAD = 0xFF2A2E3A;
    private static final int C_ROW = 0xFF20242E;
    private static final int C_ROW_HOVER = 0xFF2A303D;
    private static final int C_SET = 0xFF171A22;

    private boolean expanded = false;
    private boolean dragging = false;
    private int tab = 0;
    private static final String[] TABS = {"Render", "Donut+", "Misc"};
    private static final int TAB_W = W / 3;

    public StorageFinderScreen() {
        super(class_2561.method_43470("Storage Finder"));
    }

    private int storageY() { return Y + HEAD_H; }
    private int settingsTop() { return storageY() + ROW_H; }
    private int expandedH() { return expanded ? SET_H : 0; }
    private int freeY() { return settingsTop() + expandedH(); }
    private int windowH() {
        if (tab != 0) return HEAD_H + ROW_H + 4;
        return HEAD_H + ROW_H + expandedH() + ROW_H + 4;
    }
    private int sliderX() { return X + 12; }
    private int sliderW() { return W - 24; }
    private int sliderY() { return settingsTop() + 22; }
    private int blockY(int i) { return settingsTop() + 38 + i * 14; }
    private int playersY() { return settingsTop() + 152; }
    private int tracersY() { return settingsTop() + 168; }

    @Override
    public void method_25394(class_332 g, int mouseX, int mouseY, float partialTick) {
        super.method_25394(g, mouseX, mouseY, partialTick);
        class_327 font = this.field_22793;

        // Okno kategorii
        Ui.rrect(g, X, Y, W, windowH(), 5, C_BG);
        Ui.rrect(g, X, Y, W, HEAD_H, 5, C_HEAD);
        for (int i = 0; i < TABS.length; i++) {
            int tx = X + i * TAB_W;
            boolean active = i == tab;
            boolean thover = Ui.hit(mouseX, mouseY, tx, Y, TAB_W, HEAD_H);
            if (thover && !active) Ui.rrect(g, tx, Y, TAB_W, HEAD_H, 0, C_ROW_HOVER);
            Ui.textCenter(g, font, TABS[i], tx + TAB_W / 2, Y + 6, active ? Ui.TEXT : Ui.MUTED);
            if (active) g.method_25294(tx + 8, Y + HEAD_H - 2, tx + TAB_W - 8, Y + HEAD_H, Ui.ACCENT);
        }

        if (tab != 0) {
            Ui.textCenter(g, font, "Brak modulow", X + W / 2, Y + HEAD_H + 7, Ui.MUTED);
            return;
        }

        // Storage Finder
        int sy = storageY();
        boolean sHover = Ui.hit(mouseX, mouseY, X, sy, W, ROW_H);
        boolean on = StorageFinderConfig.enabled;
        Ui.rrect(g, X, sy, W, ROW_H, 0, sHover ? C_ROW_HOVER : C_ROW);
        Ui.rrect(g, X + 8, sy + 6, 10, 10, 3, on ? Ui.ON : Ui.OFF);
        Ui.text(g, font, "Storage Finder", X + 24, sy + 7, on ? Ui.ON : Ui.TEXT);
        Ui.textRight(g, font, expanded ? "-" : "+", X + W - 8, sy + 7, Ui.MUTED);

        // Rozwinięte ustawienia
        if (expanded) {
            int st = settingsTop();
            Ui.rrect(g, X, st, W, SET_H, 0, C_SET);

            Ui.text(g, font, "Scan range", X + 12, st + 6, Ui.TEXT);
            Ui.textRight(g, font, StorageFinderConfig.radiusChunks + " chunks", X + W - 12, st + 6, Ui.MUTED);

            int sx = sliderX(), sw = sliderW(), sly = sliderY();
            Ui.rrect(g, sx, sly, sw, 8, 4, Ui.OFF);
            float frac = (StorageFinderConfig.radiusChunks - MIN_R) / (float) (MAX_R - MIN_R);
            Ui.rrect(g, sx, sly, Math.max(8, Math.round(sw * frac)), 8, 4, Ui.ACCENT);
            Ui.rrect(g, sx + Math.round((sw - 12) * frac), sly - 2, 12, 12, 6, Ui.WHITE);

            StorageType[] types = StorageType.values();
            for (int i = 0; i < types.length; i++) {
                StorageType t = types[i];
                int by = blockY(i);
                boolean hover = Ui.hit(mouseX, mouseY, X + 8, by, W - 16, 13);
                boolean tOn = StorageFinderConfig.isOn(t);
                if (hover) Ui.rrect(g, X + 8, by, W - 16, 13, 3, C_ROW_HOVER);
                Ui.rrect(g, X + 14, by + 2, 8, 8, 2, tOn ? t.argb() : Ui.OFF);
                Ui.text(g, font, t.label, X + 28, by + 3, tOn ? Ui.TEXT : Ui.MUTED);
                String count = (StorageFinderConfig.enabled && tOn) ? String.valueOf(StorageScanner.count(t)) : "-";
                Ui.textRight(g, font, count, X + W - 16, by + 3, Ui.MUTED);
            }

            boolean pHover = Ui.hit(mouseX, mouseY, X + 8, playersY(), W - 16, 13);
            boolean pOn = StorageFinderConfig.players;
            if (pHover) Ui.rrect(g, X + 8, playersY(), W - 16, 13, 3, C_ROW_HOVER);
            Ui.rrect(g, X + 14, playersY() + 2, 8, 8, 2, pOn ? Ui.PLAYER : Ui.OFF);
            Ui.text(g, font, "Players", X + 28, playersY() + 3, pOn ? Ui.TEXT : Ui.MUTED);
            String pc = (StorageFinderConfig.enabled && pOn) ? String.valueOf(StorageScanner.playerCount()) : "-";
            Ui.textRight(g, font, pc, X + W - 16, playersY() + 3, Ui.MUTED);

            boolean tHover = Ui.hit(mouseX, mouseY, X + 8, tracersY(), W - 16, 13);
            boolean tOn = StorageFinderConfig.tracers;
            if (tHover) Ui.rrect(g, X + 8, tracersY(), W - 16, 13, 3, C_ROW_HOVER);
            Ui.rrect(g, X + 14, tracersY() + 2, 8, 8, 2, tOn ? Ui.ACCENT : Ui.OFF);
            Ui.text(g, font, "Tracers", X + 28, tracersY() + 3, tOn ? Ui.TEXT : Ui.MUTED);
        }

        // Free camera
        int fy = freeY();
        boolean fHover = Ui.hit(mouseX, mouseY, X, fy, W, ROW_H);
        boolean fOn = StorageFreeCam.isActive();
        Ui.rrect(g, X, fy, W, ROW_H, 0, fHover ? C_ROW_HOVER : C_ROW);
        Ui.rrect(g, X + 8, fy + 6, 10, 10, 3, fOn ? Ui.ACCENT : Ui.OFF);
        Ui.text(g, font, "Free camera", X + 24, fy + 7, fOn ? Ui.TEXT : Ui.MUTED);
        Ui.textRight(g, font, "V", X + W - 8, fy + 7, Ui.MUTED);
    }

    private void setRadiusFromMouse(double mx) {
        double f = (mx - sliderX()) / sliderW();
        f = Math.max(0, Math.min(1, f));
        StorageFinderConfig.radiusChunks = MIN_R + (int) Math.round(f * (MAX_R - MIN_R));
    }

    @Override
    public boolean method_25402(class_11909 event, boolean doubleClick) {
        double mx = event.comp_4798(), my = event.comp_4799();
        int btn = event.method_74245();

        if (btn == 0 && Ui.hit(mx, my, X, Y, W, HEAD_H)) {
            int i = (int) ((mx - X) / TAB_W);
            if (i >= 0 && i < TABS.length) tab = i;
            dragging = false;
            return true;
        }

        if (tab != 0) {
            return super.method_25402(event, doubleClick);
        }

        if (Ui.hit(mx, my, X, storageY(), W, ROW_H)) {
            if (btn == 0) {
                StorageFinderConfig.enabled = !StorageFinderConfig.enabled;
                StorageFinderConfig.save();
                return true;
            }
            if (btn == 1) {
                expanded = !expanded;
                return true;
            }
        }

        if (btn == 0 && Ui.hit(mx, my, X, freeY(), W, ROW_H)) {
            StorageFreeCam.toggle(class_310.method_1551());
            return true;
        }

        if (expanded && btn == 0) {
            if (Ui.hit(mx, my, sliderX() - 4, sliderY() - 6, sliderW() + 8, 20)) {
                dragging = true;
                setRadiusFromMouse(mx);
                return true;
            }

            StorageType[] types = StorageType.values();
            for (int i = 0; i < types.length; i++) {
                if (Ui.hit(mx, my, X + 8, blockY(i), W - 16, 13)) {
                    StorageFinderConfig.toggle(types[i]);
                    StorageFinderConfig.save();
                    return true;
                }
            }

            if (Ui.hit(mx, my, X + 8, playersY(), W - 16, 13)) {
                StorageFinderConfig.players = !StorageFinderConfig.players;
                StorageFinderConfig.save();
                return true;
            }

            if (Ui.hit(mx, my, X + 8, tracersY(), W - 16, 13)) {
                StorageFinderConfig.tracers = !StorageFinderConfig.tracers;
                StorageFinderConfig.save();
                return true;
            }
        }

        return super.method_25402(event, doubleClick);
    }

    @Override
    public boolean method_25403(class_11909 event, double dragX, double dragY) {
        if (dragging) {
            setRadiusFromMouse(event.comp_4798());
            return true;
        }
        return super.method_25403(event, dragX, dragY);
    }

    @Override
    public boolean method_25406(class_11909 event) {
        if (dragging) {
            dragging = false;
            StorageFinderConfig.save();
            return true;
        }
        return super.method_25406(event);
    }

    @Override
    public void method_25419() {
        StorageFinderConfig.save();
        super.method_25419();
    }
}
