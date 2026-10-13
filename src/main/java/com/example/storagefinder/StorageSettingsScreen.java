package com.example.storagefinder;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Ustawienia Storage Finder: typy bloków, gracze i zasięg. */
public class StorageSettingsScreen extends Screen {
    private static final int PW = 420, ROW_H = 26, GAP = 12;
    private static final int COL_W = (PW - 24 - GAP) / 2;
    private static final int MIN_R = 2, MAX_R = 16;

    private final Screen parent;
    private boolean dragging = false;

    public StorageSettingsScreen(Screen parent) {
        super(Component.literal("Storage Finder settings"));
        this.parent = parent;
    }

    private int px() { return (this.width - PW) / 2; }
    private int py() { return Math.max(4, (this.height - ph()) / 2); }

    private int rows() { return (StorageType.values().length + 1) / 2; }
    private int gridY() { return py() + 58; }
    private int cellX(int i) { return px() + 12 + (i % 2) * (COL_W + GAP); }
    private int cellY(int i) { return gridY() + (i / 2) * ROW_H; }
    private int gridEnd() { return gridY() + rows() * ROW_H; }
    private int playersY() { return gridEnd() + 22; }
    private int sliderX() { return px() + 16; }
    private int sliderW() { return PW - 32; }
    private int tracersY() { return playersY() + 26; }
    private int sliderY() { return tracersY() + 24 + 30; }
    private int doneY() { return sliderY() + 20; }
    private int ph() { return 58 + rows() * ROW_H + 22 + 26 + 24 + 30 + 20 + 22 + 14; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int x = px(), y = py();
        Ui.panel(g, x, y, PW, ph());

        boolean backHover = Ui.hit(mouseX, mouseY, x + 8, y + 8, 56, 20);
        Ui.text(g, this.font, "< Back", x + 16, y + 14, backHover ? Ui.WHITE : Ui.MUTED);
        Ui.textCenter(g, this.font, "Storage Finder", x + PW / 2, y + 14, Ui.TEXT);
        Ui.textRight(g, this.font, "Settings", x + PW - 16, y + 14, Ui.MUTED);

        Ui.text(g, this.font, "BLOCKS", x + 16, y + 44, Ui.MUTED);

        StorageType[] types = StorageType.values();
        for (int i = 0; i < types.length; i++) {
            StorageType t = types[i];
            int cx = cellX(i), cy = cellY(i);
            boolean hover = Ui.hit(mouseX, mouseY, cx, cy, COL_W, ROW_H - 2);
            boolean on = StorageFinderConfig.isOn(t);

            Ui.rrect(g, cx, cy, COL_W, ROW_H - 2, 5, hover ? Ui.CARD_HOVER : Ui.CARD);
            Ui.rrect(g, cx + 8, cy + 7, 10, 10, 3, on ? t.argb() : Ui.OFF);
            Ui.text(g, this.font, t.label, cx + 24, cy + 8, on ? Ui.TEXT : Ui.MUTED);

            String count = (StorageFinderConfig.enabled && on) ? String.valueOf(StorageScanner.count(t)) : "-";
            Ui.textRight(g, this.font, count, cx + COL_W - 46, cy + 8, Ui.MUTED);
            Ui.toggle(g, cx + COL_W - 40, cy + 4, on);
        }

        Ui.text(g, this.font, "EXTRAS", x + 16, gridEnd() + 8, Ui.MUTED);

        int py = playersY();
        int rx = x + 12, rw = PW - 24;
        boolean pHover = Ui.hit(mouseX, mouseY, rx, py, rw, 24);
        boolean pOn = StorageFinderConfig.players;
        Ui.rrect(g, rx, py, rw, 24, 5, pHover ? Ui.CARD_HOVER : Ui.CARD);
        Ui.rrect(g, rx + 8, py + 7, 10, 10, 3, pOn ? Ui.PLAYER : Ui.OFF);
        Ui.text(g, this.font, "Players", rx + 24, py + 8, pOn ? Ui.TEXT : Ui.MUTED);
        Ui.text(g, this.font, "Highlight other players through walls",
                rx + 24 + this.font.width("Players") + 10, py + 8, Ui.MUTED);
        String pc = (StorageFinderConfig.enabled && pOn) ? String.valueOf(StorageScanner.playerCount()) : "-";
        Ui.textRight(g, this.font, pc, rx + rw - 52, py + 8, Ui.MUTED);
        Ui.toggle(g, rx + rw - 44, py + 4, pOn);

        int ty = tracersY();
        boolean tHover = Ui.hit(mouseX, mouseY, rx, ty, rw, 24);
        boolean tOn = StorageFinderConfig.tracers;
        Ui.rrect(g, rx, ty, rw, 24, 5, tHover ? Ui.CARD_HOVER : Ui.CARD);
        Ui.rrect(g, rx + 8, ty + 7, 10, 10, 3, tOn ? Ui.ACCENT : Ui.OFF);
        Ui.text(g, this.font, "Tracers", rx + 24, ty + 8, tOn ? Ui.TEXT : Ui.MUTED);
        Ui.text(g, this.font, "Lines from your crosshair to every highlight",
                rx + 24 + this.font.width("Tracers") + 10, ty + 8, Ui.MUTED);
        Ui.toggle(g, rx + rw - 44, ty + 4, tOn);

        int sy = sliderY();
        Ui.text(g, this.font, "Scan range", x + 16, sy - 16, Ui.TEXT);
        Ui.textRight(g, this.font, StorageFinderConfig.radiusChunks + " chunks", x + PW - 16, sy - 16, Ui.MUTED);

        int sx = sliderX(), sw = sliderW();
        Ui.rrect(g, sx, sy, sw, 8, 4, Ui.OFF);
        float frac = (StorageFinderConfig.radiusChunks - MIN_R) / (float) (MAX_R - MIN_R);
        int fillW = Math.max(8, Math.round(sw * frac));
        Ui.rrect(g, sx, sy, fillW, 8, 4, Ui.ACCENT);
        int kx = sx + Math.round((sw - 12) * frac);
        Ui.rrect(g, kx, sy - 2, 12, 12, 6, Ui.WHITE);

        int dy = doneY() + 12;
        boolean doneHover = Ui.hit(mouseX, mouseY, x + 12, dy, PW - 24, 22);
        Ui.rrect(g, x + 12, dy, PW - 24, 22, 6, doneHover ? Ui.ACCENT_HOVER : Ui.ACCENT);
        Ui.textCenter(g, this.font, "Done", x + PW / 2, dy + 7, Ui.WHITE);
    }

