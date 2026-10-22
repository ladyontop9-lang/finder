package com.example.storagefinder;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Click GUI (styl "Ventura"): ciemny panel, pasek boczny z kategoriami, karty modułów w dwóch kolumnach
 * z ustawieniami w środku, gwiazdki ulubionych, menu prawego przycisku (New bind / Reset).
 */
public class ClickGuiScreen extends Screen {
    private static final int WINBG = 0xFF15181C, SIDEBG = 0xFF13161A, BORDER = 0xFF222830, CARDBG = 0xFF171B20,
            FIELD = 0xFF1B2026, TEXT = 0xFFF4F7FA, MUT = 0xFFA3ABB3, DIM = 0xFF4A535C, TAGBG = 0xFF1C2229,
            TRACK = 0xFF272E36, SELBG = 0xFF1A2027, HOVBG = 0xFF181D23, VEIL = 0x70000000, PINK = 0xFFF08CA0;

    private static final int SB = 108, HEAD = 26, GAPC = 8;
    private static final int HEAD_H = 20, TAG_H = 18, PAD_TOP = 4, PAD_BOT = 4;
    private static final int R_BOOL = 16, R_MODE = 30, R_SLIDER = 22, R_COLOR = 44, R_SWATCH = 26, R_BIND = 16, R_TEXT = 30;

    // ---- ikony 9x9 / 7x7 (piksele) ----
    private static final String[] I_COMBAT = {".......##", "......###", ".....###.", "....###..", "#..###...", ".####....", "..##.....", ".#.##....", "#...#...."};
    private static final String[] I_MOVE = {"......###", ".....####", "....####.", "...####..", "..####...", ".####....", "####.....", "#.##.....", "#........"};
    private static final String[] I_MISC = {".#.#.#.#.", "#########", ".#.....#.", "##.###.##", ".#.###.#.", "##.###.##", ".#.....#.", "#########", ".#.#.#.#."};
    private static final String[] I_RENDER = {"#########", "#.......#", "#.##....#", "#.##....#", "#.......#", "#...#...#", "#..###..#", "#.#####.#", "#########"};
    private static final String[] I_STAR = {"....#....", "....#....", "...###...", "#########", ".#######.", "..#####..", "..##.##..", ".##...##.", "#.......#"};
    private static final String[] I_SEARCH = {".###...", "#...#..", "#...#..", "#...#..", ".###...", "....##.", ".....##"};
    private static final String[] I_TOGGLE = {".#####.", "#######", "####..#", "#######", ".#####."};
    private static final String[] I_SLIDER = {"#######", "..#....", ".......", "#######", "....#.."};
    private static final String[] I_LIST = {"#######", "#.....#", "#.###.#", "#.....#", "#.###.#", "#.....#", "#######"};
    private static final String[] I_KEY = {".###...", "#...#..", "#...#..", ".###...", "..#....", "..##...", "..#...."};
    private static final String[] I_COLOR = {".#####.", "#.....#", "#.###.#", "#.###.#", "#.###.#", "#.....#", ".#####."};
    private static final String[] I_REFRESH = {".###.#.", "#...##.", "#....#.", "#......", "#.....#", ".#...#.", "..###.."};
    private static final String[] I_SCRIPT = {".#####.", "#.....#", "#.#.#.#", "#..#..#", "#.#.#.#", "#.....#", ".#####."};
    private static final String[] I_K = {"###....###", "###...###.", "###..###..", "###.###...", "######....", "######....", "###.###...", "###..###..", "###...###.", "###....###"};
    private static final String[] I_PLUS = {"..#..", "..#..", "#####", "..#..", "..#.."};
    private static final String[] I_RESET = {".###.", "#...#", "#....", "#...#", ".###."};

    private record Nav(String id, String label, String[] icon, int section) {}

    private static final Nav[] NAVS = {
            new Nav("Combat", "Combat", I_COMBAT, 0),
            new Nav("Movement", "Movement", I_MOVE, 0),
            new Nav("Misc", "Misc", I_MISC, 0),
            new Nav("Render", "Render", I_RENDER, 0),
            new Nav("Favorites", "Ulubione", I_STAR, 1)
    };

    private enum Kind { NAV, HEAD, TOGGLE, STAR, BOOL, SLIDER, CHANNEL, DROPDOWN, DD_OPT, SWATCH, BIND, CTX_BIND, CTX_RESET, TEXT, REFRESH }

