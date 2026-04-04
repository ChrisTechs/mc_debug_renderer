package com.mattworzala.debug.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.function.Consumer;

public class DebugRenderContext {

    private static final RenderPipeline TOP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("debug_renderer", "top_overlay"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withCull(false)
                    .build()
    );
    private static final net.minecraft.client.render.RenderLayer LAYER_TOP = net.minecraft.client.render.RenderLayer.of(
            "debug_top", RenderSetup.builder(TOP_PIPELINE).build());

    private static final RenderPipeline INLINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("debug_renderer", "inline_overlay"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withCull(false)
                    .build()
    );
    private static final net.minecraft.client.render.RenderLayer LAYER_INLINE = net.minecraft.client.render.RenderLayer.of(
            "debug_inline", RenderSetup.builder(INLINE_PIPELINE).build());

    private static final float epsilon = 0.00001f;

    private final MatrixStack.Entry matrixEntry;
    private final VertexConsumerProvider consumers;
    private final Vector3d camPos;

    private VertexConsumer currentBuffer = null;
    private RenderType currentRenderType = null;

    private float a = 1f;
    private float r = 1f;
    private float g = 1f;
    private float b = 1f;
    private float lineWidth = 1.0f;

    private Vector3d lastLineVertex = null;

    public DebugRenderContext(MatrixStack.Entry matrixEntry, VertexConsumerProvider consumers, Vector3d camPos) {
        this.matrixEntry = matrixEntry;
        this.consumers = consumers;
        this.camPos = camPos;
    }

    private static Vector3d diff(Vector3d v1, Vector3d v2) {
        return new Vector3d(v1).sub(v2);
    }

    private static Vector3f diffd2f(Vector3d v1, Vector3d v2) {
        return new Vector3f((float) (v1.x - v2.x), (float) (v1.y - v2.y), (float) (v1.z - v2.z));
    }

    public void setLineWidth(float width) {
        this.lineWidth = width;
    }

    public void submit(@NotNull Consumer<DebugRenderContext> func, @NotNull RenderType renderType, @NotNull RenderLayer layer) {
        net.minecraft.client.render.RenderLayer mcLayer = layer == RenderLayer.TOP ? LAYER_TOP : LAYER_INLINE;
        this.currentBuffer = consumers.getBuffer(mcLayer);
        this.currentRenderType = renderType;
        this.lastLineVertex = null;

        func.accept(this);

        this.currentBuffer = null;
        this.currentRenderType = null;
    }

    public void color(int argb) {
        this.a = ((argb >> 24) & 0xFF) / 255f;
        this.r = ((argb >> 16) & 0xFF) / 255f;
        this.g = ((argb >> 8) & 0xFF) / 255f;
        this.b = (argb & 0xFF) / 255f;
    }

    public void vertex(@NotNull Vec3d point) {
        vertex((float) point.x, (float) point.y, (float) point.z);
    }

    public void vertex(float x, float y, float z) {
        if (currentBuffer == null) return;

        if (currentRenderType == RenderType.QUADS) {
            currentBuffer.vertex(matrixEntry.getPositionMatrix(), x, y, z).color(r, g, b, a);
        } else if (currentRenderType == RenderType.LINES || currentRenderType == RenderType.LINE_STRIP) {
            if (lastLineVertex == null) {
                lastLineVertex = new Vector3d(x, y, z);
            } else {
                drawBillboardLine(lastLineVertex, new Vector3d(x, y, z));
                lastLineVertex = null;
            }
        }
    }

    private void drawBillboardLine(Vector3d start, Vector3d end) {
        float t = Math.max(0.002f, this.lineWidth * 0.01f);

        Vector3d lineDir = diff(end, start);
        if (lineDir.lengthSquared() < epsilon) return;
        lineDir.normalize();

        Vector3d camDir = diff(start, camPos);
        if (camDir.lengthSquared() < epsilon) camDir.set(0, 1, 0);
        camDir.normalize();

        Vector3d widthDir = lineDir.cross(camDir, new Vector3d());
        if (widthDir.lengthSquared() < epsilon) widthDir.set(1, 0, 0);
        widthDir.normalize().mul(t / 2.0f);

        currentBuffer.vertex(matrixEntry, diffd2f(start, widthDir)).color(r, g, b, a);
        currentBuffer.vertex(matrixEntry, diffd2f(start, widthDir.negate(new Vector3d()))).color(r, g, b, a);
        currentBuffer.vertex(matrixEntry, diffd2f(end, widthDir.negate(new Vector3d()))).color(r, g, b, a);
        currentBuffer.vertex(matrixEntry, diffd2f(end, widthDir)).color(r, g, b, a);
    }
}