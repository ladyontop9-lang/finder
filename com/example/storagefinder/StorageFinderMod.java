package com.example.storagefinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.class_2960;
import net.minecraft.class_304;
import net.minecraft.class_3675;
import org.lwjgl.glfw.GLFW;

public class StorageFinderMod implements ClientModInitializer {
    private static class_304 openGui;

    @Override
    public void onInitializeClient() {
        StorageFinderConfig.load();
        class_304.class_11900 category = class_304.class_11900.method_74698(
                class_2960.method_60655("storagefinder", "main"));

        openGui = KeyBindingHelper.registerKeyBinding(new class_304(
                "key.storagefinder.open_gui",
                class_3675.class_307.field_1668,
                InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_RIGHT_SHIFT),
                category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGui.method_1436()) {
                if (client.field_1755 == null) {
                    client.method_1507(new StorageFinderScreen());
                }
            }
        });

        KeyBinding freeCamKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.storagefinder.freecam",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (freeCamKey.wasPressed()) {
                StorageFreeCam.toggle(client);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(StorageFreeCam::tick);
        ClientTickEvents.END_CLIENT_TICK.register(StorageScanner::tick);

        StorageRenderer.init();
    }
}
