package com.example.storagefinder;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

/**
 * Linie rysowane PRZEZ ŚCIANY (bez testu głębi).
 * Jeśli ta klasa się nie kompiluje po zmianach API, tymczasowo użyj RenderLayer.getLines()
 * (wtedy obrys będzie widoczny tylko gdy nie jest zasłonięty).
 */
public final class StorageRenderLayers {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("storagefinder", "pipeline/lines_no_depth"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build());

    public static final RenderLayer LINES_NO_DEPTH = RenderLayer.of(
            "storagefinder_lines_no_depth", 1536, PIPELINE,
            RenderLayer.MultiPhaseParameters.builder().build(false));

    private StorageRenderLayers() {}
}
