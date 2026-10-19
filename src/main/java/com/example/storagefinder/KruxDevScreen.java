package com.example.storagefinder;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Narzędzia dewelopera:
 *  1) konfiguracja: generowanie pary kluczy (prywatny zostaje w config/krux/dev.key),
 *  2) wystawianie licencji lifetime / czasowych (zapis do config/krux/issued.log).
 */
public class KruxDevScreen extends Screen {
    private static final int PW = 360, PH = 214;

    private final Screen parent;
    private EditBox outBox, daysBox, noteBox;
    private int mode = 1;                 // 0 Lifetime, 1 30 days, 2 Custom
    private String msg = "";
    private int msgColor = KruxUi.MUT;
    private String generatedPublic = "";

    public KruxDevScreen(Screen parent) {
        super(Component.literal(Krux.NAME + " developer"));
        this.parent = parent;
    }

    private boolean setupMode() {
        return !Krux.hasPrivateKey() || "mismatch".equals(Krux.devKeyState());
    }

    private int px() { return (this.width - PW) / 2; }
    private int py() { return (this.height - PH) / 2; }

    @Override
    protected void init() {
        int x = px(), y = py();
        outBox = new EditBox(this.font, x + 22, y + 150, PW - 44, 14, Component.literal("Output"));
        outBox.setBordered(false);
        outBox.setMaxLength(700);
        outBox.setTextColor(KruxUi.TEXT);
        addWidget(outBox);

        daysBox = new EditBox(this.font, x + 250, y + 71, 40, 14, Component.literal("Days"));
        daysBox.setBordered(false);
        daysBox.setMaxLength(4);
        daysBox.setValue("30");
        daysBox.setTextColor(KruxUi.TEXT);
        addWidget(daysBox);

        noteBox = new EditBox(this.font, x + 22, y + 108, PW - 44, 14, Component.literal("Note"));
        noteBox.setBordered(false);
        noteBox.setMaxLength(80);
        noteBox.setTextColor(KruxUi.TEXT);
        noteBox.setHint(Component.literal("Buyer / note (saved to issued.log)"));
        addWidget(noteBox);
    }

    private void copy(String s) {
        this.minecraft.keyboardHandler.setClipboard(s);
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, KruxUi.VEIL);
        int x = px(), y = py();
        KruxUi.panel(g, x, y, PW, PH);
        KruxUi.logo(g, this.font, x + 16, y + 14);
        g.drawString(this.font, Krux.NAME, x + 42, y + 16, KruxUi.TEXT);
        g.drawString(this.font, "Developer tools", x + 42, y + 27, KruxUi.MUT);
        String ks = "Public key: " + Krux.keyStatus();
        g.drawString(this.font, ks, x + PW - 16 - this.font.width(ks), y + 16, "OK".equals(Krux.keyStatus()) ? KruxUi.GREEN : KruxUi.RED);

        if (setupMode()) drawSetup(g, mx, my, pt, x, y);
        else drawIssue(g, mx, my, pt, x, y);

        if (!msg.isEmpty()) g.drawString(this.font, msg, x + 16, y + PH - 38, msgColor);
        g.drawString(this.font, "Build " + Krux.buildId(), x + 16, y + PH - 24, KruxUi.DIM);

