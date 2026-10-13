package com.example.storagefinder;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

public final class StorageRenderer {
    private StorageRenderer() {}

    public static void init() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(StorageRenderer::render);
    }

    private static void render(WorldRenderContext ctx) {
        if (!StorageFinderConfig.enabled) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (world == null || mc.player == null) return;

        Vec3d cam = ctx.camera().getPos();
        MatrixStack matrices = ctx.matrixStack();
        VertexConsumer vc = ctx.consumers().getBuffer(StorageRenderLayers.LINES_NO_DEPTH);

        ChunkPos center = mc.player.getChunkPos();
        int r = Math.min(StorageFinderConfig.radiusChunks, mc.options.getViewDistance().getValue());

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        for (int cx = center.x - r; cx <= center.x + r; cx++) {
            for (int cz = center.z - r; cz <= center.z + r; cz++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    float[] c = color(be);
                    if (c == null) continue;
                    VertexRendering.drawBox(matrices, vc, new Box(be.getPos()), c[0], c[1], c[2], 1.0f);
                }
            }
        }

        matrices.pop();
    }

    /** Zwraca kolor RGB albo null, jeśli ten typ nie jest włączony / nie jest magazynem. */
    private static float[] color(BlockEntity be) {
        if (be instanceof ShulkerBoxBlockEntity && StorageFinderConfig.shulkers) return new float[]{1f, 0.2f, 1f};
        if (be instanceof EnderChestBlockEntity && StorageFinderConfig.enderChests) return new float[]{0.6f, 0.2f, 1f};
        if (be instanceof ChestBlockEntity && StorageFinderConfig.chests) return new float[]{1f, 0.6f, 0.1f}; // także trapped chest
        return null;
    }
}