    private void setRadiusFromMouse(double mx) {
        double f = (mx - sliderX()) / sliderW();
        f = Math.max(0, Math.min(1, f));
        StorageFinderConfig.radiusChunks = MIN_R + (int) Math.round(f * (MAX_R - MIN_R));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int btn = event.button();
        int x = px();

        if (btn == 0) {
            if (Ui.hit(mx, my, x + 8, py() + 8, 56, 20) || Ui.hit(mx, my, x + 12, doneY() + 12, PW - 24, 22)) {
                StorageFinderConfig.save();
                this.minecraft.setScreen(parent);
                return true;
            }

            StorageType[] types = StorageType.values();
            for (int i = 0; i < types.length; i++) {
                if (Ui.hit(mx, my, cellX(i), cellY(i), COL_W, ROW_H - 2)) {
                    StorageFinderConfig.toggle(types[i]);
                    StorageFinderConfig.save();
                    return true;
                }
            }

            if (Ui.hit(mx, my, x + 12, playersY(), PW - 24, 24)) {
                StorageFinderConfig.players = !StorageFinderConfig.players;
                StorageFinderConfig.save();
                return true;
            }

            if (Ui.hit(mx, my, x + 12, tracersY(), PW - 24, 24)) {
                StorageFinderConfig.tracers = !StorageFinderConfig.tracers;
                StorageFinderConfig.save();
                return true;
            }

            if (Ui.hit(mx, my, sliderX() - 4, sliderY() - 6, sliderW() + 8, 20)) {
                dragging = true;
                setRadiusFromMouse(mx);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging) {
            setRadiusFromMouse(event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            StorageFinderConfig.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        StorageFinderConfig.save();
        super.onClose();
    }
}
