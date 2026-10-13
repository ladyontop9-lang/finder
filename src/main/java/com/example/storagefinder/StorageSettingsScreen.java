package com.example.storagefinder;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Ustawienia Storage Finder: wybór typów magazynów i zasięgu. */
public class StorageSettingsScreen extends Screen {
    private static final int PW = 320, ROW_H = 26, PH = 270;
    private static final int MIN_R = 2, MAX_R = 16;

    private final Screen parent;
    private boolean dragging = false;

    public StorageSettingsScreen(Screen parent) {
        super(Component.literal("Storage Finder settings"));
        this.parent = parent;
    }

    private int px() { return (this.width - PW) / 2; }
    private int py() { return Math.max(4, (this.height - PH) / 2); }

    private int rowY(int i) { return py() + 46 + i * ROW_H; }
    private int sliderX() { return px() + 16; }
    private int sliderW() { return PW - 32; }
    private int sliderY() { return py() + 46 + StorageType.values().length * ROW_H + 30; }
    private int doneY() { return py() + PH - 34; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int x = px(), y = py();
        Ui.panel(g, x, y, PW, PH);

        boolean backHover = Ui.hit(mouseX, mouseY, x + 8, y + 8, 56, 20);
        Ui.text(g, this.font, "< Back", x + 16, y + 14, backHover ? Ui.WHITE : Ui.MUTED);
        Ui.textCenter(g, this.font, "Storage Finder", x + PW / 2, y + 14, Ui.TEXT);
        Ui.textRight(g, this.font, "Settings", x + PW - 16, y + 14, Ui.MUTED);

        StorageType[] types = StorageType.values();
        for (int i = 0; i < types.length; i++) {
            StorageType t = types[i];
            int ry = rowY(i);
            int rx = x + 12, rw = PW - 24;
            boolean hover = Ui.hit(mouseX, mouseY, rx, ry, rw, ROW_H - 2);
            boolean on = StorageFinderConfig.isOn(t);

            Ui.rrect(g, rx, ry, rw, ROW_H - 2, 5, hover ? Ui.CARD_HOVER : Ui.CARD);
            Ui.rrect(g, rx + 8, ry + 7, 10, 10, 3, on ? t.argb() : Ui.OFF);
            Ui.text(g, this.font, t.label, rx + 26, ry + 8, on ? Ui.TEXT : Ui.MUTED);

            String count = (StorageFinderConfig.enabled && on) ? String.valueOf(StorageScanner.count(t)) : "-";
            Ui.textRight(g, this.font, count, rx + rw - 52, ry + 8, Ui.MUTED);
            Ui.toggle(g, rx + rw - 44, ry + 4, on);
        }

        int sy = sliderY();
        Ui.text(g, this.font, "Scan range", x + 16, sy - 18, Ui.TEXT);
        Ui.textRight(g, this.font, StorageFinderConfig.radiusChunks + " chunks", x + PW - 16, sy - 18, Ui.MUTED);

        int sx = sliderX(), sw = sliderW();
        Ui.rrect(g, sx, sy, sw, 8, 4, Ui.OFF);
        float frac = (StorageFinderConfig.radiusChunks - MIN_R) / (float) (MAX_R - MIN_R);
        int fillW = Math.max(8, Math.round(sw * frac));
        Ui.rrect(g, sx, sy, fillW, 8, 4, Ui.ACCENT);
        int kx = sx + Math.round((sw - 12) * frac);
        Ui.rrect(g, kx, sy - 2, 12, 12, 6, Ui.WHITE);

        int dy = doneY();
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
            if (Ui.hit(mx, my, x + 8, py() + 8, 56, 20) || Ui.hit(mx, my, x + 12, doneY(), PW - 24, 22)) {
                StorageFinderConfig.save();
                this.minecraft.setScreen(parent);
                return true;
            }

            StorageType[] types = StorageType.values();
            for (int i = 0; i < types.length; i++) {
                if (Ui.hit(mx, my, x + 12, rowY(i), PW - 24, ROW_H - 2)) {
                    StorageFinderConfig.toggle(types[i]);
                    StorageFinderConfig.save();
                    return true;
                }
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
