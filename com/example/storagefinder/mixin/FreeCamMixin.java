package com.example.storagefinder.mixin;

import com.example.storagefinder.StorageFreeCam;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class FreeCamMixin {
    @Shadow protected abstract void setPos(double x, double y, double z);

    @Inject(method = "update", at = @At("TAIL"))
    private void storagefinder$freeCam(CallbackInfo ci) {
        if (!StorageFreeCam.isActive()) return;
        Vec3d p = StorageFreeCam.cameraPos();
        this.setPos(p.x, p.y, p.z);
    }
}
