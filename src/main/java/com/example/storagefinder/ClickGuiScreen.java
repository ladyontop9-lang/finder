package com.example.storagefinder;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Click GUI z animacjami. LPM = włącz/wyłącz moduł, PPM = ustawienia, ŚPM = bind klawisza.
 * Motyw z projektu "Click GUI (Minecraft)".
 */
public class ClickGuiScreen extends Screen {
    private static final int PANEL = 0xFF2A2140, PANEL2 = 0xFF352A52, HD = 0xFF4B3A7A, ON = 0xFF7DD3C0,
            TX = 0xFFEFEAFF, MUT = 0xFF9A8FBA, BD = 0xFF0F0A1C, SET = 0xFF211936, CHKBG = 0xFF14102A;
    private static final int PW = 170, HDR = 16, ROW = 15, BOOL_H = 14, SLIDER_H = 24, COLOR_H = 50;

    private static final class Panel {
        final String title;
        final List<Module> mods = new ArrayList<>();
        int x, y;
        boolean collapsed;
        float collapseAnim = 1f;

        Panel(String title) { this.title = title; }
    }

    private enum Kind { HEADER, COLLAPSE, MODULE, CHECK, SLIDER, COLOR }

    private record Hit(Kind kind, int x, int y, int w, int h, Panel panel, Module mod, Setting setting, int channel) {
        boolean inside(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static List<Panel> panels;

    private final List<Hit> hits = new ArrayList<>();
    private EditBox search;
    private Panel dragPanel;
    private int dragDX, dragDY;
    private Hit dragHit;
    private Module bindingModule;

    private long lastNanos = 0;
    private float intro = 0f;
    private int curOy = 0;

    public ClickGuiScreen() {
        super(Component.literal("Click GUI"));
    }

    // ------------------------------------------------------------------ helpers

    private static float ease(float t) { return t * t * (3f - 2f * t); }

    private static float approach(float cur, float target, float k) {
        float v = cur + (target - cur) * k;
        return Math.abs(target - v) < 0.003f ? target : v;
    }

    private static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int r = Math.round(ar + (br - ar) * t), g = Math.round(ag + (bg - ag) * t), bl = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int settingHeight(Setting s) {
        if (s instanceof Setting.Slider) return SLIDER_H;
        if (s instanceof Setting.ColorPick) return COLOR_H;
        return BOOL_H;
    }

    private static int settingsHeight(Module m) {
        int h = 8;
        for (Setting s : m.settings) h += settingHeight(s);
        return h;
    }

    private static int contentHeight(Panel p) {
        int h = 0;
        for (Module m : p.mods) {
            h += ROW + Math.round(settingsHeight(m) * ease(m.openAnim));
        }
        return h;
    }

    private void text(GuiGraphics g, String s, int x, int y, int c) {
        g.drawString(this.font, s, x, y, c);
    }

    private void textRight(GuiGraphics g, String s, int xr, int y, int c) {
        g.drawString(this.font, s, xr - this.font.width(s), y, c);
    }

    // ------------------------------------------------------------------ init

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
        // stan animacji zgodny z aktualnym stanem (bez "skoku" przy otwarciu)
        for (Panel p : panels) {
            p.collapseAnim = p.collapsed ? 0f : 1f;
            for (Module m : p.mods) {
                m.onAnim = m.isEnabled() ? 1f : 0f;
                m.openAnim = m.open ? 1f : 0f;
                m.hoverAnim = 0f;
            }
        }
        search = new EditBox(this.font, this.width / 2 - 60, this.height - 28, 120, 14, Component.literal("Search"));
        search.setHint(Component.literal("Search module..."));
        addRenderableWidget(search);
        lastNanos = 0;
        intro = 0f;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        hits.clear();

        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        float k = 1f - (float) Math.exp(-dt * 16f);
        intro = approach(intro, 1f, 1f - (float) Math.exp(-dt * 9f));
        int oy = Math.round((1f - ease(intro)) * -16f);
        curOy = oy;

        String q = search.getValue().toLowerCase();
        for (Panel p : panels) drawPanel(g, p, mx, my, q, k, oy);

        text(g, "Search", this.width / 2 - 60 - 40, this.height - 25, MUT);
        text(g, "LMB: toggle   RMB: settings   MMB: bind key   Drag headers to move", 8, this.height - 14, MUT);
    }

