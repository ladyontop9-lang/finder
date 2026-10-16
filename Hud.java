package com.example.storagefinder;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** ArrayList: lista włączonych modułów w prawym górnym rogu. */
public final class Hud {
    private static List<Module> order;

    private Hud() {}

    public static void init() {
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> render(graphics));
    }

    private static void render(GuiGraphics g) {
        if (!StorageFinderConfig.arrayList) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;

        if (order == null) {
            order = new ArrayList<>();
            for (Module m : Modules.ALL) {
                if (m != Modules.ARRAY_LIST) order.add(m);
            }
            order.sort((a, b) -> mc.font.width(b.name) - mc.font.width(a.name));
        }

        boolean bg = StorageFinderConfig.alBackground;
        boolean bar = StorageFinderConfig.alBar;
        int sw = g.guiWidth();
        int lh = mc.font.lineHeight + 3;
        int y = 4;
        int rightEdge = sw - 4 - (bar ? 3 : 0);

        if (StorageFinderConfig.alTitle) {
            y = row(g, mc, "Storage Finder", rightEdge, y, lh, bg, bar, 0xFF5E4A96, 0xFF9A8FBA);
        }
        for (Module m : order) {
            if (m.isEnabled()) {
                y = row(g, mc, m.name, rightEdge, y, lh, bg, bar, 0xFF7DD3C0, 0xFFEFEAFF);
            }
        }
    }

    private static int row(GuiGraphics g, Minecraft mc, String text, int rightEdge, int y, int lh,
                           boolean bg, boolean bar, int barColor, int textColor) {
        int w = mc.font.width(text) + 8;
        if (bg) g.fill(rightEdge - w, y, rightEdge, y + lh, 0xB80F0A1C);
        if (bar) g.fill(rightEdge, y, rightEdge + 3, y + lh, barColor);
        g.drawString(mc.font, text, rightEdge - w + 4, y + 2, textColor);
        return y + lh;
    }
}
