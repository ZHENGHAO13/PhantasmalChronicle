package com.phantasm.briefing.client.vfx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class VFXManager {
    private static final VFXManager INSTANCE = new VFXManager();

    private final List<PhantasmParticle> particles = new ArrayList<>();

    private VFXManager() {
    }

    public static VFXManager getInstance() {
        return INSTANCE;
    }

    public void clear() {
        this.particles.clear();
    }

    public void spawnSuspensionParticles(double x, double y, double z, int count, int color) {
        RandomSource random = RandomSource.create();
        Vec3 center = new Vec3(x, y, z);
        for (int i = 0; i < count; i++) {
            double speed = 0.08D + (random.nextDouble() * 0.14D);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double vertical = (random.nextDouble() - 0.5D) * 0.12D;
            Vec3 velocity = new Vec3(
                    Math.cos(angle) * speed,
                    vertical,
                    Math.sin(angle) * speed
            );
            Vec3 spawnPosition = center.add(
                    (random.nextDouble() - 0.5D) * 0.25D,
                    random.nextDouble() * 0.65D,
                    (random.nextDouble() - 0.5D) * 0.25D
            );
            float size = 0.03F + (random.nextFloat() * 0.025F);
            int lifetime = 22 + random.nextInt(18);
            this.particles.add(new PhantasmParticle(spawnPosition, velocity, color, size, lifetime));
        }
    }

    public void tick() {
        Iterator<PhantasmParticle> iterator = this.particles.iterator();
        while (iterator.hasNext()) {
            PhantasmParticle particle = iterator.next();
            particle.tick();
            if (!particle.isAlive()) {
                iterator.remove();
            }
        }
    }

    public void render(PoseStack poseStack, Camera camera, float partialTick) {
        if (this.particles.isEmpty()) {
            return;
        }

        Vec3 cameraPosition = camera.getPosition();
        Quaternionf rotation = new Quaternionf(camera.rotation());
        Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(rotation);
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(rotation);

        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (PhantasmParticle particle : this.particles) {
            renderParticleQuad(bufferBuilder, matrix, particle, right, up, partialTick);
        }

        BufferUploader.drawWithShader(bufferBuilder.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static void renderParticleQuad(
            BufferBuilder bufferBuilder,
            Matrix4f matrix,
            PhantasmParticle particle,
            Vector3f right,
            Vector3f up,
            float partialTick
    ) {
        Vec3 position = particle.getPosition(partialTick);
        float size = particle.getSize();
        float alpha = particle.getAlpha(partialTick);

        Vector3f rightScaled = new Vector3f(right).mul(size);
        Vector3f upScaled = new Vector3f(up).mul(size);

        Vector3f topLeft = new Vector3f((float) position.x, (float) position.y, (float) position.z).sub(rightScaled).add(upScaled);
        Vector3f bottomLeft = new Vector3f((float) position.x, (float) position.y, (float) position.z).sub(rightScaled).sub(upScaled);
        Vector3f bottomRight = new Vector3f((float) position.x, (float) position.y, (float) position.z).add(rightScaled).sub(upScaled);
        Vector3f topRight = new Vector3f((float) position.x, (float) position.y, (float) position.z).add(rightScaled).add(upScaled);

        bufferBuilder.vertex(matrix, topLeft.x, topLeft.y, topLeft.z)
                .color(particle.getRed(), particle.getGreen(), particle.getBlue(), alpha)
                .endVertex();
        bufferBuilder.vertex(matrix, bottomLeft.x, bottomLeft.y, bottomLeft.z)
                .color(particle.getRed(), particle.getGreen(), particle.getBlue(), alpha)
                .endVertex();
        bufferBuilder.vertex(matrix, bottomRight.x, bottomRight.y, bottomRight.z)
                .color(particle.getRed(), particle.getGreen(), particle.getBlue(), alpha)
                .endVertex();
        bufferBuilder.vertex(matrix, topRight.x, topRight.y, topRight.z)
                .color(particle.getRed(), particle.getGreen(), particle.getBlue(), alpha)
                .endVertex();
    }
}
