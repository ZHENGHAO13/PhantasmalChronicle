package com.phantasm.briefing.client.vfx;

import net.minecraft.world.phys.Vec3;

public final class PhantasmParticle {
    private Vec3 previousPosition;
    private Vec3 position;
    private Vec3 velocity;
    private final float red;
    private final float green;
    private final float blue;
    private final float size;
    private final int lifetime;
    private int age;

    public PhantasmParticle(Vec3 position, Vec3 velocity, int color, float size, int lifetime) {
        this.previousPosition = position;
        this.position = position;
        this.velocity = velocity;
        this.red = ((color >> 16) & 0xFF) / 255.0F;
        this.green = ((color >> 8) & 0xFF) / 255.0F;
        this.blue = (color & 0xFF) / 255.0F;
        this.size = size;
        this.lifetime = Math.max(1, lifetime);
        this.age = 0;
    }

    public void tick() {
        this.previousPosition = this.position;
        this.position = this.position.add(this.velocity);
        this.velocity = this.velocity.scale(0.85D);
        this.age++;
    }

    public boolean isAlive() {
        return this.age < this.lifetime;
    }

    public Vec3 getPosition(float partialTick) {
        return this.previousPosition.lerp(this.position, partialTick);
    }

    public float getAlpha(float partialTick) {
        float lifeProgress = (this.age + partialTick) / this.lifetime;
        return Math.max(0.0F, 1.0F - lifeProgress);
    }

    public float getRed() {
        return this.red;
    }

    public float getGreen() {
        return this.green;
    }

    public float getBlue() {
        return this.blue;
    }

    public float getSize() {
        return this.size;
    }
}