        boolean ch = KruxUi.hit(mx, my, x + PW - 70, y + PH - 28, 54, 16);
        KruxUi.button(g, this.font, x + PW - 70, y + PH - 28, 54, 16, "Close", ch, false);
    }

    private void drawSetup(GuiGraphics g, int mx, int my, float pt, int x, int y) {
        String text = Krux.hasPrivateKey()
                ? "Your dev.key does NOT match the public key built into this jar. Put the right dev.key back into "
                + "config/krux, or generate new keys (the old file is kept as dev.key.<time>.bak). New keys mean pasting "
                + "the new public key into KruxPublicKey.java and rebuilding."
                : "No developer key yet. Generate your signing keys: the PRIVATE key is saved to "
                + "config/krux/dev.key (keep it secret, make a backup) and the PUBLIC key is copied to your clipboard. "
                + "Paste the public key into KruxPublicKey.java, rebuild the mod and send that jar to customers.";
        List<FormattedCharSequence> lines = this.font.split(Component.literal(text), PW - 32);
        int ly = y + 46;
        for (FormattedCharSequence l : lines) {
            g.drawString(this.font, l, x + 16, ly, KruxUi.MUT);
            ly += 10;
        }

        KruxUi.button(g, this.font, x + 16, y + 112, 110, 20, "Generate keys",
                KruxUi.hit(mx, my, x + 16, y + 112, 110, 20), true);
        if (generatedPublic.isEmpty()) {
            List<FormattedCharSequence> det = this.font.split(Component.literal(Krux.keyDetail()), PW - 32);
            int dy = y + 140;
            for (FormattedCharSequence l : det) {
                g.drawString(this.font, l, x + 16, dy, KruxUi.MUT);
                dy += 10;
            }
        }

        if (!generatedPublic.isEmpty()) {
            g.drawString(this.font, "Public key (copied):", x + 16, y + 140, KruxUi.TEXT);
            KruxUi.field(g, x + 16, y + 150, PW - 32 - 62, 22, false);
            outBox.setValue(generatedPublic);
            outBox.setX(x + 22);
            outBox.setY(y + 154);
            outBox.setWidth(PW - 44 - 62);
            outBox.render(g, mx, my, pt);
            KruxUi.button(g, this.font, x + PW - 16 - 56, y + 150, 56, 22, "Copy",
                    KruxUi.hit(mx, my, x + PW - 16 - 56, y + 150, 56, 22), false);
        }
    }

    private void drawIssue(GuiGraphics g, int mx, int my, float pt, int x, int y) {
        String[] labels = {"Lifetime", "30 days", "Custom"};
        g.drawString(this.font, "License type", x + 16, y + 48, KruxUi.TEXT);
        int bx = x + 16;
        for (int i = 0; i < labels.length; i++) {
            int w = 70;
            boolean sel = mode == i;
            KruxUi.button(g, this.font, bx, y + 60, w, 20, labels[i], KruxUi.hit(mx, my, bx, y + 60, w, 20), sel);
            bx += w + 6;
        }
        if (mode == 2) {
            g.drawString(this.font, "Days:", x + 232, y + 66, KruxUi.MUT);
            KruxUi.field(g, x + 246, y + 62, 48, 18, daysBox.isFocused());
            daysBox.setX(x + 252);
            daysBox.setY(y + 64);
            daysBox.render(g, mx, my, pt);
        }

        KruxUi.field(g, x + 16, y + 100, PW - 32, 22, noteBox.isFocused());
        noteBox.setX(x + 22);
        noteBox.setY(y + 104);
        noteBox.setWidth(PW - 44);
        noteBox.render(g, mx, my, pt);

        KruxUi.button(g, this.font, x + 16, y + 128, 120, 20, "Generate license",
                KruxUi.hit(mx, my, x + 16, y + 128, 120, 20), true);
        KruxUi.button(g, this.font, x + 142, y + 128, 100, 20, "Copy public key",
                KruxUi.hit(mx, my, x + 142, y + 128, 100, 20), false);

        KruxUi.field(g, x + 16, y + 154, PW - 32 - 62, 22, false);
        outBox.setX(x + 22);
        outBox.setY(y + 158);
        outBox.setWidth(PW - 44 - 62);
        outBox.render(g, mx, my, pt);
        KruxUi.button(g, this.font, x + PW - 16 - 56, y + 154, 56, 22, "Copy",
                KruxUi.hit(mx, my, x + PW - 16 - 56, y + 154, 56, 22), false);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int x = px(), y = py();
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);

        if (KruxUi.hit(mx, my, x + PW - 70, y + PH - 28, 54, 16)) {
            onClose();
            return true;
        }

        if (setupMode()) {
            if (KruxUi.hit(mx, my, x + 16, y + 112, 110, 20)) {
                try {
                    generatedPublic = Krux.generateKeys();
                    copy(generatedPublic);
                    msg = "Keys generated. Public key copied to clipboard.";
                    msgColor = KruxUi.GREEN;
                } catch (Exception e) {
                    msg = "Could not generate keys: " + e.getMessage();
                    msgColor = KruxUi.RED;
                }
                return true;
            }
            if (!generatedPublic.isEmpty() && KruxUi.hit(mx, my, x + PW - 16 - 56, y + 150, 56, 22)) {
                copy(generatedPublic);
                msg = "Public key copied.";
                msgColor = KruxUi.GREEN;
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        for (int i = 0; i < 3; i++) {
            if (KruxUi.hit(mx, my, x + 16 + i * 76, y + 60, 70, 20)) {
                mode = i;
                return true;
            }
        }
        if (KruxUi.hit(mx, my, x + 16, y + 128, 120, 20)) {
            try {
                boolean life = mode == 0;
                int days = mode == 1 ? 30 : parseDays();
                String token = Krux.issue(life, days, noteBox.getValue());
                outBox.setValue(token);
                outBox.moveCursorToStart(false);
                copy(token);
                msg = "License generated and copied. Saved to config/krux/issued.log.";
                msgColor = KruxUi.GREEN;
            } catch (Exception e) {
                msg = "Could not generate: " + e.getMessage();
                msgColor = KruxUi.RED;
            }
            return true;
        }
        if (KruxUi.hit(mx, my, x + 142, y + 128, 100, 20)) {
            String pub = Krux.devPublicKey();
            copy(pub);
            msg = pub.isEmpty() ? "No public key stored in dev.key." : "Public key copied.";
            msgColor = pub.isEmpty() ? KruxUi.RED : KruxUi.GREEN;
            return true;
        }
        if (KruxUi.hit(mx, my, x + PW - 16 - 56, y + 154, 56, 22)) {
            copy(outBox.getValue());
            msg = "Copied.";
            msgColor = KruxUi.GREEN;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private int parseDays() {
        try {
            return Math.max(1, Integer.parseInt(daysBox.getValue().trim()));
        } catch (Exception e) {
            return 30;
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
