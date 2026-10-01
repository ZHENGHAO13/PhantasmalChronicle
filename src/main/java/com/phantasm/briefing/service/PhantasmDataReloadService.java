package com.phantasm.briefing.service;

import com.phantasm.briefing.data.DialogueDataManager;
import com.phantasm.briefing.data.ManualDataManager;
import com.phantasm.briefing.data.NpcBindingDataManager;
import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.ShopDataManager;
import com.phantasm.briefing.data.TradeDataManager;
import com.phantasm.briefing.data.WalletDataManager;
import com.phantasm.briefing.data.migration.ContentMigrationService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class PhantasmDataReloadService {
    public static final String QUESTS = "quests";
    public static final String MANUALS = "manuals";
    public static final String DIALOGUES = "dialogues";
    public static final String NPCS = "npcs";
    public static final String SHOPS = "shops";
    public static final String TRADES = "trades";
    public static final String WALLETS = "wallets";

    private static final List<String> EDITOR_DOMAIN_ORDER = List.of(
            DIALOGUES, NPCS, QUESTS, MANUALS, WALLETS, SHOPS, TRADES
    );
    private static final Map<String, PreparableReloadListener> EDITOR_LISTENERS = createEditorListeners();
    private PhantasmDataReloadService() {
    }

    /**
     * Reload only editor-managed domains that actually changed on disk. An empty set means
     * there is nothing to reload; callers may still run runtime reconciliation separately.
     */
    public static CompletableFuture<Void> reloadEditorDomains(
            MinecraftServer server,
            Executor preparationExecutor,
            Set<String> changedDomains
    ) {
        LinkedHashSet<String> effectiveDomains = new LinkedHashSet<>();
        if (changedDomains != null) {
            effectiveDomains.addAll(changedDomains);
        }
        effectiveDomains.addAll(ContentMigrationService.migrateExternalPack());
        if (effectiveDomains.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        List<PreparableReloadListener> listeners = EDITOR_DOMAIN_ORDER.stream()
                .filter(effectiveDomains::contains)
                .map(EDITOR_LISTENERS::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (listeners.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return reloadListeners(server, preparationExecutor, listeners);
    }

    public static Set<String> supportedEditorDomains() {
        return Set.copyOf(EDITOR_DOMAIN_ORDER);
    }

    private static CompletableFuture<Void> reloadListeners(
            MinecraftServer server,
            Executor preparationExecutor,
            List<PreparableReloadListener> listeners
    ) {
        ResourceManager resourceManager = server.getResourceManager();
        Executor applyExecutor = server::execute;
        PreparableReloadListener.PreparationBarrier barrier = new PreparableReloadListener.PreparationBarrier() {
            @Override
            public <T> CompletableFuture<T> wait(T preparedObject) {
                return CompletableFuture.completedFuture(preparedObject);
            }
        };

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (PreparableReloadListener listener : listeners) {
            chain = chain.thenCompose(ignored -> listener.reload(
                    barrier,
                    resourceManager,
                    InactiveProfiler.INSTANCE,
                    InactiveProfiler.INSTANCE,
                    preparationExecutor,
                    applyExecutor
            ));
        }
        return chain;
    }

    private static Map<String, PreparableReloadListener> createEditorListeners() {
        LinkedHashMap<String, PreparableReloadListener> listeners = new LinkedHashMap<>();
        listeners.put(DIALOGUES, DialogueDataManager.getInstance());
        listeners.put(NPCS, NpcBindingDataManager.getInstance());
        listeners.put(QUESTS, QuestDataManager.getInstance());
        listeners.put(MANUALS, ManualDataManager.getInstance());
        listeners.put(WALLETS, WalletDataManager.getInstance());
        listeners.put(SHOPS, ShopDataManager.getInstance());
        listeners.put(TRADES, TradeDataManager.getInstance());
        return Map.copyOf(listeners);
    }
}
