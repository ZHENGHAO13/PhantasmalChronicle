package com.phantasm.briefing.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.jetbrains.annotations.Nullable;

/** Shared helpers for resolving a server player behind direct or projectile damage sources. */
public final class EntitySourceUtil {
    private EntitySourceUtil() {
    }

    @Nullable
    public static ServerPlayer resolveServerPlayer(@Nullable Entity sourceEntity) {
        if (sourceEntity instanceof ServerPlayer player) {
            return player;
        }
        if (sourceEntity instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }
}
