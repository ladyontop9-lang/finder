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
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

/**
 * Rysuje półprzezroczyste kolorowe boxy na chestach / enderchestach / shulkerach PRZEZ ŚCIANY.
 * Oparte na oficjalnym przykładzie Fabric docs dla 1.21.11 (custom render pipeline).
 */
public final class StorageRenderer {
    private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("storagefinder", "pipeline/filled_through_walls"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build());

    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static BufferBuilder buffer;

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static MappableRingBuffer vertexBuffer;

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

        Minecraft mc = Minecraft.getInstance();
        java.util.List<StorageScanner.Entry> entries = StorageScanner.found();
        boolean showPlayers = StorageFinderConfig.players && mc.level != null && mc.player != null;
        if (entries.isEmpty() && !showPlayers) return;

        PoseStack matrices = context.matrices();
        Vec3 camera = context.worldState().cameraRenderState.pos;

        boolean tracers = StorageFinderConfig.tracers && mc.player != null;
        Vec3 look = null;
        if (tracers) {
            var viewer = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
            look = viewer.getViewVector(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        }

        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        for (StorageScanner.Entry e : entries) {
            BlockPos p = e.pos();
            if (mc.level != null && mc.level.getBlockState(p).isAir()) continue;
            ensureBuffer();
            float[] c = e.type().rgbFloats();
            renderFilledBox(matrices.last().pose(), buffer,
                    p.getX() - GROW, p.getY() - GROW, p.getZ() - GROW,
                    p.getX() + 1 + GROW, p.getY() + 1 + GROW, p.getZ() + 1 + GROW,
                    c[0], c[1], c[2], ALPHA);
            if (tracers) {
                renderTracer(matrices.last().pose(), buffer, camera, look,
                        p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, c[0], c[1], c[2], 0.9f);
            }
        }

        if (showPlayers) {
            float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            for (var other : mc.level.players()) {
                if (other == mc.player) continue;
                double x = Mth.lerp(pt, other.xo, other.getX());
                double y = Mth.lerp(pt, other.yo, other.getY());
                double z = Mth.lerp(pt, other.zo, other.getZ());
                float hw = other.getBbWidth() / 2f;
                float h = other.getBbHeight();
                ensureBuffer();
                renderFilledBox(matrices.last().pose(), buffer,
                        (float) (x - hw), (float) y, (float) (z - hw),
                        (float) (x + hw), (float) (y + h), (float) (z + hw),
                        0.24f, 0.84f, 1.0f, 0.5f);
                if (tracers) {
                    renderTracer(matrices.last().pose(), buffer, camera, look,
                            x, y + h / 2.0, z, 0.24f, 0.84f, 1.0f, 0.9f);
                }
            }
        }

        matrices.popPose();

        if (buffer != null) {
            drawFilledThroughWalls(mc, FILLED_THROUGH_WALLS);
        }
    }

    /** Cienka linia (billboard) od celownika do celu. Grubość rośnie z dystansem, więc na ekranie ma stałą szerokość. */
    private static void renderTracer(Matrix4fc m, BufferBuilder b, Vec3 cam, Vec3 look,
                                     double tx, double ty, double tz,
                                     float r, float g, float bl, float a) {
        double ax = cam.x + look.x * 0.8, ay = cam.y + look.y * 0.8, az = cam.z + look.z * 0.8;
        double dx = tx - ax, dy = ty - ay, dz = tz - az;

        double vx = (ax + tx) / 2 - cam.x, vy = (ay + ty) / 2 - cam.y, vz = (az + tz) / 2 - cam.z;
        double px = dy * vz - dz * vy, py = dz * vx - dx * vz, pz = dx * vy - dy * vx;
        double len = Math.sqrt(px * px + py * py + pz * pz);
        if (len < 1e-6) return;
        px /= len; py /= len; pz /= len;

        double dEnd = Math.sqrt((tx - cam.x) * (tx - cam.x) + (ty - cam.y) * (ty - cam.y) + (tz - cam.z) * (tz - cam.z));
        double wA = 0.0008, wB = dEnd * 0.0010;

        float a1x = (float) (ax + px * wA), a1y = (float) (ay + py * wA), a1z = (float) (az + pz * wA);
        float a2x = (float) (ax - px * wA), a2y = (float) (ay - py * wA), a2z = (float) (az - pz * wA);
        float b1x = (float) (tx + px * wB), b1y = (float) (ty + py * wB), b1z = (float) (tz + pz * wB);
        float b2x = (float) (tx - px * wB), b2y = (float) (ty - py * wB), b2z = (float) (tz - pz * wB);

        b.addVertex(m, a1x, a1y, a1z).setColor(r, g, bl, a);
        b.addVertex(m, a2x, a2y, a2z).setColor(r, g, bl, a);
        b.addVertex(m, b2x, b2y, b2z).setColor(r, g, bl, a);
        b.addVertex(m, b1x, b1y, b1z).setColor(r, g, bl, a);

        b.addVertex(m, b1x, b1y, b1z).setColor(r, g, bl, a);
        b.addVertex(m, b2x, b2y, b2z).setColor(r, g, bl, a);
        b.addVertex(m, a2x, a2y, a2z).setColor(r, g, bl, a);
        b.addVertex(m, a1x, a1y, a1z).setColor(r, g, bl, a);
    }

