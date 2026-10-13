package com.example.storagefinder;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Główne menu: lista modułów. LPM = włącz/wyłącz, PPM = ustawienia. */
public class StorageFinderScreen extends Screen {
    private static final int PW = 320, PH = 168;

    public StorageFinderScreen() {
        super(Component.literal("Storage Finder"));
    }

    private int px() { return (this.width - PW) / 2; }
    private int py() { return Math.max(4, (this.height - PH) / 2); }

    private int cardX() { return px() + 12; }
    private int cardY() { return py() + 58; }
    private static final int CARD_W = PW - 24, CARD_H = 56;

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int x = px(), y = py();
        Ui.panel(g, x, y, PW, PH);

        Ui.text(g, this.font, "Storage Finder", x + 16, y + 14, Ui.TEXT);
        Ui.text(g, this.font, "Highlights storages through walls", x + 16, y + 28, Ui.MUTED);

        int cx = cardX(), cy = cardY();
        boolean hover = Ui.hit(mouseX, mouseY, cx, cy, CARD_W, CARD_H);
        Ui.rrect(g, cx, cy, CARD_W, CARD_H, 6, hover ? Ui.CARD_HOVER : Ui.CARD);

        Ui.rrect(g, cx + 10, cy + 12, 32, 32, 6, StorageFinderConfig.enabled ? Ui.ACCENT : Ui.OFF);
        Ui.textCenter(g, this.font, "S", cx + 26, cy + 24, Ui.WHITE);

        Ui.text(g, this.font, "Storage Finder", cx + 52, cy + 14, Ui.TEXT);
        Ui.text(g, this.font, "Right-click for settings", cx + 52, cy + 29, Ui.MUTED);

        Ui.toggle(g, cx + CARD_W - 46, cy + CARD_H / 2 - 8, StorageFinderConfig.enabled);

        Ui.textCenter(g, this.font, "Left-click: toggle   Right-click: settings", x + PW / 2, y + PH - 28, Ui.MUTED);
        Ui.textCenter(g, this.font, "Press ESC to close", x + PW / 2, y + PH - 16, Ui.OFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int btn = event.button();

        if (Ui.hit(mx, my, cardX(), cardY(), CARD_W, CARD_H)) {
            if (btn == 0) {
                StorageFinderConfig.enabled = !StorageFinderConfig.enabled;
                StorageFinderConfig.save();
                return true;
            }
            if (btn == 1) {
                this.minecraft.setScreen(new StorageSettingsScreen(this));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        StorageFinderConfig.save();
        super.onClose();
    }
}
