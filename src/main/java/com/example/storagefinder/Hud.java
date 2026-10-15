package com.example.storagefinder;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Lista włączonych modułów w prawym górnym rogu (jak w projekcie Click GUI). */
public final class Hud {
    private Hud() {}

    public static void init() {
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> render(graphics));
    }

    private static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;

        List<String> names = new ArrayList<>();
        for (Module m : Modules.ALL) {
            if (m.isEnabled()) names.add(m.name);
        }
        names.sort((a, b) -> mc.font.width(b) - mc.font.width(a));

        int sw = g.guiWidth();
        int y = 4;
        int lh = mc.font.lineHeight + 3;

        String title = "Storage Finder";
        int tw = mc.font.width(title) + 8;
        g.fill(sw - 4 - tw - 3, y, sw - 4 - 3, y + lh, 0xB80F0A1C);
        g.fill(sw - 4 - 3, y, sw - 4, y + lh, 0xFF5E4A96);
        g.drawString(mc.font, title, sw - 4 - 3 - tw + 4, y + 2, 0xFF9A8FBA);
        y += lh;

        for (String n : names) {
            int w = mc.font.width(n) + 8;
            g.fill(sw - 4 - w - 3, y, sw - 4 - 3, y + lh, 0xB80F0A1C);
            g.fill(sw - 4 - 3, y, sw - 4, y + lh, 0xFF7DD3C0);
            g.drawString(mc.font, n, sw - 4 - 3 - w + 4, y + 2, 0xFFEFEAFF);
            y += lh;
        }
    }
}
