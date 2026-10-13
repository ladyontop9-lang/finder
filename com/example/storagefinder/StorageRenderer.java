package com.example.storagefinder;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.class_10799;
import net.minecraft.class_11285;
import net.minecraft.class_1921;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_287;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_3532;
import net.minecraft.class_4587;
import net.minecraft.class_9799;
import net.minecraft.class_9801;

/**
 * Rysuje półprzezroczyste kolorowe boxy na chestach / enderchestach / shulkerach PRZEZ ŚCIANY.
 * Oparte na oficjalnym przykładzie Fabric docs dla 1.21.11 (custom render pipeline).
 */
public final class StorageRenderer {
    private static final RenderPipeline FILLED_THROUGH_WALLS = class_10799.method_67887(
            RenderPipeline.builder(class_10799.field_56860)
                    .withLocation(class_2960.method_60655("storagefinder", "pipeline/filled_through_walls"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build());

    private static final class_9799 ALLOCATOR = new class_9799(class_1921.field_64009);
    private static class_287 buffer;

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static class_11285 vertexBuffer;

    private static final float ALPHA = 0.45f;
    private static final float GROW = 0.002f;

    private StorageRenderer() {}

    public static void init() {
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(StorageRenderer::onRender);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());
    }

    // ---------- Extraction ----------

    private static void onRender(WorldRenderContext context) {
        if (!StorageFinderConfig.enabled) return;

        class_310 mc = class_310.method_1551();
        java.util.List<StorageScanner.Entry> entries = StorageScanner.found();
        boolean showPlayers = StorageFinderConfig.players && mc.field_1687 != null && mc.field_1724 != null;
        if (entries.isEmpty() && !showPlayers) return;

        class_4587 matrices = context.matrices();
        class_243 camera = context.worldState().field_63082.field_63078;

        boolean tracers = StorageFinderConfig.tracers && mc.field_1724 != null;
        class_243 look = tracers ? mc.field_1724.method_5828(mc.method_61966().method_60637(false)) : null;

        matrices.method_22903();
        matrices.method_22904(-camera.field_1352, -camera.field_1351, -camera.field_1350);

        for (StorageScanner.Entry e : entries) {
            ensureBuffer();
            class_2338 p = e.pos();
            float[] c = e.type().rgbFloats();
            renderFilledBox(matrices.method_23760().method_23761(), buffer,
                    p.method_10263() - GROW, p.method_10264() - GROW, p.method_10260() - GROW,
                    p.method_10263() + 1 + GROW, p.method_10264() + 1 + GROW, p.method_10260() + 1 + GROW,
                    c[0], c[1], c[2], ALPHA);
            if (tracers) {
                renderTracer(matrices.method_23760().method_23761(), buffer, camera, look,
                        p.method_10263() + 0.5, p.method_10264() + 0.5, p.method_10260() + 0.5, c[0], c[1], c[2], 0.9f);
            }
        }

        if (showPlayers) {
            float pt = mc.method_61966().method_60637(false);
            for (var other : mc.field_1687.method_18456()) {
                if (other == mc.field_1724) continue;
                double x = class_3532.method_16436(pt, other.field_6014, other.method_23317());
                double y = class_3532.method_16436(pt, other.field_6036, other.method_23318());
                double z = class_3532.method_16436(pt, other.field_5969, other.method_23321());
                float hw = other.method_17681() / 2f;
                float h = other.method_17682();
                ensureBuffer();
                renderFilledBox(matrices.method_23760().method_23761(), buffer,
                        (float) (x - hw), (float) y, (float) (z - hw),
                        (float) (x + hw), (float) (y + h), (float) (z + hw),
                        0.24f, 0.84f, 1.0f, 0.5f);
                if (tracers) {
                    renderTracer(matrices.method_23760().method_23761(), buffer, camera, look,
                            x, y + h / 2.0, z, 0.24f, 0.84f, 1.0f, 0.9f);
                }
            }
        }

        matrices.method_22909();

        if (buffer != null) {
            drawFilledThroughWalls(mc, FILLED_THROUGH_WALLS);
        }
    }

    /** Cienka linia (billboard) od celownika do celu. Grubość rośnie z dystansem, więc na ekranie ma stałą szerokość. */
    private static void renderTracer(Matrix4fc m, class_287 b, class_243 cam, class_243 look,
                                     double tx, double ty, double tz,
                                     float r, float g, float bl, float a) {
        double ax = cam.field_1352 + look.field_1352 * 0.8, ay = cam.field_1351 + look.field_1351 * 0.8, az = cam.field_1350 + look.field_1350 * 0.8;
        double dx = tx - ax, dy = ty - ay, dz = tz - az;

        double vx = (ax + tx) / 2 - cam.field_1352, vy = (ay + ty) / 2 - cam.field_1351, vz = (az + tz) / 2 - cam.field_1350;
        double px = dy * vz - dz * vy, py = dz * vx - dx * vz, pz = dx * vy - dy * vx;
        double len = Math.sqrt(px * px + py * py + pz * pz);
        if (len < 1e-6) return;
        px /= len; py /= len; pz /= len;

        double dEnd = Math.sqrt((tx - cam.field_1352) * (tx - cam.field_1352) + (ty - cam.field_1351) * (ty - cam.field_1351) + (tz - cam.field_1350) * (tz - cam.field_1350));
        double wA = 0.0008, wB = dEnd * 0.0010;

        float a1x = (float) (ax + px * wA), a1y = (float) (ay + py * wA), a1z = (float) (az + pz * wA);
        float a2x = (float) (ax - px * wA), a2y = (float) (ay - py * wA), a2z = (float) (az - pz * wA);
        float b1x = (float) (tx + px * wB), b1y = (float) (ty + py * wB), b1z = (float) (tz + pz * wB);
        float b2x = (float) (tx - px * wB), b2y = (float) (ty - py * wB), b2z = (float) (tz - pz * wB);

        b.method_22918(m, a1x, a1y, a1z).method_22915(r, g, bl, a);
        b.method_22918(m, a2x, a2y, a2z).method_22915(r, g, bl, a);
        b.method_22918(m, b2x, b2y, b2z).method_22915(r, g, bl, a);
        b.method_22918(m, b1x, b1y, b1z).method_22915(r, g, bl, a);

        b.method_22918(m, b1x, b1y, b1z).method_22915(r, g, bl, a);
        b.method_22918(m, b2x, b2y, b2z).method_22915(r, g, bl, a);
        b.method_22918(m, a2x, a2y, a2z).method_22915(r, g, bl, a);
        b.method_22918(m, a1x, a1y, a1z).method_22915(r, g, bl, a);
    }

    private static void ensureBuffer() {
        if (buffer == null) {
            buffer = new class_287(ALLOCATOR,
                    FILLED_THROUGH_WALLS.getVertexFormatMode(),
                    FILLED_THROUGH_WALLS.getVertexFormat());
        }
    }

    private static void renderFilledBox(Matrix4fc m, class_287 b, float minX, float minY, float minZ,
                                        float maxX, float maxY, float maxZ,
                                        float red, float green, float blue, float alpha) {
        // Front
        b.method_22918(m, minX, minY, maxZ).method_22915(red, green, blue, alpha);
        b.method_22918(m, maxX, minY, maxZ).method_22915(red, green, blue, alpha);
        b.method_22918(m, maxX, maxY, maxZ).method_22915(red, green, blue, alpha);
        b.method_22918(m, minX, maxY, maxZ).method_22915(red, green, blue, alpha);
        // Back
        b.method_22918(m, maxX, minY, minZ).method_22915(red, green, blue, alpha);
        b.method_22918(m, minX, minY, minZ).method_22915(red, green, blue, alpha);
        b.method_22918(m, minX, maxY, minZ).method_22915(red, green, blue, alpha);
        b.method_22918(m, maxX, maxY, minZ).method_22915(red, green, blue, alpha);
        // Left
        b.method_22918(m, minX, minY, minZ).method_22915(red, green, blue, alpha);
        b.method_22918(m, minX, minY, maxZ).method_22915(red, green, blue, alpha);
        b.method_22918(m, minX, maxY, maxZ).method_22915(red, green, blue, alpha);
        b.method_22918(m, minX, maxY, minZ).method_22915(red, green, blue, alpha);
        // Right
        b.method_22918(m, maxX, minY, maxZ).method_22915(red, green, blue, alpha);
        b.method_22918(m, maxX, minY, minZ).method_22915(red, green, blue, alpha);
        b.method_22918(m, maxX, maxY, minZ).method_22915(red, green, blue, alpha);
        b.method_22918(m, maxX, maxY, maxZ).method_22915(red, green, blue, alpha);
        // Top
        b.method_22918(m, minX, maxY, maxZ).method_22915(red, green, blue, alpha);
        b.method_22918(m, maxX, maxY, maxZ).method_22915(red, green, blue, alpha);
        b.method_22918(m, maxX, maxY, minZ).method_22915(red, green, blue, alpha);
        b.method_22918(m, minX, maxY, minZ).method_22915(red, green, blue, alpha);
        // Bottom
        b.method_22918(m, minX, minY, minZ).method_22915(red, green, blue, alpha);
        b.method_22918(m, maxX, minY, minZ).method_22915(red, green, blue, alpha);
        b.method_22918(m, maxX, minY, maxZ).method_22915(red, green, blue, alpha);
        b.method_22918(m, minX, minY, maxZ).method_22915(red, green, blue, alpha);
    }

    // ---------- Drawing ----------

    private static void drawFilledThroughWalls(class_310 client, RenderPipeline pipeline) {
        class_9801 builtBuffer = buffer.method_60800();
        class_9801.class_4574 drawParameters = builtBuffer.method_60822();
        VertexFormat format = drawParameters.comp_749();

        GpuBuffer vertices = upload(drawParameters, format, builtBuffer);
        draw(client, pipeline, builtBuffer, drawParameters, vertices, format);

        vertexBuffer.method_71121();
        buffer = null;
    }

    private static GpuBuffer upload(class_9801.class_4574 drawParameters, VertexFormat format, class_9801 builtBuffer) {
        int vertexBufferSize = drawParameters.comp_750() * format.getVertexSize();

        if (vertexBuffer == null || vertexBuffer.method_71312() < vertexBufferSize) {
            if (vertexBuffer != null) {
                vertexBuffer.close();
            }
            vertexBuffer = new class_11285(() -> "storagefinder render pipeline",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize);
        }

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();

        try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(
                vertexBuffer.method_71119().slice(0, builtBuffer.method_60818().remaining()), false, true)) {
            MemoryUtil.memCopy(builtBuffer.method_60818(), mappedView.data());
        }

        return vertexBuffer.method_71119();
    }

