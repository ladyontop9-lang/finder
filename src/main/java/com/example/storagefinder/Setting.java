package com.example.storagefinder;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public abstract class Setting {
    public final String name;

    protected Setting(String name) {
        this.name = name;
    }

    public static final class Bool extends Setting {
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;
        public final int color;

        public Bool(String name, int color, BooleanSupplier getter, Consumer<Boolean> setter) {
            super(name);
            this.color = color;
            this.getter = getter;
            this.setter = setter;
        }

        public boolean get() { return getter.getAsBoolean(); }
        public void set(boolean v) { setter.accept(v); }
    }

    public static final class Slider extends Setting {
        public final double min, max, step;
        public final boolean integer;
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;

        public Slider(String name, double min, double max, double step, boolean integer,
                      DoubleSupplier getter, DoubleConsumer setter) {
            super(name);
            this.min = min;
            this.max = max;
            this.step = step;
            this.integer = integer;
            this.getter = getter;
            this.setter = setter;
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
    }
}
