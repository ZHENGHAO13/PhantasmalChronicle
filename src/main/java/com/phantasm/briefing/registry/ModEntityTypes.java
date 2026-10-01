package com.phantasm.briefing.registry;

import com.phantasm.briefing.PhantasmBriefing;
import com.phantasm.briefing.entity.StaticQuestNPCEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, PhantasmBriefing.MOD_ID);

    public static final RegistryObject<EntityType<StaticQuestNPCEntity>> STATIC_QUEST_NPC =
            ENTITY_TYPES.register("static_quest_npc", () -> EntityType.Builder
                    .of(StaticQuestNPCEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(8)
                    .build(ResourceLocation.fromNamespaceAndPath(PhantasmBriefing.MOD_ID, "static_quest_npc").toString()));

    private ModEntityTypes() {
    }

    public static void onRegisterAttributes(EntityAttributeCreationEvent event) {
        event.put(STATIC_QUEST_NPC.get(), StaticQuestNPCEntity.createAttributes().build());
    }
}
