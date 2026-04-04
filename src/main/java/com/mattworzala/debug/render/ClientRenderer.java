package com.mattworzala.debug.render;

import com.mattworzala.debug.shape.Shape;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientRenderer {

    private final Map<Identifier, Shape> shapes = new ConcurrentHashMap<>();

    public void register() {
        WorldRenderEvents.END_MAIN.register(this::render);
    }

    public void add(@NotNull Identifier id, @NotNull Shape shape) {
        shapes.put(id, shape);
    }

    public void remove(@NotNull Identifier id) {
        shapes.remove(id);
    }

    public void remove(@NotNull String namespace) {
        shapes.keySet().removeIf(id -> id.getNamespace().equals(namespace));
    }

    public void clear() {
        shapes.clear();
    }

    private void render(WorldRenderContext worldContext) {
        if (shapes.isEmpty()) return;

        MatrixStack matrices = worldContext.matrices();
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);
        MatrixStack.Entry entry = matrices.peek();

        try (BufferAllocator allocator = new BufferAllocator(196608)) {
            VertexConsumerProvider.Immediate consumers = VertexConsumerProvider.immediate(allocator);
            DebugRenderContext context = new DebugRenderContext(entry, consumers, new Vector3d(camPos.x, camPos.y, camPos.z));

            shapes.values().stream().sorted((a, b) -> {
                double aDist = a.distanceTo(camPos);
                double bDist = b.distanceTo(camPos);
                return Double.compare(bDist, aDist);
            }).forEach((shape) -> {
                shape.render(context);
            });

            consumers.draw();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            matrices.pop();
        }
    }
}