    private void drawPanel(GuiGraphics g, Panel p, int mx, int my, String q, float k, int oy) {
        int x = p.x, y = p.y + oy, w = PW;

        p.collapseAnim = approach(p.collapseAnim, p.collapsed ? 0f : 1f, k);
        for (Module m : p.mods) {
            m.onAnim = approach(m.onAnim, m.isEnabled() ? 1f : 0f, k);
            m.openAnim = approach(m.openAnim, m.open ? 1f : 0f, k);
        }

        int visH = Math.round(contentHeight(p) * ease(p.collapseAnim));
        int totalH = HDR + visH;
        boolean interactive = p.collapseAnim > 0.99f;

        g.fill(x + 4, y + 4, x + w + 6, y + totalH + 6, BD);   // twardy cień
        g.fill(x - 2, y - 2, x + w + 2, y + totalH + 2, BD);   // ramka
        g.fill(x, y, x + w, y + HDR, HD);
        g.fill(x, y + HDR - 1, x + w, y + HDR, BD);
        text(g, p.title, x + 6, y + 4, TX);
        textRight(g, p.collapsed ? "+" : "-", x + w - 6, y + 4, MUT);
        hits.add(new Hit(Kind.HEADER, x, y, w - 18, HDR, p, null, null, 0));
        hits.add(new Hit(Kind.COLLAPSE, x + w - 18, y, 18, HDR, p, null, null, 0));

        if (visH <= 0) return;

        g.enableScissor(x, y + HDR, x + w, y + HDR + visH);
        int cy = y + HDR;
        for (Module m : p.mods) {
            boolean on = m.isEnabled();
            boolean hover = interactive && mx >= x && mx < x + w && my >= cy && my < cy + ROW
                    && my < y + HDR + visH;
            m.hoverAnim = approach(m.hoverAnim, hover ? 1f : 0f, k);

            float act = Math.max(m.onAnim, m.hoverAnim);
            g.fill(x, cy, x + w, cy + ROW, lerp(PANEL, PANEL2, act));
            int bar = Math.round(3 * m.onAnim);
            if (bar > 0) g.fill(x, cy, x + bar, cy + ROW, ON);

            int tc = lerp(lerp(MUT, TX, m.hoverAnim), ON, m.onAnim);
            boolean binding = bindingModule == m;
            text(g, binding ? "Press a key..." : m.name, x + 8, cy + 4, binding ? ON : tc);

            if (!binding && m.keyMapping != null && !m.keyMapping.isUnbound()) {
                String key = m.keyMapping.getTranslatedKeyMessage().getString();
                if (key.length() > 6) key = key.substring(0, 6);
                textRight(g, "[" + key + "]", x + w - 24, cy + 4, MUT);
            }
            if (!m.settings.isEmpty()) textRight(g, "...", x + w - 6, cy + 4, MUT);

            if (!q.isEmpty() && m.name.toLowerCase().contains(q)) {
                g.fill(x, cy, x + w, cy + 1, ON);
                g.fill(x, cy + ROW - 1, x + w, cy + ROW, ON);
                g.fill(x, cy, x + 1, cy + ROW, ON);
                g.fill(x + w - 1, cy, x + w, cy + ROW, ON);
            } else {
                g.fill(x, cy + ROW - 1, x + w, cy + ROW, BD);
            }
            if (interactive) hits.add(new Hit(Kind.MODULE, x, cy, w, ROW, p, m, null, 0));
            cy += ROW;

            float oe = ease(m.openAnim);
            int sh = Math.round(settingsHeight(m) * oe);
            if (sh > 0) {
                g.enableScissor(x, cy, x + w, cy + sh);
                g.fill(x, cy, x + w, cy + sh, SET);
                boolean clickable = interactive && m.openAnim > 0.99f;
                drawSettings(g, p, m, x, cy + 4, w, clickable);
                g.fill(x, cy + sh - 1, x + w, cy + sh, BD);
                g.disableScissor();
                cy += sh;
            }
        }
        g.disableScissor();
    }

