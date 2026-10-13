package com.example.storagefinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class StorageFinderMod implements ClientModInitializer {
    private static KeyBinding openGui;

    @Override
    public void onInitializeClient() {
        // W 1.21.9+ kategorie klawiszy to obiekt KeyBinding.Category
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("storagefinder", "main"));
        openGui = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.storagefinder.open_gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGui.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new StorageFinderScreen());
                }
            }
        });

        StorageRenderer.init();
    }
}
