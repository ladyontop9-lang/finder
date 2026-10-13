package com.example.storagefinder;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class StorageFinderScreen extends Screen {

    public StorageFinderScreen() {
        super(Component.literal("Storage Finder"));
    }

    private static Component label(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "§aON" : "§cOFF"));
    }

    @Override
    protected void init() {
        int w = 160, h = 20, x = this.width / 2 - w / 2, y = this.height / 2 - 50;

        addRenderableWidget(Button.builder(label("Storage Finder", StorageFinderConfig.enabled), b -> {
            StorageFinderConfig.enabled = !StorageFinderConfig.enabled;
            b.setMessage(label("Storage Finder", StorageFinderConfig.enabled));
        }).bounds(x, y, w, h).build());

        addRenderableWidget(Button.builder(label("Chesty", StorageFinderConfig.chests), b -> {
            StorageFinderConfig.chests = !StorageFinderConfig.chests;
            b.setMessage(label("Chesty", StorageFinderConfig.chests));
        }).bounds(x, y + 28, w, h).build());

        addRenderableWidget(Button.builder(label("Ender Chesty", StorageFinderConfig.enderChests), b -> {
            StorageFinderConfig.enderChests = !StorageFinderConfig.enderChests;
            b.setMessage(label("Ender Chesty", StorageFinderConfig.enderChests));
        }).bounds(x, y + 52, w, h).build());

        addRenderableWidget(Button.builder(label("Shulker Boxy", StorageFinderConfig.shulkers), b -> {
            StorageFinderConfig.shulkers = !StorageFinderConfig.shulkers;
            b.setMessage(label("Shulker Boxy", StorageFinderConfig.shulkers));
        }).bounds(x, y + 76, w, h).build());

        addRenderableWidget(Button.builder(Component.literal("Zamknij"), b -> onClose())
                .bounds(x, y + 108, w, h).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 75, 0xFFFFFFFF);
    }
}
