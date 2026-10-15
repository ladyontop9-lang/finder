package com.example.storagefinder;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class Module {
    public final String name;
    public final String category;
    public final List<Setting> settings;
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;

    public Module(String name, String category, BooleanSupplier getter, Consumer<Boolean> setter, List<Setting> settings) {
        this.name = name;
        this.category = category;
        this.getter = getter;
        this.setter = setter;
        this.settings = settings;
    }

    public boolean isEnabled() { return getter.getAsBoolean(); }
    public void setEnabled(boolean v) { setter.accept(v); }
    public void toggle() { setEnabled(!isEnabled()); }
}