    private void drawSettings(GuiGraphics g, Panel p, Module m, int x, int sy, int w, boolean clickable) {
        for (Setting s : m.settings) {
            if (s instanceof Setting.Bool b) {
                boolean v = b.get();
                g.fill(x + 8, sy + 3, x + 16, sy + 11, BD);
                g.fill(x + 9, sy + 4, x + 15, sy + 10, v ? b.color : CHKBG);
                text(g, b.name, x + 22, sy + 3, v ? TX : MUT);
                if (clickable) hits.add(new Hit(Kind.CHECK, x, sy, w, BOOL_H, p, m, b, 0));
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
                if (clickable) hits.add(new Hit(Kind.SLIDER, tx0, sy + 8, tw, 14, p, m, sl, 0));
            } else if (s instanceof Setting.ColorPick cp) {
                int rgb = cp.get();
                text(g, cp.name, x + 8, sy + 1, MUT);
                g.fill(x + w - 28, sy, x + w - 8, sy + 10, BD);
                g.fill(x + w - 27, sy + 1, x + w - 9, sy + 9, 0xFF000000 | rgb);

                int[] tint = {0xFFFF5555, 0xFF55FF55, 0xFF5577FF};
                int tx0 = x + 8, tw = w - 16;
                for (int ch = 0; ch < 3; ch++) {
                    int ty = sy + 14 + ch * 12;
                    int val = (rgb >> (16 - 8 * ch)) & 255;
                    g.fill(tx0, ty, tx0 + tw, ty + 6, BD);
                    g.fill(tx0 + 1, ty + 1, tx0 + tw - 1, ty + 5, CHKBG);
                    int fw = (int) Math.round((tw - 2) * (val / 255.0));
                    g.fill(tx0 + 1, ty + 1, tx0 + 1 + fw, ty + 5, tint[ch]);
                    g.fill(tx0 + fw, ty - 2, tx0 + fw + 3, ty + 8, TX);
                    if (clickable) hits.add(new Hit(Kind.COLOR, tx0, ty - 3, tw, 12, p, m, cp, ch));
                }
            }
            sy += settingHeight(s);
        }
    }

    // ------------------------------------------------------------------ input

    private void applyDrag(Hit h, double mx) {
        double frac = Math.max(0, Math.min(1, (mx - h.x()) / h.w()));
        if (h.kind() == Kind.SLIDER) {
            Setting.Slider s = (Setting.Slider) h.setting();
            s.set(s.min + frac * (s.max - s.min));
        } else if (h.kind() == Kind.COLOR) {
            Setting.ColorPick cp = (Setting.ColorPick) h.setting();
            int shift = 16 - 8 * h.channel();
            int v = (int) Math.round(frac * 255);
            cp.set((cp.get() & ~(0xFF << shift)) | (v << shift));
        }
    }

    private void bringToFront(Panel p) {
        panels.remove(p);
        panels.add(p);
    }

    private void setBind(Module m, int keyCode) {
        KeyMapping km = m.keyMapping;
        if (km == null) return;
        km.setKey(keyCode < 0 ? InputConstants.UNKNOWN : InputConstants.Type.KEYSYM.getOrCreate(keyCode));
        KeyMapping.resetMapping();
        this.minecraft.options.save();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (bindingModule != null) {
            Module m = bindingModule;
            bindingModule = null;
            int key = event.key();
            if (key == 256) return true;                              // ESC = anuluj
            if (key == 259 || key == 261) setBind(m, -1);             // Backspace/Delete = usuń bind
            else setBind(m, key);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int btn = event.button();

        Module wasBinding = bindingModule;
        bindingModule = null;

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
                    Module m = h.mod();
                    if (btn == 0) {
                        m.toggle();
                        StorageFinderConfig.save();
                    } else if (btn == 1 && !m.settings.isEmpty()) {
                        m.open = !m.open;
                    } else if (btn == 2) {
                        bindingModule = (wasBinding == m) ? null : m;
                    }
                }
                case CHECK -> {
                    if (btn == 0) {
                        Setting.Bool b = (Setting.Bool) h.setting();
                        b.set(!b.get());
                        StorageFinderConfig.save();
                    }
                }
                case SLIDER, COLOR -> {
                    if (btn == 0) {
                        dragHit = h;
                        applyDrag(h, mx);
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
            dragPanel.y = Math.max(0, Math.min(this.height - HDR, (int) event.y() - dragDY - curOy));
            return true;
        }
        if (dragHit != null) {
            applyDrag(dragHit, event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean handled = dragPanel != null || dragHit != null;
        if (dragHit != null) StorageFinderConfig.save();
        dragPanel = null;
        dragHit = null;
        if (handled) return true;
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        StorageFinderConfig.save();
        super.onClose();
    }
}
