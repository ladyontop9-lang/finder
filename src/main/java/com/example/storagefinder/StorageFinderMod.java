package com.example.storagefinder;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class StorageFinderMod implements ClientModInitializer {
    private static KeyMapping openGui;

    @Override
    public void onInitializeClient() {
        Modules.ALL.size();                 // najpierw rejestr modułów (zapamiętuje domyślne wartości)
        StorageFinderConfig.load();
        Krux.load();
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("storagefinder", "main"));

        openGui = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.storagefinder.open_gui",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_K,
                category));

        // Osobny (domyślnie nieprzypisany) klawisz dla każdego modułu — bind ustawia się w Click GUI środkowym przyciskiem myszy
        for (Module m : Modules.ALL) {
            m.keyMapping = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    "key.storagefinder.module." + m.slug(),
                    InputConstants.Type.KEYSYM,
                    InputConstants.UNKNOWN.getValue(),
                    category));
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGui.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(Krux.licensed() ? new ClickGuiScreen() : Krux.gateScreen(null));
                }
            }
            for (Module m : Modules.ALL) {
                while (m.keyMapping.consumeClick()) {
                    if (client.screen == null) {
                        m.toggle();
                        StorageFinderConfig.save();
                    }
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(StorageScanner::tick);

        ClientTickEvents.END_CLIENT_TICK.register(Krux::tick);
        ClientTickEvents.START_CLIENT_TICK.register(Sprint::tick);
        ClientTickEvents.END_CLIENT_TICK.register(Fullbright::tick);
        ClientTickEvents.END_CLIENT_TICK.register(Zoom::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Zoom.restore());

        ClientTickEvents.START_CLIENT_TICK.register(Aura::tickStart);
        ClientTickEvents.END_CLIENT_TICK.register(Aura::tickEnd);

        Freecam.init();
        Hud.init();
        StorageRenderer.init();
    }
}
