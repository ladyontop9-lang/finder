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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
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
        ClientLevel level = mc.level;
        Player player = mc.player;
        if (level == null || player == null) return;

        PoseStack matrices = context.matrices();
        Vec3 camera = context.worldState().cameraRenderState.pos;

        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        ChunkPos center = player.chunkPosition();
        int r = Math.min(StorageFinderConfig.radiusChunks, mc.options.renderDistance().get());

        for (int cx = center.x - r; cx <= center.x + r; cx++) {
            for (int cz = center.z - r; cz <= center.z + r; cz++) {
                if (!(level.getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    float[] c = color(be);
                    if (c == null) continue;

                    if (buffer == null) {
                        buffer = new BufferBuilder(ALLOCATOR,
                                FILLED_THROUGH_WALLS.getVertexFormatMode(),
                                FILLED_THROUGH_WALLS.getVertexFormat());
                    }

                    BlockPos p = be.getBlockPos();
                    renderFilledBox(matrices.last().pose(), buffer,
                            p.getX() - GROW, p.getY() - GROW, p.getZ() - GROW,
                            p.getX() + 1 + GROW, p.getY() + 1 + GROW, p.getZ() + 1 + GROW,
                            c[0], c[1], c[2], ALPHA);
                }
            }
        }

        matrices.popPose();

        if (buffer != null) {
            drawFilledThroughWalls(mc, FILLED_THROUGH_WALLS);
        }
    }

    /** Kolor RGB albo null, jeśli ten typ jest wyłączony / to nie magazyn. */
    private static float[] color(BlockEntity be) {
        if (be instanceof ShulkerBoxBlockEntity && StorageFinderConfig.shulkers) return new float[]{1f, 0.2f, 1f};
        if (be instanceof EnderChestBlockEntity && StorageFinderConfig.enderChests) return new float[]{0.6f, 0.2f, 1f};
        if (be instanceof ChestBlockEntity && StorageFinderConfig.chests) return new float[]{1f, 0.6f, 0.1f};
        return null;
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
