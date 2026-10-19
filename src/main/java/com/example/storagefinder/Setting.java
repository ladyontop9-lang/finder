package com.example.storagefinder;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public abstract class Setting {
    public final String name;

    protected Setting(String name) {
        this.name = name;
    }

    /** Przywraca wartość domyślną (zapamiętaną przy tworzeniu ustawienia). */
    public abstract void reset();

    public static final class Bool extends Setting {
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;
        private final boolean def;
        public final int color;

        public Bool(String name, int color, BooleanSupplier getter, Consumer<Boolean> setter) {
            super(name);
            this.color = color;
            this.getter = getter;
            this.setter = setter;
            this.def = getter.getAsBoolean();
        }

        public boolean get() { return getter.getAsBoolean(); }
        public void set(boolean v) { setter.accept(v); }
        @Override public void reset() { setter.accept(def); }
    }

    public static final class Slider extends Setting {
        public final double min, max, step;
        public final boolean integer;
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;
        private final double def;

        public Slider(String name, double min, double max, double step, boolean integer,
                      DoubleSupplier getter, DoubleConsumer setter) {
            super(name);
            this.min = min;
            this.max = max;
            this.step = step;
            this.integer = integer;
            this.getter = getter;
            this.setter = setter;
            this.def = getter.getAsDouble();
        }

        public double get() { return getter.getAsDouble(); }

        public void set(double v) {
            v = Math.round(v / step) * step;
            v = Math.max(min, Math.min(max, v));
            v = Math.round(v * 100.0) / 100.0;
            setter.accept(v);
        }

        public String display() {
            return integer ? String.valueOf((int) Math.round(get()))
                    : String.format(java.util.Locale.ROOT, "%.1f", get());
        }

        @Override public void reset() { setter.accept(def); }
    }

    /** Wybór koloru RGB (trzy suwaki + podgląd). */
    public static final class ColorPick extends Setting {
        private final IntSupplier getter;
        private final IntConsumer setter;
        private final int def;

        public ColorPick(String name, IntSupplier getter, IntConsumer setter) {
            super(name);
            this.getter = getter;
            this.setter = setter;
            this.def = getter.getAsInt() & 0xFFFFFF;
        }

        public int get() { return getter.getAsInt() & 0xFFFFFF; }
        public void set(int rgb) { setter.accept(rgb & 0xFFFFFF); }
        @Override public void reset() { setter.accept(def); }
    }

    /** Wybór jednej opcji z listy (rozwijana lista). */
    public static final class Mode extends Setting {
        public final String[] options;
        private final IntSupplier getter;
        private final IntConsumer setter;
        private final int def;

        public Mode(String name, String[] options, IntSupplier getter, IntConsumer setter) {
            super(name);
            this.options = options;
            this.getter = getter;
            this.setter = setter;
            this.def = getter.getAsInt();
        }

        public int get() { return Math.max(0, Math.min(options.length - 1, getter.getAsInt())); }
        public void set(int i) { setter.accept(i); }
        @Override public void reset() { setter.accept(def); }
    }

    /** Wybór koloru z gotowych próbek. */
    public static final class Swatches extends Setting {
        public final String[] names;
        public final int[] colors;
        private final IntSupplier getter;
        private final IntConsumer setter;
        private final int def;

        public Swatches(String name, String[] names, int[] colors, IntSupplier getter, IntConsumer setter) {
            super(name);
            this.names = names;
            this.colors = colors;
            this.getter = getter;
            this.setter = setter;
            this.def = getter.getAsInt() & 0xFFFFFF;
        }

        public int selected() {
            int cur = getter.getAsInt() & 0xFFFFFF;
            for (int i = 0; i < colors.length; i++) if ((colors[i] & 0xFFFFFF) == cur) return i;
            return -1;
        }

        public void set(int i) { setter.accept(colors[i] & 0xFFFFFF); }
        @Override public void reset() { setter.accept(def); }
    }

    /** Pole tekstowe (np. fałszywy nick). */
    public static final class Text extends Setting {
        public final int maxLen;
        private final Supplier<String> getter;
        private final Consumer<String> setter;
        private final String def;

        public Text(String name, int maxLen, Supplier<String> getter, Consumer<String> setter) {
            super(name);
            this.maxLen = maxLen;
            this.getter = getter;
            this.setter = setter;
            this.def = getter.get();
        }

        public String get() { return getter.get(); }
        public void set(String v) { setter.accept(v); }
        @Override public void reset() { setter.accept(def); }
    }
}