    private record Hit(Kind kind, int x, int y, int w, int h, boolean body, Module mod, Setting setting, int idx) {
        boolean inside(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private List<Nav> navList = new ArrayList<>(List.of(NAVS));
    private EditBox search;
    private EditBox textBox;
    private Setting.Text editing = null;
    private int editX, editY, editW;

    private int cat = 0;
    private Module binding = null;
    private Hit dragHit = null;

    private Module ctxModule = null;
    private int ctxX, ctxY;
    private Setting.Mode ddSetting = null;
    private int ddX, ddY, ddW;

    private float scroll = 0, scrollTarget = 0, maxScroll = 0;
    private float openT = 0, pageT = 1;
    private long lastNanos = 0;
    private float A = 1f;

    private int wx, wy, ww, wh, bx, by, bw, bh;

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

    private int accent() { return 0xFF000000 | StorageFinderConfig.accent; }

    private int c(int argb) {
        int al = Math.round(((argb >>> 24) & 255) * A);
        return (al << 24) | (argb & 0xFFFFFF);
    }

    private void fill(GuiGraphics g, int x1, int y1, int x2, int y2, int col) { g.fill(x1, y1, x2, y2, c(col)); }

    private void rr(GuiGraphics g, int x, int y, int w, int h, int r, int col) { Ui.rrect(g, x, y, w, h, r, c(col)); }

    /** Ramka 1px + wypełnienie (zaokrąglone). */
    private void box(GuiGraphics g, int x, int y, int w, int h, int r, int fillCol, int borderCol) {
        rr(g, x - 1, y - 1, w + 2, h + 2, r + 1, borderCol);
        rr(g, x, y, w, h, r, fillCol);
    }

    private void txt(GuiGraphics g, String s, int x, int y, int col) { g.drawString(this.font, s, x, y, c(col)); }

    private void txtR(GuiGraphics g, String s, int xr, int y, int col) {
        g.drawString(this.font, s, xr - this.font.width(s), y, c(col));
    }

    private void icon(GuiGraphics g, String[] rows, int x, int y, int col) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int cc = 0; cc < row.length(); cc++) {
                if (row.charAt(cc) == '#') fill(g, x + cc, y + r, x + cc + 1, y + r + 1, col);
            }
        }
    }

    private static String[] iconFor(String category) {
        return switch (category) {
            case "Combat" -> I_COMBAT;
            case "Movement" -> I_MOVE;
            case "Misc" -> I_MISC;
            default -> I_RENDER;
        };
    }

    private void startPage() {
        commitText();
        pageT = 0;
        scroll = 0;
        scrollTarget = 0;
        ctxModule = null;
        ddSetting = null;
    }

    private void layout(int oy) {
        ww = Math.min(480, this.width - 20);
        wh = Math.min(290, this.height - 20);
        wx = (this.width - ww) / 2;
        wy = (this.height - wh) / 2 + oy;
        bx = wx + SB + 8;
        by = wy + HEAD + 8;
        bw = ww - SB - 16 - 4;
        bh = wh - HEAD - 16;
    }

    private String keyText(Module m) {
        if (m.keyMapping == null || m.keyMapping.isUnbound()) return "Brak";
        return m.keyMapping.getTranslatedKeyMessage().getString();
    }

    // ------------------------------------------------------------------ init

    @Override
    protected void init() {
        navList = new ArrayList<>(List.of(NAVS));
        navList.add(new Nav("License", "Licencja", I_KEY, 1));
        if (Krux.DEV_BUILD) navList.add(new Nav("Developer", "Developer", I_MISC, 1));
        layout(0);
        int btnX = wx + ww - 8 - 16;
        int sbx = btnX - 6 - 124;
        search = new EditBox(this.font, sbx + 18, wy + 5, 124 - 24, 16, Component.literal("Search"));
        search.setBordered(false);
        search.setTextColor(TEXT);
        search.setHint(Component.literal("Szukaj modułów..."));
        search.setResponder(s -> startPage());
        addWidget(search);

        textBox = new EditBox(this.font, 0, 0, 100, 14, Component.literal("Text"));
        textBox.setBordered(false);
        textBox.setTextColor(TEXT);
        textBox.setVisible(false);
        addWidget(textBox);

        for (Module m : Modules.ALL) m.onAnim = m.isEnabled() ? 1f : 0f;
        lastNanos = 0;
        openT = 0;
        pageT = 1;
    }

    // ------------------------------------------------------------------ module list + card sizes

    private List<Module> visibleModules() {
        String term = search.getValue().trim().toLowerCase();
        List<Module> out = new ArrayList<>();
        for (Module m : Modules.ALL) {
            if (!term.isEmpty()) {
                boolean tag = false;
                for (String t : m.tags) if (t.toLowerCase().contains(term)) tag = true;
                if (tag || m.name.toLowerCase().contains(term)) out.add(m);
            } else if (navList.get(cat).id().equals("Favorites")) {
                if (StorageFinderConfig.favorites.contains(m.name)) out.add(m);
            } else if (m.category.equals(navList.get(cat).id())) {
                out.add(m);
            }
        }
        return out;
    }

    private static int settingHeight(Setting s) {
        if (s instanceof Setting.Slider) return R_SLIDER;
        if (s instanceof Setting.Mode) return R_MODE;
        if (s instanceof Setting.ColorPick) return R_COLOR;
        if (s instanceof Setting.Swatches) return R_SWATCH;
        if (s instanceof Setting.Text) return R_TEXT;
        return R_BOOL;
    }

    private static int cardHeight(Module m) {
        int h = HEAD_H + (m.tags.length > 0 ? TAG_H : 0) + 1 + PAD_TOP;
        for (Setting s : m.settings) h += settingHeight(s);
        h += R_BIND + PAD_BOT;
        return h;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        float k = 1f - (float) Math.exp(-dt * 16f);

        openT = approach(openT, 1f, 1f - (float) Math.exp(-dt * 12f));
        pageT = approach(pageT, 1f, 1f - (float) Math.exp(-dt * 14f));
        scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-dt * 18f));
        if (Math.abs(scrollTarget - scroll) < 0.05f) scroll = scrollTarget;

        float ge = ease(openT);
        int oy = Math.round((1f - ge) * 6f);
        layout(oy);
        hits.clear();

        for (Module m : Modules.ALL) m.onAnim = approach(m.onAnim, m.isEnabled() ? 1f : 0f, k);

        A = ge;
        g.fill(0, 0, this.width, this.height, c(VEIL));

        drawChrome(g, mx, my);
        drawSearch(g, mx, my, partialTick);

        // ---- treść ----
        float pe = ease(pageT);
        A = ge * pe;
        int yOff = Math.round((1f - pe) * 4f);
        g.enableScissor(bx - 2, by, bx + bw + 6, by + bh);
        int contentH = drawBody(g, mx, my, yOff);
        g.disableScissor();
        maxScroll = Math.max(0, contentH - bh);
        if (scrollTarget > maxScroll) scrollTarget = maxScroll;

        A = ge;
        if (maxScroll > 0) {
            int thumbH = Math.max(14, Math.round(bh * (bh / (float) contentH)));
            int thumbY = by + Math.round((bh - thumbH) * (scroll / maxScroll));
            rr(g, wx + ww - 6, thumbY, 2, thumbH, 1, TRACK);
        }

        drawPopups(g, mx, my);
    }

    private void drawChrome(GuiGraphics g, int mx, int my) {
        box(g, wx, wy, ww, wh, 6, WINBG, BORDER);

        // pasek boczny
        rr(g, wx, wy, SB, wh, 6, SIDEBG);
        fill(g, wx + 6, wy, wx + SB, wy + wh, SIDEBG);
        fill(g, wx + SB, wy, wx + SB + 1, wy + wh, BORDER);
        fill(g, wx, wy + HEAD, wx + SB, wy + HEAD + 1, BORDER);
        fill(g, wx + SB + 1, wy + HEAD, wx + ww, wy + HEAD + 1, BORDER);

        // logo + nazwa
        rr(g, wx + 8, wy + 4, 18, 18, 5, accent());
        icon(g, I_K, wx + 8 + 4, wy + 4 + 4, 0xFFFFFFFF);
        Component krux = Component.literal("Krux").withStyle(ChatFormatting.BOLD);
        g.drawString(this.font, krux, wx + 32, wy + 9, c(TEXT));
        txt(g, "Client", wx + 32 + this.font.width(krux) + 4, wy + 9, MUT);

        // pozycje paska bocznego
        int y = wy + HEAD + 8;
        int lastSection = -1;
        for (int i = 0; i < navList.size(); i++) {
            Nav n = navList.get(i);
            if (n.section() != lastSection) {
                lastSection = n.section();
                if (i != 0) y += 6;
                txt(g, n.section() == 0 ? "Główne" : "Inne", wx + 12, y, DIM);
                y += 12;
            }
            boolean sel = i == cat && search.getValue().trim().isEmpty();
            boolean hover = mx >= wx + 6 && mx < wx + SB - 6 && my >= y && my < y + 18;
            if (sel) {
                box(g, wx + 6, y, SB - 12, 18, 4, SELBG, BORDER);
                fill(g, wx + 7, y + 3, wx + 9, y + 15, accent());
            }
            else if (hover) rr(g, wx + 6, y, SB - 12, 18, 4, HOVBG);
            icon(g, n.icon(), wx + 13, y + 4, sel ? accent() : MUT);
            txt(g, n.label(), wx + 27, y + 5, sel ? TEXT : MUT);
            if (sel) {
                for (int dx = 0; dx < 2; dx++) for (int dy = 0; dy < 3; dy++) {
                    fill(g, wx + SB - 6 - 11 + dx * 4, y + 4 + dy * 4, wx + SB - 6 - 11 + dx * 4 + 2, y + 4 + dy * 4 + 2, accent());
                }
            }
            hits.add(new Hit(Kind.NAV, wx + 6, y, SB - 12, 18, false, null, null, i));
            y += 20;
        }

        // stopka z nazwą gracza
        int fy = wy + wh - 28;
        fill(g, wx, fy, wx + SB, fy + 1, BORDER);
        rr(g, wx + 8, fy + 6, 16, 16, 4, 0xFF0B0B0D);
        txt(g, "K", wx + 8 + 8 - this.font.width("K") / 2, fy + 10, accent());
        String name = NameProtect.apply(Minecraft.getInstance().getUser().getName());
        if (this.font.width(name) > SB - 52) name = this.font.plainSubstrByWidth(name, SB - 52);
        txt(g, name, wx + 30, fy + 6, TEXT);
        txt(g, Krux.DEV_BUILD ? "Developer" : "Customer", wx + 30, fy + 16, MUT);

        // nagłówek: ścieżka
        int cx = wx + SB + 12;
        String term = search.getValue().trim();
        if (term.isEmpty()) {
            icon(g, navList.get(cat).icon(), cx, wy + 9, accent());
            txt(g, navList.get(cat).label(), cx + 14, wy + 9, TEXT);
        } else {
            icon(g, I_SEARCH, cx, wy + 10, accent());
            txt(g, "Wyniki wyszukiwania", cx + 14, wy + 9, TEXT);
        }
    }

    private void drawSearch(GuiGraphics g, int mx, int my, float pt) {
        int btnX = wx + ww - 8 - 16;
        int sbx = btnX - 6 - 124, sby = wy + 5;
        boolean focus = search.isFocused();
        box(g, sbx, sby, 124, 16, 4, FIELD, focus ? accent() : BORDER);
        icon(g, I_SEARCH, sbx + 6, sby + 4, MUT);

        boolean rh = mx >= btnX && mx < btnX + 16 && my >= sby && my < sby + 16;
        box(g, btnX, sby, 16, 16, 5, rh ? 0xFF2A2A31 : FIELD, BORDER);
        icon(g, I_REFRESH, btnX + 4, sby + 4, TEXT);
        hits.add(new Hit(Kind.REFRESH, btnX, sby, 16, 16, false, null, null, 0));

        search.setX(sbx + 18);
        search.setY(sby + (16 - 8) / 2 - 3);
        search.render(g, mx, my, pt);
    }

    // ------------------------------------------------------------------ body (cards, masonry)

    private void toggleSwitch(GuiGraphics g, int x, int y, float t) {
        rr(g, x, y, 22, 12, 6, lerp(TRACK, accent(), t));
        rr(g, x + 2 + Math.round(10 * t), y + 2, 8, 8, 4, 0xFFFFFFFF);
    }

    private int drawBody(GuiGraphics g, int mx, int my, int yOff) {
        List<Module> mods = visibleModules();
        int top = by - Math.round(scroll) + yOff;

        if (mods.isEmpty()) {
            String id = navList.get(cat).id();
            boolean noSearch = search.getValue().trim().isEmpty();
            String msg = id.equals("Favorites") && noSearch ? "Nie masz jeszcze ulubionych. Kliknij gwiazdkę przy module."
                    : "Nie znaleziono modułów.";
            txt(g, msg, bx + 4, top + 6, MUT);
            return 24;
        }

        int colW = (bw - GAPC) / 2;
        int[] colH = {0, 0};
        boolean inView = mx >= bx && mx < bx + bw && my >= by && my < by + bh;

        for (Module m : mods) {
            int col = colH[0] <= colH[1] ? 0 : 1;
            int cx = bx + col * (colW + GAPC);
            int cy = top + colH[col];
            int ch = cardHeight(m);
            if (cy + ch >= by - 4 && cy <= by + bh + 4) drawCard(g, m, cx, cy, colW, ch, mx, my, inView);
            colH[col] += ch + GAPC;
        }
        return Math.max(colH[0], colH[1]) - GAPC;
    }

    private void drawCard(GuiGraphics g, Module m, int x, int y, int cw, int ch, int mx, int my, boolean inView) {
        int tint = lerp(BORDER, accent(), 0.45f * m.onAnim);
        box(g, x, y, cw, ch, 5, CARDBG, tint);
        if (m.onAnim > 0.02f) fill(g, x, y + 2, x + 2, y + HEAD_H - 2, lerp(BORDER, accent(), m.onAnim));

        // nagłówek karty
        icon(g, iconFor(m.category), x + 7, y + 6, accent());
        txt(g, m.name, x + 19 + 4 + 0, y + 6, TEXT);
        int swX = x + cw - 7 - 22;
        int starX = swX - 14;
        boolean fav = StorageFinderConfig.favorites.contains(m.name);
        icon(g, I_STAR, starX, y + 6, fav ? accent() : DIM);
        toggleSwitch(g, swX, y + 4, m.onAnim);
        hits.add(new Hit(Kind.HEAD, x, y, cw - 40, HEAD_H, true, m, null, 0));
        hits.add(new Hit(Kind.STAR, starX - 2, y + 3, 13, 14, true, m, null, 0));
        hits.add(new Hit(Kind.TOGGLE, swX, y + 4, 22, 12, true, m, null, 0));

        int yy = y + HEAD_H;
        if (m.tags.length > 0) {
            int tx = x + 7;
            for (String tag : m.tags) {
                int w = this.font.width(tag) + 10;
                rr(g, tx, yy + 1, w, 13, 4, TAGBG);
                txt(g, tag, tx + 5, yy + 3, MUT);
                tx += w + 4;
            }
            yy += TAG_H;
        }
        fill(g, x, yy, x + cw, yy + 1, BORDER);
        yy += 1 + PAD_TOP;

        for (Setting s : m.settings) {
            if (s instanceof Setting.Bool b) drawBool(g, m, b, x, yy, cw);
            else if (s instanceof Setting.Slider sl) drawSlider(g, m, sl, x, yy, cw);
            else if (s instanceof Setting.Mode md) drawMode(g, m, md, x, yy, cw);
            else if (s instanceof Setting.Swatches sw) drawSwatches(g, m, sw, x, yy, cw);
            else if (s instanceof Setting.ColorPick cp) drawColor(g, m, cp, x, yy, cw);
            else if (s instanceof Setting.Text tx) drawText(g, m, tx, x, yy, cw);
            yy += settingHeight(s);
        }

        // Activation Key
        icon(g, I_KEY, x + 8, yy + 4, accent());
        txt(g, "Activation Key", x + 19 + 4, yy + 4, TEXT);
        boolean wait = binding == m;
        String kt = wait ? "Naciśnij klawisz..." : keyText(m);
        txtR(g, kt, x + cw - 7, yy + 4, wait ? accent() : MUT);
        hits.add(new Hit(Kind.BIND, x, yy, cw, R_BIND, true, m, null, 0));
    }

    private void commitText() {
        if (editing != null) {
            editing.set(textBox.getValue().trim());
            editing = null;
            StorageFinderConfig.save();
        }
        if (textBox != null) {
            textBox.setVisible(false);
            textBox.setFocused(false);
        }
    }

    private void drawText(GuiGraphics g, Module m, Setting.Text tx, int x, int y, int cw) {
        icon(g, I_LIST, x + 8, y + 2, accent());
        txt(g, tx.name, x + 19 + 4, y + 2, TEXT);
        int fx = x + 7, fw = cw - 14, fy = y + 13;
        boolean active = editing == tx;
        box(g, fx, fy, fw, 15, 4, FIELD, active ? accent() : BORDER);
        if (active) {
            textBox.setX(fx + 6);
            textBox.setY(fy + 3);
            textBox.setWidth(fw - 12);
            textBox.render(this.dummyG(g), 0, 0, 0f);
            editX = fx;
            editY = fy;
            editW = fw;
        } else {
            txt(g, tx.get(), fx + 6, fy + 4, 0xFFB4B4BE);
        }
        hits.add(new Hit(Kind.TEXT, fx, fy, fw, 15, true, m, tx, 0));
    }

    private GuiGraphics dummyG(GuiGraphics g) { return g; }

    private void drawBool(GuiGraphics g, Module m, Setting.Bool b, int x, int y, int cw) {
        icon(g, I_TOGGLE, x + 8, y + 5, accent());
        int lx = x + 19 + 4;
        if (b.color != 0) {
            rr(g, lx, y + 5, 6, 6, 2, b.color);
            lx += 10;
        }
        txt(g, b.name, lx, y + 4, TEXT);
        toggleSwitch(g, x + cw - 7 - 22, y + 2, b.get() ? 1f : 0f);
        hits.add(new Hit(Kind.BOOL, x, y, cw, R_BOOL, true, m, b, 0));
    }

    private void track(GuiGraphics g, int x, int y, int w, double frac) {
        rr(g, x, y, w, 3, 1, TRACK);
        int fw = (int) Math.round(w * Math.max(0, Math.min(1, frac)));
        if (fw > 0) rr(g, x, y, Math.max(2, fw), 3, 1, accent());
        rr(g, x + fw - 3, y - 2, 7, 7, 3, 0xFFFFFFFF);
    }

    private void drawSlider(GuiGraphics g, Module m, Setting.Slider sl, int x, int y, int cw) {
        icon(g, I_SLIDER, x + 8, y + 2, accent());
        txt(g, sl.name, x + 19 + 4, y + 2, TEXT);
        txtR(g, sl.display(), x + cw - 7, y + 2, TEXT);
        int tx = x + 8, tw = cw - 16;
        track(g, tx, y + 15, tw, (sl.get() - sl.min) / (sl.max - sl.min));
        hits.add(new Hit(Kind.SLIDER, tx, y + 11, tw, 11, true, m, sl, 0));
    }

    private void drawMode(GuiGraphics g, Module m, Setting.Mode md, int x, int y, int cw) {
        icon(g, I_LIST, x + 8, y + 2, accent());
        txt(g, md.name, x + 19 + 4, y + 2, TEXT);
        int fx = x + 7, fw = cw - 14;
        box(g, fx, y + 13, fw, 15, 4, FIELD, BORDER);
        txt(g, md.options[md.get()], fx + 6, y + 17, 0xFFB4B4BE);
        // strzałka
        int ax = fx + fw - 12, ay = y + 19;
        fill(g, ax, ay, ax + 5, ay + 1, MUT);
        fill(g, ax + 1, ay + 1, ax + 4, ay + 2, MUT);
        fill(g, ax + 2, ay + 2, ax + 3, ay + 3, MUT);
        hits.add(new Hit(Kind.DROPDOWN, fx, y + 13, fw, 15, true, m, md, 0));
    }

    private void drawSwatches(GuiGraphics g, Module m, Setting.Swatches sw, int x, int y, int cw) {
        icon(g, I_COLOR, x + 8, y + 2, accent());
        txt(g, sw.name, x + 19 + 4, y + 2, TEXT);
        int sel = sw.selected();
        int cx = x + 8;
        for (int i = 0; i < sw.colors.length; i++) {
            if (i == sel) rr(g, cx - 2, y + 11, 14, 14, 4, 0xFFFFFFFF);
            rr(g, cx, y + 13, 10, 10, 3, 0xFF000000 | sw.colors[i]);
            hits.add(new Hit(Kind.SWATCH, cx - 2, y + 11, 14, 14, true, m, sw, i));
            cx += 16;
        }
    }

    private void drawColor(GuiGraphics g, Module m, Setting.ColorPick cp, int x, int y, int cw) {
        int rgb = cp.get();
        icon(g, I_COLOR, x + 8, y + 2, accent());
        txt(g, cp.name, x + 19 + 4, y + 2, TEXT);
        rr(g, x + cw - 7 - 18, y + 1, 18, 9, 3, 0xFF000000 | rgb);
        int[] tint = {0xFFFF5555, 0xFF55FF55, 0xFF5577FF};
        int tx = x + 8, tw = cw - 16;
        for (int ch = 0; ch < 3; ch++) {
            int ty = y + 16 + ch * 10;
            int val = (rgb >> (16 - 8 * ch)) & 255;
            rr(g, tx, ty, tw, 3, 1, TRACK);
            int fw = (int) Math.round(tw * (val / 255.0));
            if (fw > 0) rr(g, tx, ty, Math.max(2, fw), 3, 1, tint[ch]);
            rr(g, tx + fw - 3, ty - 2, 7, 7, 3, 0xFFFFFFFF);
            hits.add(new Hit(Kind.CHANNEL, tx, ty - 4, tw, 10, true, m, cp, ch));
        }
    }

    // ------------------------------------------------------------------ popups

    private void drawPopups(GuiGraphics g, int mx, int my) {
        A = ease(openT);

        if (ddSetting != null) {
            int n = ddSetting.options.length;
            int h = n * 14 + 4;
            box(g, ddX, ddY, ddW, h, 4, FIELD, BORDER);
            int sel = ddSetting.get();
            for (int i = 0; i < n; i++) {
                int oy = ddY + 2 + i * 14;
                boolean hv = mx >= ddX && mx < ddX + ddW && my >= oy && my < oy + 14;
                if (hv) rr(g, ddX + 2, oy, ddW - 4, 14, 3, SELBG);
                txt(g, ddSetting.options[i], ddX + 8, oy + 3, i == sel ? accent() : (hv ? TEXT : MUT));
                hits.add(new Hit(Kind.DD_OPT, ddX, oy, ddW, 14, false, null, ddSetting, i));
            }
        }

        if (ctxModule != null) {
            int w = 80, h = 36;
            int x = Math.min(ctxX, this.width - w - 4), y = Math.min(ctxY, this.height - h - 4);
            box(g, x, y, w, h, 5, CARDBG, BORDER);
            boolean h1 = mx >= x && mx < x + w && my >= y + 2 && my < y + 18;
            boolean h2 = mx >= x && mx < x + w && my >= y + 18 && my < y + 34;
            if (h1) rr(g, x + 2, y + 2, w - 4, 16, 3, SELBG);
            if (h2) rr(g, x + 2, y + 18, w - 4, 16, 3, SELBG);
            icon(g, I_PLUS, x + 8, y + 7, MUT);
            txt(g, "Nowy bind", x + 20, y + 6, TEXT);
            icon(g, I_RESET, x + 8, y + 23, PINK);
            txt(g, "Reset", x + 20, y + 22, PINK);
            hits.add(new Hit(Kind.CTX_BIND, x, y + 2, w, 16, false, ctxModule, null, 0));
            hits.add(new Hit(Kind.CTX_RESET, x, y + 18, w, 16, false, ctxModule, null, 0));
        }
    }

    // ------------------------------------------------------------------ input

    private void applyDrag(Hit h, double mx) {
        double frac = Math.max(0, Math.min(1, (mx - h.x()) / h.w()));
        if (h.kind() == Kind.SLIDER) {
            Setting.Slider s = (Setting.Slider) h.setting();
            s.set(s.min + frac * (s.max - s.min));
        } else if (h.kind() == Kind.CHANNEL) {
            Setting.ColorPick cp = (Setting.ColorPick) h.setting();
            int shift = 16 - 8 * h.idx();
            int v = (int) Math.round(frac * 255);
            cp.set((cp.get() & ~(0xFF << shift)) | (v << shift));
        }
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
        if (editing != null) {
            int k = event.key();
            if (k == 257 || k == 335) {                 // Enter = zatwierdź
                commitText();
                return true;
            }
            if (k == 256) {                             // ESC = anuluj edycję
                editing = null;
                textBox.setVisible(false);
                textBox.setFocused(false);
                return true;
            }
            return super.keyPressed(event);
        }
        if (binding != null) {
            Module m = binding;
            binding = null;
            int key = event.key();
            if (key == 256) return true;                              // ESC = anuluj
            if (key == 259 || key == 261) setBind(m, -1);             // Backspace / Delete = brak bindu
            else setBind(m, key);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= wx && mouseX < wx + ww && mouseY >= wy && mouseY < wy + wh) {
            ctxModule = null;
            ddSetting = null;
            scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - (float) scrollY * 18f));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean popupHitAt(double mx, double my) {
        for (Hit h : hits) {
            if ((h.kind() == Kind.DD_OPT || h.kind() == Kind.CTX_BIND || h.kind() == Kind.CTX_RESET) && h.inside(mx, my)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int btn = event.button();

        if (editing != null) {
            boolean inField = mx >= editX && mx < editX + editW && my >= editY && my < editY + 15;
            if (inField) return super.mouseClicked(event, doubleClick);
            commitText();
        }

        // klik poza otwartym popupem tylko go zamyka
        if ((ctxModule != null || ddSetting != null) && !popupHitAt(mx, my)) {
            ctxModule = null;
            ddSetting = null;
            return true;
        }

        Module wasBinding = binding;
        binding = null;

        boolean inView = mx >= bx && mx < bx + bw + 6 && my >= by && my < by + bh;

        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.inside(mx, my)) continue;
            if (h.body() && !inView) continue;

            switch (h.kind()) {
                case NAV -> {
                    if (btn == 0) {
                        Nav n = navList.get(h.idx());
                        if (n.id().equals("License")) {
                            this.minecraft.setScreen(new LicenseScreen(this));
                        } else if (n.id().equals("Developer")) {
                            this.minecraft.setScreen(new KruxDevScreen(this));
                        } else {
                            cat = h.idx();
                            search.setValue("");
                            startPage();
                        }
                    }
                }
                case HEAD -> {
                    if (btn == 0) {
                        h.mod().toggle();
                        StorageFinderConfig.save();
                    } else if (btn == 1) {
                        ctxModule = h.mod();
                        ctxX = (int) mx;
                        ctxY = (int) my;
                    }
                }
                case TOGGLE -> {
                    if (btn == 0) {
                        h.mod().toggle();
                        StorageFinderConfig.save();
                    }
                }
                case STAR -> {
                    if (btn == 0) {
                        String n = h.mod().name;
                        if (!StorageFinderConfig.favorites.remove(n)) StorageFinderConfig.favorites.add(n);
                        StorageFinderConfig.save();
                    }
                }
                case BOOL -> {
                    if (btn == 0) {
                        Setting.Bool b = (Setting.Bool) h.setting();
                        b.set(!b.get());
                        StorageFinderConfig.save();
                    }
                }
                case SLIDER, CHANNEL -> {
                    if (btn == 0) {
                        dragHit = h;
                        applyDrag(h, mx);
                    }
                }
                case DROPDOWN -> {
                    if (btn == 0) {
                        ddSetting = (Setting.Mode) h.setting();
                        ddX = h.x();
                        ddY = h.y() + h.h() + 1;
                        ddW = h.w();
                    }
                }
                case DD_OPT -> {
                    if (btn == 0) {
                        ((Setting.Mode) h.setting()).set(h.idx());
                        ddSetting = null;
                        StorageFinderConfig.save();
                    }
                }
                case REFRESH -> {
                    if (btn == 0) {
                        StorageFinderConfig.load();
                        search.setValue("");
                        startPage();
                    }
                }
                case TEXT -> {
                    if (btn == 0) {
                        editing = (Setting.Text) h.setting();
                        textBox.setMaxLength(editing.maxLen);
                        textBox.setValue(editing.get());
                        textBox.setVisible(true);
                        setFocused(textBox);
                        textBox.setFocused(true);
                    }
                }
                case SWATCH -> {
                    if (btn == 0) {
                        ((Setting.Swatches) h.setting()).set(h.idx());
                        StorageFinderConfig.save();
                    }
                }
                case BIND -> {
                    if (btn == 0) binding = (wasBinding == h.mod()) ? null : h.mod();
                }
                case CTX_BIND -> {
                    binding = h.mod();
                    ctxModule = null;
                }
                case CTX_RESET -> {
                    h.mod().reset();
                    ctxModule = null;
                    StorageFinderConfig.save();
                }
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragHit != null) {
            applyDrag(dragHit, event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragHit != null) {
            dragHit = null;
            StorageFinderConfig.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        commitText();
        StorageFinderConfig.save();
        super.onClose();
    }
}
