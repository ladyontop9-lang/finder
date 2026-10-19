package com.example.storagefinder.mixin;

import com.example.storagefinder.NameProtect;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Wspólny punkt, przez który przechodzi cały tekst rysowany i mierzony przez grę.
 * Jeśli któraś sygnatura w tej wersji gry się różni, wstrzyknięcie jest po cichu pomijane
 * (krux.mixins.json ma "required": false), a gra działa normalnie — tylko bez Name Protect.
 */
@Mixin(StringDecomposer.class)
public abstract class StringDecomposerMixin {
    @ModifyVariable(
            method = "iterate(Ljava/lang/String;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At("HEAD"), argsOnly = true)
    private static String krux$iterate(String text) {
        return NameProtect.apply(text);
    }

    @ModifyVariable(
            method = "iterateFormatted(Ljava/lang/String;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At("HEAD"), argsOnly = true)
    private static String krux$iterateFormatted(String text) {
        return NameProtect.apply(text);
    }
}
