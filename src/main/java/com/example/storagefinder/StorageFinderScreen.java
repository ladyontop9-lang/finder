package com.example.storagefinder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class StorageFinderScreen extends Screen {
    private ButtonWidget mainBtn, chestBtn, enderBtn, shulkerBtn;

    public StorageFinderScreen() {
        super(Text.literal("Storage Finder"));
    }

    private static Text label(String name, boolean on) {
        return Text.literal(name + ": " + (on ? "§aON" : "§cOFF"));
    }

    @Override
    protected void init() {
        int w = 160, h = 20, x = this.width / 2 - w / 2, y = this.height / 2 - 50;

        mainBtn = addDrawableChild(ButtonWidget.builder(label("Storage Finder", StorageFinderConfig.enabled), b -> {
            StorageFinderConfig.enabled = !StorageFinderConfig.enabled;
            b.setMessage(label("Storage Finder", StorageFinderConfig.enabled));
        }).dimensions(x, y, w, h).build());

        chestBtn = addDrawableChild(ButtonWidget.builder(label("Chesty", StorageFinderConfig.chests), b -> {
            StorageFinderConfig.chests = !StorageFinderConfig.chests;
            b.setMessage(label("Chesty", StorageFinderConfig.chests));
        }).dimensions(x, y + 28, w, h).build());

        enderBtn = addDrawableChild(ButtonWidget.builder(label("Ender Chesty", StorageFinderConfig.enderChests), b -> {
            StorageFinderConfig.enderChests = !StorageFinderConfig.enderChests;
            b.setMessage(label("Ender Chesty", StorageFinderConfig.enderChests));
        }).dimensions(x, y + 52, w, h).build());

        shulkerBtn = addDrawableChild(ButtonWidget.builder(label("Shulker Boxy", StorageFinderConfig.shulkers), b -> {
            StorageFinderConfig.shulkers = !StorageFinderConfig.shulkers;
            b.setMessage(label("Shulker Boxy", StorageFinderConfig.shulkers));
        }).dimensions(x, y + 76, w, h).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Zamknij"), b -> close())
                .dimensions(x, y + 108, w, h).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, this.title, this.width / 2, this.height / 2 - 75, 0xFFFFFFFF);
    }
}
