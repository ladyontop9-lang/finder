package com.example.storagefinder;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Okno wpisywania klucza licencji. Po poprawnym wpisaniu klucz jest zapamiętany na stałe. */
public class LicenseScreen extends Screen {
    private static final int PW = 330, PH = 176;

    private final Screen parent;
    private EditBox box;
    private String msg = "";
    private int msgColor = KruxUi.MUT;

    public LicenseScreen(Screen parent) {
        super(Component.literal(Krux.NAME + " license"));
        this.parent = parent;
    }

    private int px() { return (this.width - PW) / 2; }
    private int py() { return (this.height - PH) / 2; }

    @Override
    protected void init() {
        int x = px(), y = py();
        box = new EditBox(this.font, x + 20, y + 70, PW - 40, 14, Component.literal("License key"));
        box.setBordered(false);
        box.setMaxLength(600);
        box.setTextColor(KruxUi.TEXT);
        box.setHint(Component.literal("KRUX-XXXXXXXX-XXXXXXXX-..."));
        addWidget(box);
        setInitialFocus(box);
    }

    private void activate() {
        String err = Krux.activate(box.getValue());
        if (err == null) {
            msg = "License activated. Thank you!";
            msgColor = KruxUi.GREEN;
            box.setValue("");
            this.minecraft.setScreen(parent);
        } else {
            msg = err;
            msgColor = KruxUi.RED;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, KruxUi.VEIL);
        int x = px(), y = py();
        KruxUi.panel(g, x, y, PW, PH);

        KruxUi.logo(g, this.font, x + 16, y + 14);
        g.drawString(this.font, Krux.NAME, x + 42, y + 16, KruxUi.TEXT);
        g.drawString(this.font, "License activation", x + 42, y + 27, KruxUi.MUT);
        String ks = "Public key: " + Krux.keyStatus();
        g.drawString(this.font, ks, x + PW - 16 - this.font.width(ks), y + 16, "OK".equals(Krux.keyStatus()) ? KruxUi.DIM : KruxUi.RED);

        String info = Krux.licensed()
                ? "Active: " + Krux.describe()
                : "Enter your license key to unlock the client.";
        g.drawString(this.font, info, x + 16, y + 48, Krux.licensed() ? KruxUi.GREEN : KruxUi.MUT);

        KruxUi.field(g, x + 16, y + 66, PW - 32, 22, box.isFocused());
        box.setX(x + 22);
        box.setY(y + 70);
        box.render(g, mx, my, pt);

        int by = y + 98;
        KruxUi.button(g, this.font, x + 16, by, 80, 20, "Paste", KruxUi.hit(mx, my, x + 16, by, 80, 20), false);
        KruxUi.button(g, this.font, x + 102, by, 110, 20, "Activate", KruxUi.hit(mx, my, x + 102, by, 110, 20), true);

        if (!msg.isEmpty()) g.drawString(this.font, msg, x + 16, y + 128, msgColor);

        g.drawString(this.font, "Build " + Krux.buildId(), x + 16, y + PH - 21, KruxUi.DIM);
        String close = Krux.licensed() ? "Close" : "Continue without client features";
        int cw = this.font.width(close);
        boolean ch = KruxUi.hit(mx, my, x + PW / 2 - cw / 2 - 4, y + PH - 24, cw + 8, 14);
        g.drawString(this.font, close, x + PW / 2 - cw / 2, y + PH - 21, ch ? KruxUi.TEXT : KruxUi.MUT);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int x = px(), y = py();
        if (event.button() == 0) {
            if (KruxUi.hit(mx, my, x + 16, y + 98, 80, 20)) {
                box.setValue(this.minecraft.keyboardHandler.getClipboard().trim());
                msg = "";
                return true;
            }
            if (KruxUi.hit(mx, my, x + 102, y + 98, 110, 20)) {
                activate();
                return true;
            }
            String close = Krux.licensed() ? "Close" : "Continue without client features";
            int cw = this.font.width(close);
            if (KruxUi.hit(mx, my, x + PW / 2 - cw / 2 - 4, y + PH - 24, cw + 8, 14)) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (k == 257 || k == 335) {            // Enter
            activate();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
