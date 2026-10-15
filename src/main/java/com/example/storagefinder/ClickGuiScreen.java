package com.example.storagefinder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Click GUI: przeciągane panele kategorii. LPM na module = włącz/wyłącz, PPM = ustawienia.
 * Motyw przeniesiony z projektu "Click GUI (Minecraft)".
 */
public class ClickGuiScreen extends Screen {
    private static final int PANEL = 0xFF2A2140, PANEL2 = 0xFF352A52, HD = 0xFF4B3A7A, ON = 0xFF7DD3C0,
            TX = 0xFFEFEAFF, MUT = 0xFF9A8FBA, BD = 0xFF0F0A1C, SET = 0xFF211936, CHKBG = 0xFF14102A;
    private static final int PW = 150, HDR = 16, ROW = 15, BOOL_H = 14, SLIDER_H = 24;

    private static final class Panel {
        final String title;
        final List<Module> mods = new ArrayList<>();
        final Set<Module> open = new HashSet<>();
        int x, y;
        boolean collapsed;

        Panel(String title) { this.title = title; }
    }

    private enum Kind { HEADER, COLLAPSE, MODULE, CHECK, SLIDER }

    private record Hit(Kind kind, int x, int y, int w, int h, Panel panel, Module mod, Setting setting) {
        boolean inside(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static List<Panel> panels;

    private final List<Hit> hits = new ArrayList<>();
    private EditBox search;
    private Panel dragPanel;
    private int dragDX, dragDY;
    private Hit dragSlider;

    public ClickGuiScreen() {
        super(Component.literal("Click GUI"));
    }

    @Override
    protected void init() {
        if (panels == null) {
            panels = new ArrayList<>();
            int i = 0;
            for (String cat : Modules.CATEGORIES) {
                Panel p = new Panel(cat);
                p.x = 16 + i * (PW + 14);
                p.y = 16 + (i % 2) * 14;
                for (Module m : Modules.ALL) {
                    if (m.category.equals(cat)) p.mods.add(m);
                }
                panels.add(p);
                i++;
            }
        }
        search = new EditBox(this.font, this.width / 2 - 60, this.height - 28, 120, 14, Component.literal("Search"));
        search.setHint(Component.literal("Search module..."));
        addRenderableWidget(search);
    }

    private static int settingsHeight(Module m) {
        int h = 8;
        for (Setting s : m.settings) h += (s instanceof Setting.Slider) ? SLIDER_H : BOOL_H;
        return h;
    }

    private static int contentHeight(Panel p) {
        int h = 0;
        for (Module m : p.mods) {
            h += ROW;
            if (p.open.contains(m)) h += settingsHeight(m);
        }
        return h;
    }

    private void text(GuiGraphics g, String s, int x, int y, int c) {
        g.drawString(this.font, s, x, y, c);
    }

    private void textRight(GuiGraphics g, String s, int xr, int y, int c) {
        g.drawString(this.font, s, xr - this.font.width(s), y, c);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        hits.clear();

        String q = search.getValue().toLowerCase();
        for (Panel p : panels) drawPanel(g, p, mx, my, q);

        text(g, "Search", this.width / 2 - 60 - 40, this.height - 25, MUT);
        text(g, "LMB: toggle   RMB: settings   Drag headers to move", 8, this.height - 14, MUT);
    }

    private void drawPanel(GuiGraphics g, Panel p, int mx, int my, String q) {
        int x = p.x, y = p.y, w = PW;
        int totalH = HDR + (p.collapsed ? 0 : contentHeight(p));

        g.fill(x + 4, y + 4, x + w + 6, y + totalH + 6, BD);       // twardy cień
        g.fill(x - 2, y - 2, x + w + 2, y + totalH + 2, BD);       // ramka
        g.fill(x, y, x + w, y + HDR, HD);
        g.fill(x, y + HDR - 1, x + w, y + HDR, BD);
        text(g, p.title, x + 6, y + 4, TX);
        textRight(g, p.collapsed ? "+" : "-", x + w - 6, y + 4, MUT);
        hits.add(new Hit(Kind.HEADER, x, y, w - 18, HDR, p, null, null));
        hits.add(new Hit(Kind.COLLAPSE, x + w - 18, y, 18, HDR, p, null, null));

        if (p.collapsed) return;

        int cy = y + HDR;
        for (Module m : p.mods) {
            boolean on = m.isEnabled();
            boolean hover = mx >= x && mx < x + w && my >= cy && my < cy + ROW;

            g.fill(x, cy, x + w, cy + ROW, (on || hover) ? PANEL2 : PANEL);
            if (on) g.fill(x, cy, x + 3, cy + ROW, ON);
            text(g, m.name, x + 8, cy + 4, on ? ON : (hover ? TX : MUT));
            if (!m.settings.isEmpty()) textRight(g, "...", x + w - 6, cy + 4, MUT);

            if (!q.isEmpty() && m.name.toLowerCase().contains(q)) {
                g.fill(x, cy, x + w, cy + 1, ON);
                g.fill(x, cy + ROW - 1, x + w, cy + ROW, ON);
                g.fill(x, cy, x + 1, cy + ROW, ON);
                g.fill(x + w - 1, cy, x + w, cy + ROW, ON);
            } else {
                g.fill(x, cy + ROW - 1, x + w, cy + ROW, BD);
            }
            hits.add(new Hit(Kind.MODULE, x, cy, w, ROW, p, m, null));
            cy += ROW;

            if (p.open.contains(m)) {
                int sh = settingsHeight(m);
                g.fill(x, cy, x + w, cy + sh, SET);
                g.fill(x, cy + sh - 1, x + w, cy + sh, BD);
                int sy = cy + 4;
                for (Setting s : m.settings) {
                    if (s instanceof Setting.Bool b) {
                        boolean v = b.get();
                        g.fill(x + 8, sy + 3, x + 16, sy + 11, BD);
                        g.fill(x + 9, sy + 4, x + 15, sy + 10, v ? b.color : CHKBG);
                        text(g, b.name, x + 22, sy + 3, v ? TX : MUT);
                        hits.add(new Hit(Kind.CHECK, x, sy, w, BOOL_H, p, m, b));
                        sy += BOOL_H;
                    } else if (s instanceof Setting.Slider sl) {
                        text(g, sl.name, x + 8, sy + 1, MUT);
                        textRight(g, sl.display(), x + w - 8, sy + 1, TX);
                        int tx0 = x + 8, tw = w - 16;
                        g.fill(tx0, sy + 12, tx0 + tw, sy + 18, BD);
                        g.fill(tx0 + 1, sy + 13, tx0 + tw - 1, sy + 17, CHKBG);
                        double frac = (sl.get() - sl.min) / (sl.max - sl.min);
                        frac = Math.max(0, Math.min(1, frac));
                        int fw = (int) Math.round((tw - 2) * frac);
                        g.fill(tx0 + 1, sy + 13, tx0 + 1 + fw, sy + 17, ON);
                        g.fill(tx0 + fw, sy + 10, tx0 + fw + 3, sy + 20, TX);
                        hits.add(new Hit(Kind.SLIDER, tx0, sy + 8, tw, 14, p, m, sl));
                        sy += SLIDER_H;
                    }
                }
                cy += sh;
            }
        }
    }

    private void applySlider(Hit h, double mx) {
        Setting.Slider s = (Setting.Slider) h.setting();
        double frac = Math.max(0, Math.min(1, (mx - h.x()) / h.w()));
        s.set(s.min + frac * (s.max - s.min));
    }

    private void bringToFront(Panel p) {
        panels.remove(p);
        panels.add(p);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int btn = event.button();

        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.inside(mx, my)) continue;

            bringToFront(h.panel());
            switch (h.kind()) {
                case HEADER -> {
                    if (btn == 0) {
                        dragPanel = h.panel();
                        dragDX = (int) mx - h.panel().x;
                        dragDY = (int) my - h.panel().y;
                    } else if (btn == 1) {
                        h.panel().collapsed = !h.panel().collapsed;
                    }
                }
                case COLLAPSE -> {
                    if (btn == 0) h.panel().collapsed = !h.panel().collapsed;
                }
                case MODULE -> {
                    if (btn == 0) {
                        h.mod().toggle();
                        StorageFinderConfig.save();
                    } else if (btn == 1 && !h.mod().settings.isEmpty()) {
                        Set<Module> open = h.panel().open;
                        if (!open.remove(h.mod())) open.add(h.mod());
                    }
                }
                case CHECK -> {
                    if (btn == 0) {
                        Setting.Bool b = (Setting.Bool) h.setting();
                        b.set(!b.get());
                        StorageFinderConfig.save();
                    }
                }
                case SLIDER -> {
                    if (btn == 0) {
                        dragSlider = h;
                        applySlider(h, mx);
                    }
                }
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragPanel != null) {
            dragPanel.x = Math.max(0, Math.min(this.width - PW, (int) event.x() - dragDX));
            dragPanel.y = Math.max(0, Math.min(this.height - HDR, (int) event.y() - dragDY));
            return true;
        }
        if (dragSlider != null) {
            applySlider(dragSlider, event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean handled = dragPanel != null || dragSlider != null;
        if (dragSlider != null) StorageFinderConfig.save();
        dragPanel = null;
        dragSlider = null;
        if (handled) return true;
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        StorageFinderConfig.save();
        super.onClose();
    }
}
