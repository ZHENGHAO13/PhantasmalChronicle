package com.phantasm.briefing.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.DialogueVoiceDataManager;
import com.phantasm.briefing.dialogue.sound.DialogueVoiceDefinition;
import com.phantasm.briefing.integration.FTBIntegrationHelper;
import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.network.packet.PreviewDialogueVoiceS2CPacket;
import com.phantasm.briefing.network.packet.RefreshQuestContentS2CPacket;
import com.phantasm.briefing.service.PhantasmDataReloadService;
import com.phantasm.briefing.service.DialogueFlowCoordinator;
import com.phantasm.briefing.service.EditorReloadReconciliationService;
import com.phantasm.briefing.service.NpcStructureSpawnService;
import com.phantasm.briefing.service.QuestEntityHintService;
import com.phantasm.briefing.service.QuestTrackerService;
import com.phantasm.briefing.service.QuestRuntimeService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import net.minecraftforge.network.PacketDistributor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class EditorHotReloadEvents {
    public static final int BRIDGE_PORT = 38471;
    public static final String RELOAD_PATH = "/phantasmbriefing/reload";
    public static final String RESOURCES_PATH = "/phantasmbriefing/resources";
    public static final String PREVIEW_DIALOGUE_VOICE_PATH = "/phantasmbriefing/preview-dialogue-voice";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MAKER_HEADER = "X-Phantasm-Maker";

    private HttpServer bridge;
    private ExecutorService bridgeExecutor;
    private MinecraftServer activeServer;
    private boolean reloadInProgress;
    private PendingEditorSave queuedSave;
    private EditorReloadReconciliationService.SaveManifest lastAppliedManifest;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        stopBridge();
        this.activeServer = event.getServer();
        this.reloadInProgress = false;
        this.queuedSave = null;
        this.lastAppliedManifest = null;
        startBridge();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        stopBridge();
        this.activeServer = null;
        this.reloadInProgress = false;
        this.queuedSave = null;
        this.lastAppliedManifest = null;
    }

    private void startBridge() {
        try {
            this.bridge = HttpServer.create(new InetSocketAddress("127.0.0.1", BRIDGE_PORT), 0);
            this.bridge.createContext(RELOAD_PATH, this::handleReloadRequest);
            this.bridge.createContext(RESOURCES_PATH, this::handleResourcesRequest);
            this.bridge.createContext(PREVIEW_DIALOGUE_VOICE_PATH, this::handlePreviewDialogueVoiceRequest);
            this.bridgeExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "Phantasm-Maker-Hot-Reload");
                thread.setDaemon(true);
                return thread;
            });
            this.bridge.setExecutor(this.bridgeExecutor);
            this.bridge.start();
            LOGGER.info(
                    "[PhantasmBriefing] Phantasm Maker bridge listening on http://127.0.0.1:{} (reload={}, resources={}, voicePreview={}, voices={})",
                    BRIDGE_PORT,
                    RELOAD_PATH,
                    RESOURCES_PATH,
                    PREVIEW_DIALOGUE_VOICE_PATH,
                    DialogueVoiceDataManager.getInstance().getAllVoices().size()
            );
        } catch (IOException exception) {
            LOGGER.warn(
                    "[PhantasmBriefing] Could not start the local editor hot reload bridge on port {}: {}",
                    BRIDGE_PORT,
                    exception.getMessage()
            );
            stopBridge();
        }
    }

    private void stopBridge() {
        if (this.bridge != null) {
            this.bridge.stop(0);
            this.bridge = null;
        }
        if (this.bridgeExecutor != null) {
            this.bridgeExecutor.shutdownNow();
            this.bridgeExecutor = null;
        }
    }

    private void handleReloadRequest(HttpExchange exchange) throws IOException {
        try (exchange) {
            Headers requestHeaders = exchange.getRequestHeaders();
            String origin = requestHeaders.getFirst("Origin");
            addCorsHeaders(exchange.getResponseHeaders(), origin);

            if (!isAllowedOrigin(origin)) {
                sendJson(exchange, 403, "{\"accepted\":false,\"message\":\"origin_not_allowed\"}");
                return;
            }
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"accepted\":false,\"message\":\"post_required\"}");
                return;
            }
            if (!"1".equals(requestHeaders.getFirst(MAKER_HEADER))) {
                sendJson(exchange, 403, "{\"accepted\":false,\"message\":\"maker_header_required\"}");
                return;
            }

            byte[] requestBody = exchange.getRequestBody().readNBytes(1024 * 1024 + 1);
            if (requestBody.length > 1024 * 1024) {
                sendJson(exchange, 413, "{\"accepted\":false,\"message\":\"request_too_large\"}");
                return;
            }
            PendingEditorSave saveRequest;
            try {
                saveRequest = parseSaveRequest(requestBody);
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("[PhantasmBriefing] Rejected invalid editor save request: {}", exception.getMessage());
                sendJson(exchange, 400, "{\"accepted\":false,\"message\":\"invalid_save_request\"}");
                return;
            }
            MinecraftServer server = this.activeServer;
            if (server == null || !server.isRunning()) {
                sendJson(exchange, 503, "{\"accepted\":false,\"message\":\"game_server_not_running\"}");
                return;
            }

            server.execute(() -> requestReload(server, saveRequest));
            String mode = saveRequest.reloadRequired() ? "reload_requested" : "reconcile_requested";
            sendJson(exchange, 202, "{\"accepted\":true,\"message\":\"" + mode + "\"}");
        }
    }


    private void handleResourcesRequest(HttpExchange exchange) throws IOException {
        try (exchange) {
            Headers requestHeaders = exchange.getRequestHeaders();
            String origin = requestHeaders.getFirst("Origin");
            addCorsHeaders(exchange.getResponseHeaders(), origin);

            if (!isAllowedOrigin(origin)) {
                sendJson(exchange, 403, "{\"ok\":false,\"message\":\"origin_not_allowed\"}");
                return;
            }
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"ok\":false,\"message\":\"get_required\"}");
                return;
            }
            if (!"1".equals(requestHeaders.getFirst(MAKER_HEADER))) {
                sendJson(exchange, 403, "{\"ok\":false,\"message\":\"maker_header_required\"}");
                return;
            }

            MinecraftServer server = this.activeServer;
            if (server == null || !server.isRunning()) {
                sendJson(exchange, 503, "{\"ok\":false,\"message\":\"game_server_not_running\"}");
                return;
            }

            CompletableFuture<List<FTBIntegrationHelper.FtbQuestInfo>> future = new CompletableFuture<>();
            server.execute(() -> {
                try {
                    future.complete(FTBIntegrationHelper.listAvailableQuests());
                } catch (RuntimeException exception) {
                    future.completeExceptionally(exception);
                }
            });

            try {
                List<FTBIntegrationHelper.FtbQuestInfo> ftbQuests = future.get(2L, TimeUnit.SECONDS);
                JsonObject payload = new JsonObject();
                payload.addProperty("ok", true);
                payload.addProperty("source", "game");
                payload.addProperty("ftbLoaded", FTBIntegrationHelper.isFTBQuestsLoaded());
                payload.addProperty("ftbBridgeAvailable", FTBIntegrationHelper.isBridgeAvailable());
                JsonArray quests = new JsonArray();
                for (FTBIntegrationHelper.FtbQuestInfo quest : ftbQuests) {
                    JsonObject row = new JsonObject();
                    row.addProperty("id", quest.id());
                    row.addProperty("title", quest.title());
                    quests.add(row);
                }
                payload.add("ftbQuests", quests);
                JsonArray voices = new JsonArray();
                for (DialogueVoiceDefinition voice : DialogueVoiceDataManager.getInstance().getAllVoices()) {
                    JsonObject row = new JsonObject();
                    row.addProperty("id", voice.id());
                    row.addProperty("soundEventId", voice.soundEventId());
                    row.addProperty("nameZh", voice.displayNameZh());
                    row.addProperty("nameEn", voice.displayNameEn());
                    row.addProperty("defaultVolume", voice.defaultVolume());
                    row.addProperty("sortOrder", voice.sortOrder());
                    voices.add(row);
                }
                payload.add("dialogueVoices", voices);
                sendJson(exchange, 200, payload.toString());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                sendJson(exchange, 503, "{\"ok\":false,\"message\":\"resource_query_interrupted\"}");
            } catch (ExecutionException | TimeoutException exception) {
                sendJson(exchange, 503, "{\"ok\":false,\"message\":\"resource_query_failed\"}");
            }
        }
    }

    private void handlePreviewDialogueVoiceRequest(HttpExchange exchange) throws IOException {
        try (exchange) {
            Headers requestHeaders = exchange.getRequestHeaders();
            String origin = requestHeaders.getFirst("Origin");
            addCorsHeaders(exchange.getResponseHeaders(), origin);

            if (!isAllowedOrigin(origin)) {
                sendJson(exchange, 403, "{\"ok\":false,\"message\":\"origin_not_allowed\"}");
                return;
            }
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"ok\":false,\"message\":\"post_required\"}");
                return;
            }
            if (!"1".equals(requestHeaders.getFirst(MAKER_HEADER))) {
                sendJson(exchange, 403, "{\"ok\":false,\"message\":\"maker_header_required\"}");
                return;
            }

            MinecraftServer server = this.activeServer;
            if (server == null || !server.isRunning()) {
                sendJson(exchange, 503, "{\"ok\":false,\"message\":\"game_server_not_running\"}");
                return;
            }

            JsonObject request;
            try {
                byte[] body = exchange.getRequestBody().readNBytes(4096);
                request = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (RuntimeException exception) {
                sendJson(exchange, 400, "{\"ok\":false,\"message\":\"invalid_json\"}");
                return;
            }

            String voiceId = request.has("soundId") ? request.get("soundId").getAsString().trim() : "";
            DialogueVoiceDefinition definition = DialogueVoiceDataManager.getInstance().getVoice(voiceId).orElse(null);
            if (definition == null) {
                sendJson(exchange, 404, "{\"ok\":false,\"message\":\"unknown_voice\"}");
                return;
            }
            float multiplier = request.has("volume") ? request.get("volume").getAsFloat() : 1.0F;
            if (!Float.isFinite(multiplier)) {
                multiplier = 1.0F;
            }
            float previewVolume = Math.max(0.0F, Math.min(2.0F, multiplier)) * definition.defaultVolume();

            CompletableFuture<Boolean> future = new CompletableFuture<>();
            final float finalPreviewVolume = previewVolume;
            server.execute(() -> {
                ServerPlayer target = server.getPlayerList().getPlayers().stream()
                        .filter(player -> player.hasPermissions(2))
                        .findFirst()
                        .orElseGet(() -> server.getPlayerList().getPlayers().stream().findFirst().orElse(null));
                if (target == null) {
                    future.complete(false);
                    return;
                }
                ModNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> target),
                        new PreviewDialogueVoiceS2CPacket(definition.soundEventId(), finalPreviewVolume)
                );
                future.complete(true);
            });

            try {
                if (Boolean.TRUE.equals(future.get(2L, TimeUnit.SECONDS))) {
                    sendJson(exchange, 200, "{\"ok\":true}");
                } else {
                    sendJson(exchange, 503, "{\"ok\":false,\"message\":\"preview_unavailable\"}");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                sendJson(exchange, 503, "{\"ok\":false,\"message\":\"preview_interrupted\"}");
            } catch (ExecutionException | TimeoutException exception) {
                sendJson(exchange, 503, "{\"ok\":false,\"message\":\"preview_failed\"}");
            }
        }
    }

    private void requestReload(MinecraftServer server, PendingEditorSave saveRequest) {
        if (this.reloadInProgress) {
            this.queuedSave = this.queuedSave == null ? saveRequest : this.queuedSave.merge(saveRequest);
            return;
        }
        beginReload(server, saveRequest);
    }

    private void beginReload(MinecraftServer server, PendingEditorSave saveRequest) {
        this.reloadInProgress = true;
        EditorReloadReconciliationService.Snapshot beforeReload = EditorReloadReconciliationService.capture();
        Set<String> changedDomains = saveRequest.changedDomains();
        boolean reloadRequired = saveRequest.reloadRequired() && !changedDomains.isEmpty();
        boolean runOrphanSweep = saveRequest.manifest() != null
                && (this.lastAppliedManifest == null || !this.lastAppliedManifest.equals(saveRequest.manifest()));
        LOGGER.info(
                "[PhantasmBriefing] Phantasm Maker save requested {} for domains {}",
                reloadRequired ? "a selective hot reload" : "runtime reconciliation only",
                changedDomains
        );

        Executor preparationExecutor = this.bridgeExecutor == null ? Runnable::run : this.bridgeExecutor;
        CompletableFuture<Void> reloadFuture = reloadRequired
                ? PhantasmDataReloadService.reloadEditorDomains(server, preparationExecutor, changedDomains)
                : CompletableFuture.completedFuture(null);

        reloadFuture.whenComplete((ignored, throwable) -> server.execute(() -> {
            this.reloadInProgress = false;
            if (throwable != null) {
                LOGGER.error("[PhantasmBriefing] Editor hot reload failed", throwable);
                notifyOperators(server, Component.translatable("msg.phantasmbriefing.editor_reload_failed"));
            } else {
                EditorReloadReconciliationService.Result reconciliation =
                        EditorReloadReconciliationService.reconcile(
                                server,
                                beforeReload,
                                saveRequest.manifest(),
                                runOrphanSweep
                        );
                if (saveRequest.manifest() != null) {
                    this.lastAppliedManifest = saveRequest.manifest();
                }
                applyPostSaveRuntimeUpdates(server, changedDomains, reconciliation);

                if (reconciliation.hasRemovals() || reconciliation.purgedQuestStates() > 0
                        || reconciliation.closedDialogueSessions() > 0) {
                    LOGGER.info(
                            "[PhantasmBriefing] Editor deletion/orphan sync removed quests={}, dialogueNodes={}, npcBindings={}; purgedQuestStates={}, closedDialogueSessions={}, clearedStaleDirectNpcAssignments={}, discardedNpcEntities={}, removedNpcSpawnRecords={}, removedNpcCompletionRecords={}",
                            reconciliation.removedQuestIds().size(),
                            reconciliation.removedDialogueNodeIds().size(),
                            reconciliation.removedNpcBindingIds().size(),
                            reconciliation.purgedQuestStates(),
                            reconciliation.closedDialogueSessions(),
                            reconciliation.staleDirectQuestNpcAssignments(),
                            reconciliation.npcCleanup().discardedEntities(),
                            reconciliation.npcCleanup().removedSpawnRecords(),
                            reconciliation.npcCleanup().removedCompletionRecords()
                    );
                }

                int questCount = QuestDataManager.getInstance().getAllQuests().size();
                LOGGER.info(
                        "[PhantasmBriefing] Editor save processing completed (reloadedDomains={}, quests={})",
                        reloadRequired ? changedDomains : Set.of(),
                        questCount
                );
                if (reloadRequired || reconciliation.hasRemovals() || reconciliation.purgedQuestStates() > 0
                        || reconciliation.closedDialogueSessions() > 0) {
                    notifyOperators(
                            server,
                            Component.translatable(
                                    "msg.phantasmbriefing.editor_reload_success",
                                    Component.literal(Integer.toString(questCount)).withStyle(ChatFormatting.GREEN)
                            )
                    );
                }
            }

            if (this.queuedSave != null && this.activeServer == server && server.isRunning()) {
                PendingEditorSave next = this.queuedSave;
                this.queuedSave = null;
                beginReload(server, next);
            }
        }));
    }

    private static void applyPostSaveRuntimeUpdates(
            MinecraftServer server,
            Set<String> changedDomains,
            EditorReloadReconciliationService.Result reconciliation
    ) {
        boolean questChanged = changedDomains.contains(PhantasmDataReloadService.QUESTS)
                || !reconciliation.removedQuestIds().isEmpty()
                || reconciliation.purgedQuestStates() > 0;
        boolean manualChanged = changedDomains.contains(PhantasmDataReloadService.MANUALS);
        boolean dialogueChanged = changedDomains.contains(PhantasmDataReloadService.DIALOGUES)
                || !reconciliation.removedDialogueNodeIds().isEmpty()
                || reconciliation.closedDialogueSessions() > 0;
        boolean npcChanged = changedDomains.contains(PhantasmDataReloadService.NPCS)
                || !reconciliation.removedNpcBindingIds().isEmpty()
                || reconciliation.staleDirectQuestNpcAssignments() > 0
                || reconciliation.npcCleanup().discardedEntities() > 0
                || reconciliation.npcCleanup().removedSpawnRecords() > 0
                || reconciliation.npcCleanup().removedCompletionRecords() > 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (questChanged) {
                QuestRuntimeService.refreshFtbCompletionCache(player);
                QuestRuntimeService.ensureAutoStartedQuests(player);
                QuestTrackerService.normalizeTracking(player);
                QuestRuntimeService.invalidateAutomaticObjectives(player);
                QuestRuntimeService.evaluateAutomaticObjectives(player);
                QuestRuntimeService.evaluateFtbObjectivesOnce(player);
                QuestRuntimeService.reconcileActiveQuestFlow(player);
                QuestTrackerService.syncToClient(player);
                NpcStructureSpawnEvents.invalidatePlayer(player);
            }
            if (npcChanged) {
                NpcStructureSpawnService.reapplyNearbyGeneratedNpcBindings(player);
                NpcStructureSpawnEvents.invalidatePlayer(player);
            }
            if (dialogueChanged || npcChanged) {
                DialogueFlowCoordinator.validateActiveSession(player);
            }
            if (questChanged || dialogueChanged || npcChanged) {
                QuestEntityHintService.requestSync(player);
            }
            if (questChanged || manualChanged) {
                ModNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        RefreshQuestContentS2CPacket.forPlayer(player)
                );
            }
        }
    }

    private static PendingEditorSave parseSaveRequest(byte[] requestBody) {
        if (requestBody == null || requestBody.length == 0) {
            throw new IllegalArgumentException("Request body is empty");
        }
        JsonElement parsed = JsonParser.parseString(new String(requestBody, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Request body must be a JSON object");
        }
        JsonObject root = parsed.getAsJsonObject();
        if (!hasJsonArray(root, "changedKinds")) {
            throw new IllegalArgumentException("changedKinds is required by the current editor protocol");
        }
        if (!root.has("reloadRequired") || !root.get("reloadRequired").isJsonPrimitive()) {
            throw new IllegalArgumentException("reloadRequired is required by the current editor protocol");
        }
        EditorReloadReconciliationService.SaveManifest manifest = parseSaveManifest(root);
        Set<String> changedDomains = readSupportedDomains(root, "changedKinds");
        boolean reloadRequired = root.get("reloadRequired").getAsBoolean();
        return new PendingEditorSave(manifest, changedDomains, reloadRequired);
    }

    private static EditorReloadReconciliationService.SaveManifest parseSaveManifest(JsonObject root) {
        if (root == null || !root.has("manifest") || !root.get("manifest").isJsonObject()) {
            throw new IllegalArgumentException("manifest is required by the current editor protocol");
        }
        JsonObject manifest = root.getAsJsonObject("manifest");
        if (!hasJsonArray(manifest, "questIds")
                || !hasJsonArray(manifest, "dialogueNodeIds")
                || !hasJsonArray(manifest, "npcBindingIds")) {
            throw new IllegalArgumentException("manifest is incomplete");
        }
        return new EditorReloadReconciliationService.SaveManifest(
                readStringSet(manifest, "questIds"),
                readStringSet(manifest, "dialogueNodeIds"),
                readStringSet(manifest, "npcBindingIds")
        );
    }

    private static Set<String> readSupportedDomains(JsonObject owner, String key) {
        LinkedHashSet<String> domains = new LinkedHashSet<>();
        for (String value : readStringSet(owner, key)) {
            if (PhantasmDataReloadService.supportedEditorDomains().contains(value)) {
                domains.add(value);
            }
        }
        return Set.copyOf(domains);
    }

    private record PendingEditorSave(
            EditorReloadReconciliationService.SaveManifest manifest,
            Set<String> changedDomains,
            boolean reloadRequired
    ) {
        private PendingEditorSave {
            changedDomains = changedDomains == null ? Set.of() : Set.copyOf(changedDomains);
        }


        private PendingEditorSave merge(PendingEditorSave newer) {
            LinkedHashSet<String> mergedDomains = new LinkedHashSet<>(this.changedDomains);
            mergedDomains.addAll(newer.changedDomains);
            return new PendingEditorSave(
                    newer.manifest != null ? newer.manifest : this.manifest,
                    Set.copyOf(mergedDomains),
                    this.reloadRequired || newer.reloadRequired
            );
        }
    }

    private static boolean hasJsonArray(JsonObject owner, String key) {
        return owner != null && owner.has(key) && owner.get(key).isJsonArray();
    }

    private static Set<String> readStringSet(JsonObject owner, String key) {
        if (owner == null || !owner.has(key) || !owner.get(key).isJsonArray()) {
            return Set.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (var element : owner.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            String value = element.getAsString().trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return Set.copyOf(values);
    }

    private static boolean isAllowedOrigin(String origin) {
        if (origin == null || origin.equals("null")) {
            return true;
        }
        try {
            URI uri = URI.create(origin);
            String host = uri.getHost();
            return "http".equalsIgnoreCase(uri.getScheme())
                    && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static void addCorsHeaders(Headers headers, String origin) {
        headers.set("Access-Control-Allow-Origin", origin == null ? "*" : origin);
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, " + MAKER_HEADER);
        headers.set("Access-Control-Allow-Private-Network", "true");
        headers.set("Cache-Control", "no-store");
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
    }

    private static void notifyOperators(MinecraftServer server, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) {
                player.sendSystemMessage(message);
            }
        }
    }
}