    private static void ensureBuffer() {
        if (buffer == null) {
            buffer = new BufferBuilder(ALLOCATOR,
                    FILLED_THROUGH_WALLS.getVertexFormatMode(),
                    FILLED_THROUGH_WALLS.getVertexFormat());
        }
    }

    private static void renderFilledBox(Matrix4fc m, BufferBuilder b, float minX, float minY, float minZ,
                                        float maxX, float maxY, float maxZ,
                                        float red, float green, float blue, float alpha) {
        // Front
        b.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
        b.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        b.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        b.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        // Back
        b.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
        b.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        b.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);
        b.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        // Left
        b.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        b.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
        b.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        b.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);
        // Right
        b.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        b.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
        b.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        b.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        // Top
        b.addVertex(m, minX, maxY, maxZ).setColor(red, green, blue, alpha);
        b.addVertex(m, maxX, maxY, maxZ).setColor(red, green, blue, alpha);
        b.addVertex(m, maxX, maxY, minZ).setColor(red, green, blue, alpha);
        b.addVertex(m, minX, maxY, minZ).setColor(red, green, blue, alpha);
        // Bottom
        b.addVertex(m, minX, minY, minZ).setColor(red, green, blue, alpha);
        b.addVertex(m, maxX, minY, minZ).setColor(red, green, blue, alpha);
        b.addVertex(m, maxX, minY, maxZ).setColor(red, green, blue, alpha);
        b.addVertex(m, minX, minY, maxZ).setColor(red, green, blue, alpha);
    }

    // ---------- Drawing ----------

    private static void drawFilledThroughWalls(Minecraft client, RenderPipeline pipeline) {
        MeshData builtBuffer = buffer.buildOrThrow();
        MeshData.DrawState drawParameters = builtBuffer.drawState();
        VertexFormat format = drawParameters.format();

        GpuBuffer vertices = upload(drawParameters, format, builtBuffer);
        draw(client, pipeline, builtBuffer, drawParameters, vertices, format);

        vertexBuffer.rotate();
        buffer = null;
    }

    private static GpuBuffer upload(MeshData.DrawState drawParameters, VertexFormat format, MeshData builtBuffer) {
        int vertexBufferSize = drawParameters.vertexCount() * format.getVertexSize();

        if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
            if (vertexBuffer != null) {
                vertexBuffer.close();
            }
            vertexBuffer = new MappableRingBuffer(() -> "storagefinder render pipeline",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize);
        }

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();

        try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(
                vertexBuffer.currentBuffer().slice(0, builtBuffer.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(builtBuffer.vertexBuffer(), mappedView.data());
        }

        return vertexBuffer.currentBuffer();
    }

    private static void draw(Minecraft client, RenderPipeline pipeline, MeshData builtBuffer,
                             MeshData.DrawState drawParameters, GpuBuffer vertices, VertexFormat format) {
        GpuBuffer indices;
        VertexFormat.IndexType indexType;

        if (pipeline.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
            builtBuffer.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
            indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(builtBuffer.indexBuffer());
            indexType = builtBuffer.drawState().indexType();
        } else {
            RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(pipeline.getVertexFormatMode());
            indices = shapeIndexBuffer.getBuffer(drawParameters.indexCount());
            indexType = shapeIndexBuffer.type();
        }

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "storagefinder render pipeline rendering",
                        client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
                        client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            renderPass.setPipeline(pipeline);

            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);

            renderPass.setVertexBuffer(0, vertices);
            renderPass.setIndexBuffer(indices, indexType);

            renderPass.drawIndexed(0, 0, drawParameters.indexCount(), 1);
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
