package com.phantasm.briefing.client;

import com.phantasm.briefing.entity.StaticQuestNPCEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nonnull;

public final class StaticQuestNPCRenderer extends MobRenderer<StaticQuestNPCEntity, VillagerModel<StaticQuestNPCEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/villager/villager.png");

    public StaticQuestNPCRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(@Nonnull StaticQuestNPCEntity entity) {
        return TEXTURE;
    }
}
