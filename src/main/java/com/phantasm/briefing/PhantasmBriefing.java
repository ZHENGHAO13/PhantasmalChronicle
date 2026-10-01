package com.phantasm.briefing;

import com.mojang.logging.LogUtils;
import com.phantasm.briefing.capability.ModCapabilities;
import com.phantasm.briefing.config.PhantasmBriefingClientConfig;
import com.phantasm.briefing.event.PlayerDataEvents;
import com.phantasm.briefing.event.DialogueDataReloadEvents;
import com.phantasm.briefing.event.QuestEntityHintEvents;
import com.phantasm.briefing.event.QuestObjectiveProgressEvents;
import com.phantasm.briefing.event.DialogueSessionEvents;
import com.phantasm.briefing.event.QuestNpcCapabilityEvents;
import com.phantasm.briefing.event.QuestNpcCommandEvents;
import com.phantasm.briefing.event.QuestNpcProtectionEvents;
import com.phantasm.briefing.event.NpcStructureSpawnEvents;
import com.phantasm.briefing.event.StructureSearchCompatEvents;
import com.phantasm.briefing.event.EditorHotReloadEvents;
import com.phantasm.briefing.integration.FTBIntegrationHelper;
import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.registry.ModEntityTypes;
import com.phantasm.briefing.registry.ModItems;
import com.phantasm.briefing.service.QuestRuntimeService;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(PhantasmBriefing.MOD_ID)
public final class PhantasmBriefing {
    public static final String MOD_ID = "phantasmbriefing";

    public static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings("removal")
    public PhantasmBriefing(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        ModNetwork.register();
        FTBIntegrationHelper.registerQuestCompletionListener(QuestRuntimeService::onFtbQuestCompleted);

        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, PhantasmBriefingClientConfig.SPEC, "phantasmbriefing-client.toml");

        ModEntityTypes.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);

        modEventBus.addListener(ModCapabilities::onRegisterCapabilities);
        modEventBus.addListener(ModEntityTypes::onRegisterAttributes);

        MinecraftForge.EVENT_BUS.register(new QuestNpcCapabilityEvents());
        MinecraftForge.EVENT_BUS.register(new QuestNpcProtectionEvents());
        MinecraftForge.EVENT_BUS.register(new NpcStructureSpawnEvents());
        MinecraftForge.EVENT_BUS.register(new QuestNpcCommandEvents());
        MinecraftForge.EVENT_BUS.register(new DialogueDataReloadEvents());
        MinecraftForge.EVENT_BUS.register(new DialogueSessionEvents());
        MinecraftForge.EVENT_BUS.register(new QuestEntityHintEvents());
        MinecraftForge.EVENT_BUS.register(new QuestObjectiveProgressEvents());
        MinecraftForge.EVENT_BUS.register(new PlayerDataEvents());
        MinecraftForge.EVENT_BUS.register(new EditorHotReloadEvents());
        MinecraftForge.EVENT_BUS.register(new StructureSearchCompatEvents());
    }

}