    private static void draw(class_310 client, RenderPipeline pipeline, class_9801 builtBuffer,
                             class_9801.class_4574 drawParameters, GpuBuffer vertices, VertexFormat format) {
        GpuBuffer indices;
        VertexFormat.class_5595 indexType;

        if (pipeline.getVertexFormatMode() == VertexFormat.class_5596.field_27382) {
            builtBuffer.method_60819(ALLOCATOR, RenderSystem.getProjectionType().method_65045());
            indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(builtBuffer.method_60821());
            indexType = builtBuffer.method_60822().comp_753();
        } else {
            RenderSystem.class_5590 shapeIndexBuffer = RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
            indices = shapeIndexBuffer.method_68274(drawParameters.comp_751());
            indexType = shapeIndexBuffer.method_31924();
        }

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .method_71106(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "storagefinder render pipeline rendering",
                        client.method_1522().method_71639(), OptionalInt.empty(),
                        client.method_1522().method_71640(), OptionalDouble.empty())) {
            renderPass.setPipeline(pipeline);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);

            renderPass.setVertexBuffer(0, vertices);
            renderPass.setIndexBuffer(indices, indexType);

            renderPass.drawIndexed(0, 0, drawParameters.comp_751(), 1);
        }

        builtBuffer.close();
    }

    public static void close() {
        ALLOCATOR.close();
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }
}
