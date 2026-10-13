package com.example.storagefinder;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class StorageFinderMod implements ClientModInitializer {
    private static KeyMapping openGui;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("storagefinder", "main"));

        openGui = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.storagefinder.open_gui",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_K,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGui.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new StorageFinderScreen());
                }
            }
        });

        StorageRenderer.init();
    }
}
