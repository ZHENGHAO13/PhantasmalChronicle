package com.phantasm.briefing.data.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.phantasm.briefing.PhantasmBriefing;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * One-way migration for editor-managed content. Runtime managers consume only the current model;
 * every recognized old shape is rewritten on disk before a reload reaches those managers.
 */
public final class ContentMigrationService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private static final String QUESTS = "quests";
    private static final String MANUALS = "manuals";
    private static final String DIALOGUES = "dialogues";
    private static final String NPCS = "npcs";
    private static final String SHOPS = "shops";
    private static final String TRADES = "trades";
    private static final String WALLETS = "wallets";

    private ContentMigrationService() {
    }

    public static synchronized Set<String> migrateExternalPack() {
        Path modConfig = FMLPaths.CONFIGDIR.get().resolve(PhantasmBriefing.MOD_ID);
        return migrateExternalPack(modConfig);
    }

    /** Visible for regression tests without booting Minecraft. */
    public static synchronized Set<String> migrateExternalPack(Path modConfigDirectory) {
        Path packRoot = modConfigDirectory.resolve("briefing_pack");
        if (!Files.isDirectory(packRoot)) {
            return Set.of();
        }

        try {
            MigrationPlan plan = buildPlan(modConfigDirectory, packRoot);
            if (plan.rewrites.isEmpty() && plan.copies.isEmpty() && plan.legacyImageDirectory == null) {
                return Set.of();
            }
            applyPlan(modConfigDirectory, packRoot, plan);
            LOGGER.info(
                    "[PhantasmBriefing] Migrated old editor content into the current schema (rewritten={}, copied={}, removedOldImageDirectory={}, domains={})",
                    plan.rewrites.size(), plan.copies.size(), plan.legacyImageDirectory != null, plan.changedDomains
            );
            return Set.copyOf(plan.changedDomains);
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("[PhantasmBriefing] Content migration failed; old files were left untouched: {}", exception.getMessage(), exception);
            return Set.of();
        }
    }

    private static MigrationPlan buildPlan(Path modConfig, Path packRoot) throws IOException {
        Map<Path, JsonObject> allJson = loadPackJson(packRoot);
        Map<Path, JsonObject> working = new LinkedHashMap<>();
        allJson.forEach((path, json) -> working.put(path, json.deepCopy()));
        Set<String> changedDomains = new LinkedHashSet<>();
        Map<Path, byte[]> generatedFiles = new LinkedHashMap<>();

        Path dialogueDir = packRoot.resolve("phantasm_dialogues");
        Map<String, String> oldNodeToScoped = migrateDialogues(working, dialogueDir);

        Path manualDir = packRoot.resolve("phantasm_manuals");
        Set<String> manualIds = collectManualIds(working, manualDir);
        migrateManualFiles(working, manualDir);
        migrateQuestFiles(working, packRoot.resolve("phantasm_quests"), manualDir, manualIds, generatedFiles);
        migrateNpcFiles(working, packRoot.resolve("phantasm_npc_bindings"));
        migrateWalletFiles(working, packRoot.resolve("phantasm_wallet"));
        migrateShopFiles(working, packRoot.resolve("phantasm_shops"));
        migrateTradeFiles(working, packRoot.resolve("phantasm_trades"));

        if (!oldNodeToScoped.isEmpty()) {
            for (Map.Entry<Path, JsonObject> entry : working.entrySet()) {
                rewriteNodeReferences(entry.getValue(), oldNodeToScoped);
            }
            for (Map.Entry<Path, byte[]> generated : generatedFiles.entrySet()) {
                JsonElement parsed = JsonParser.parseString(new String(generated.getValue(), StandardCharsets.UTF_8));
                if (parsed.isJsonObject()) {
                    rewriteNodeReferences(parsed.getAsJsonObject(), oldNodeToScoped);
                    generated.setValue(bytes(parsed.getAsJsonObject()));
                }
            }
        }

        Map<Path, byte[]> rewrites = new LinkedHashMap<>();
        for (Map.Entry<Path, JsonObject> entry : working.entrySet()) {
            JsonObject original = allJson.get(entry.getKey());
            if (!sameJson(original, entry.getValue())) {
                rewrites.put(entry.getKey(), bytes(entry.getValue()));
                String domain = domainFor(packRoot, entry.getKey());
                if (!domain.isBlank()) changedDomains.add(domain);
            }
        }
        for (Map.Entry<Path, byte[]> generated : generatedFiles.entrySet()) {
            boolean generatedChanged = false;
            if (!Files.exists(generated.getKey())) {
                rewrites.put(generated.getKey(), generated.getValue());
                generatedChanged = true;
            } else {
                byte[] old = Files.readAllBytes(generated.getKey());
                if (!java.util.Arrays.equals(old, generated.getValue())) {
                    rewrites.put(generated.getKey(), generated.getValue());
                    generatedChanged = true;
                }
            }
            if (generatedChanged) changedDomains.add(MANUALS);
        }

        ImageMigrationPlan imageMigration = planImageMigration(modConfig);
        if (imageMigration.legacyDirectory() != null) {
            changedDomains.add(MANUALS);
        }
        return new MigrationPlan(rewrites, imageMigration.copies(), changedDomains, imageMigration.legacyDirectory());
    }

    private static Map<Path, JsonObject> loadPackJson(Path packRoot) throws IOException {
        Map<Path, JsonObject> result = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(packRoot)) {
            for (Path file : paths.filter(Files::isRegularFile).filter(ContentMigrationService::isJson).sorted().toList()) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonElement element = JsonParser.parseReader(reader);
                    if (element.isJsonObject()) {
                        result.put(file, element.getAsJsonObject());
                    }
                } catch (RuntimeException exception) {
                    // Invalid user JSON must remain untouched; the normal loader/validator will report it.
                    LOGGER.warn("[PhantasmBriefing] Skipping migration for unreadable JSON {}: {}", file, exception.getMessage());
                }
            }
        }
        return result;
    }

    private static Map<String, String> migrateDialogues(
            Map<Path, JsonObject> files,
            Path dialogueDir
    ) {
        Map<String, String> oldNodeToScoped = new LinkedHashMap<>();
        Set<String> usedDialogueIds = new HashSet<>();
        for (Map.Entry<Path, JsonObject> entry : files.entrySet()) {
            if (!entry.getKey().startsWith(dialogueDir)) continue;
            JsonObject json = entry.getValue();
            if (json.has("nodes") && json.get("nodes").isJsonArray()) {
                String id = firstString(json, "id", "dialogueId");
                if (!id.isBlank()) usedDialogueIds.add(id);
            }
        }

        for (Map.Entry<Path, JsonObject> entry : files.entrySet()) {
            Path file = entry.getKey();
            if (!file.startsWith(dialogueDir)) continue;
            JsonObject json = entry.getValue();
            if (json.has("nodes") && json.get("nodes").isJsonArray()) {
                normalizeCurrentDialogue(json);
                continue;
            }

            String oldNodeId = firstString(json, "nodeId", "id");
            if (oldNodeId.isBlank()) oldNodeId = stripExtension(file.getFileName().toString());
            String dialogueId = uniqueDialogueId(oldNodeId, usedDialogueIds);
            usedDialogueIds.add(dialogueId);
            oldNodeToScoped.put(oldNodeId, dialogueId + "::root");

            JsonObject node = migrateStandaloneNode(json, "root");
            JsonObject current = new JsonObject();
            current.addProperty("id", dialogueId);
            current.addProperty("defaultNpc", firstText(json, "title", "name", "npcName", "defaultNpc", oldNodeId));
            current.addProperty("startNodeId", "root");
            JsonArray entries = new JsonArray();
            entries.add("root");
            current.add("entryNodeIds", entries);
            JsonArray nodes = new JsonArray();
            nodes.add(node);
            current.add("nodes", nodes);
            replaceObject(json, current);
        }
        return oldNodeToScoped;
    }

    private static void normalizeCurrentDialogue(JsonObject dialogue) {
        if (!dialogue.has("id") && dialogue.has("dialogueId")) {
            dialogue.add("id", dialogue.remove("dialogueId"));
        }
        copyTextField(dialogue, "defaultNpc", "npcName", "title");
        if (!dialogue.has("entryNodeIds") || !dialogue.get("entryNodeIds").isJsonArray()) {
            JsonArray entries = new JsonArray();
            entries.add(firstString(dialogue, "startNodeId").isBlank() ? "root" : firstString(dialogue, "startNodeId"));
            dialogue.add("entryNodeIds", entries);
        }
        if (dialogue.has("nodes") && dialogue.get("nodes").isJsonArray()) {
            for (JsonElement element : dialogue.getAsJsonArray("nodes")) {
                if (element.isJsonObject()) normalizeDialogueNode(element.getAsJsonObject());
            }
        }
    }

    private static JsonObject migrateStandaloneNode(JsonObject old, String localNodeId) {
        JsonObject node = old.deepCopy();
        node.remove("id");
        node.remove("nodeId");
        node.addProperty("nodeId", localNodeId);
        normalizeDialogueNode(node);
        String oldFtb = firstString(old, "ftbQuestIdToComplete");
        if (!oldFtb.isBlank()) {
            JsonArray completion = node.has("completionActions") && node.get("completionActions").isJsonArray()
                    ? node.getAsJsonArray("completionActions") : new JsonArray();
            JsonObject action = new JsonObject();
            action.addProperty("type", "complete_ftb_quest");
            action.addProperty("questId", oldFtb);
            completion.add(action);
            node.add("completionActions", completion);
        }
        node.remove("ftbQuestIdToComplete");
        return node;
    }

    private static void normalizeDialogueNode(JsonObject node) {
        if (!node.has("nodeId") && node.has("id")) node.add("nodeId", node.remove("id"));
        copyTextField(node, "title", "name");
        normalizeDialogueContents(node);
        convertRequiredQuestToCondition(node);
        if (node.has("options") && !node.has("choices")) node.add("choices", node.remove("options"));
        if (node.has("choices") && node.get("choices").isJsonArray()) {
            int index = 1;
            for (JsonElement element : node.getAsJsonArray("choices")) {
                if (!element.isJsonObject()) continue;
                JsonObject choice = element.getAsJsonObject();
                if (!choice.has("optionId")) {
                    String id = firstString(choice, "choiceId", "id");
                    choice.addProperty("optionId", id.isBlank() ? "choice_" + index : id);
                }
                choice.remove("choiceId");
                choice.remove("id");
                copyTextField(choice, "label", "text", "title", "name");
                convertRequiredQuestToCondition(choice);
                normalizeActions(choice, "actions");
                index++;
            }
        }
        normalizeActions(node, "completionActions");
        normalizeConditions(node);
    }

    private static void normalizeDialogueContents(JsonObject node) {
        JsonArray contents = new JsonArray();
        if (node.has("contents") && node.get("contents").isJsonArray()) {
            for (JsonElement element : node.getAsJsonArray("contents")) {
                if (element.isJsonPrimitive()) {
                    String text = textValue(element);
                    if (!text.isBlank()) {
                        JsonObject content = new JsonObject();
                        content.addProperty("type", "text");
                        content.addProperty("text", text);
                        contents.add(content);
                    }
                    continue;
                }
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("Unsupported dialogue content entry");
                }
                JsonObject old = element.getAsJsonObject();
                String type = firstString(old, "type").toLowerCase(Locale.ROOT);
                if ("image".equals(type)) {
                    String image = firstNonBlank(firstString(old, "image"), firstString(old, "value"));
                    if (image.isBlank()) throw new IllegalArgumentException("Dialogue image content has no image path");
                    JsonObject content = new JsonObject();
                    content.addProperty("type", "image");
                    content.addProperty("image", image);
                    contents.add(content);
                } else {
                    String text = firstNonBlank(textValue(old.get("text")), textValue(old.get("value")));
                    if (text.isBlank()) throw new IllegalArgumentException("Dialogue text content is blank");
                    JsonObject content = new JsonObject();
                    content.addProperty("type", "text");
                    content.addProperty("text", text);
                    contents.add(content);
                }
            }
        } else if (node.has("lines") && node.get("lines").isJsonArray()) {
            for (JsonElement line : node.getAsJsonArray("lines")) {
                String text = textValue(line);
                if (!text.isBlank()) {
                    JsonObject content = new JsonObject();
                    content.addProperty("type", "text");
                    content.addProperty("text", text);
                    contents.add(content);
                }
            }
        } else if (node.has("text")) {
            String text = textValue(node.get("text"));
            if (!text.isBlank()) {
                JsonObject content = new JsonObject();
                content.addProperty("type", "text");
                content.addProperty("text", text);
                contents.add(content);
            }
        }
        if (!contents.isEmpty()) node.add("contents", contents);
        node.remove("lines");
        node.remove("text");
    }

    private static void migrateManualFiles(Map<Path, JsonObject> files, Path manualDir) {
        for (Map.Entry<Path, JsonObject> entry : files.entrySet()) {
            if (!entry.getKey().startsWith(manualDir)) continue;
            JsonObject manual = entry.getValue();
            if (!manual.has("manualId") && manual.has("id")) manual.add("manualId", manual.remove("id"));
            if (firstString(manual, "manualId").isBlank()) {
                manual.addProperty("manualId", "phantasmbriefing:" + slug(stripExtension(entry.getKey().getFileName().toString())));
            }
            copyTextField(manual, "title", "name");
            if (textValue(manual.get("title")).isBlank()) manual.addProperty("title", firstString(manual, "manualId"));
            copyTextField(manual, "description");
            if (!manual.has("coverImage") && manual.has("cover")) manual.add("coverImage", manual.remove("cover"));
            if ((!manual.has("sections") || !manual.get("sections").isJsonArray())
                    && manual.has("pages") && manual.get("pages").isJsonArray()) {
                JsonObject lesson = new JsonObject();
                lesson.addProperty("type", "lesson");
                lesson.addProperty("nodeId", "lesson_main");
                lesson.addProperty("title", firstText(manual, "title", "manualId", "lesson_main"));
                lesson.add("unlock", alwaysUnlock());
                lesson.add("pages", normalizePages(manual.getAsJsonArray("pages")));
                JsonArray sections = new JsonArray();
                sections.add(lesson);
                manual.add("sections", sections);
                manual.remove("pages");
            }
            if (manual.has("sections") && manual.get("sections").isJsonArray()) {
                normalizeManualNodes(manual.getAsJsonArray("sections"));
            }
        }
    }

    private static void normalizeManualNodes(JsonArray nodes) {
        for (JsonElement element : nodes) {
            if (!element.isJsonObject()) continue;
            JsonObject node = element.getAsJsonObject();
            if (!node.has("nodeId") && node.has("id")) node.add("nodeId", node.remove("id"));
            copyTextField(node, "title", "name");
            if (textValue(node.get("title")).isBlank() && !firstString(node, "nodeId").isBlank()) {
                node.addProperty("title", firstString(node, "nodeId"));
            }
            if ("group".equals(firstString(node, "type"))) {
                if (node.has("children") && node.get("children").isJsonArray()) normalizeManualNodes(node.getAsJsonArray("children"));
                continue;
            }
            if (!node.has("type")) node.addProperty("type", "lesson");
            if (node.has("visibility") && node.get("visibility").isJsonObject() && !node.has("unlock")) {
                JsonObject visibility = node.getAsJsonObject("visibility");
                String questId = firstString(visibility, "questId");
                String phaseId = firstString(visibility, "phaseId");
                JsonObject unlock = new JsonObject();
                if (!questId.isBlank() && !phaseId.isBlank()) {
                    unlock.addProperty("type", "phase_completed");
                    unlock.addProperty("questId", questId);
                    unlock.addProperty("phaseId", phaseId);
                } else {
                    unlock.addProperty("type", "always");
                }
                node.add("unlock", unlock);
            }
            node.remove("visibility");
            if (!node.has("unlock")) node.add("unlock", alwaysUnlock());
            if (node.has("pages") && node.get("pages").isJsonArray()) node.add("pages", normalizePages(node.getAsJsonArray("pages")));
        }
    }

    private static JsonArray normalizePages(JsonArray pages) {
        JsonArray result = new JsonArray();
        int index = 1;
        for (JsonElement element : pages) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Manual page must be an object");
            }
            JsonObject page = element.getAsJsonObject().deepCopy();
            if (!page.has("pageId") || firstString(page, "pageId").isBlank()) page.addProperty("pageId", "page_" + index);
            copyTextField(page, "title", "name");
            copyTextField(page, "text", "description");
            String image = firstString(page, "image");
            if (!image.isBlank()) page.addProperty("image", image);
            boolean hasContent = !textValue(page.get("title")).isBlank()
                    || !textValue(page.get("text")).isBlank()
                    || !image.isBlank();
            if (!hasContent) {
                throw new IllegalArgumentException("Manual page has no title, text, or image");
            }
            result.add(page);
            index++;
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Manual lesson has no valid pages");
        return result;
    }

    private static void migrateQuestFiles(
            Map<Path, JsonObject> files,
            Path questDir,
            Path manualDir,
            Set<String> manualIds,
            Map<Path, byte[]> generatedFiles
    ) {
        for (Map.Entry<Path, JsonObject> entry : files.entrySet()) {
            if (!entry.getKey().startsWith(questDir)) continue;
            JsonObject quest = entry.getValue();
            if (!quest.has("questId") && quest.has("id")) quest.add("questId", quest.remove("id"));
            copyTextField(quest, "title", "name");
            if (textValue(quest.get("title")).isBlank() && !firstString(quest, "questId").isBlank()) {
                quest.addProperty("title", firstString(quest, "questId"));
            }
            copyTextField(quest, "description", "desc");
            copyTextField(quest, "trackerTitle");
            normalizeTextArray(quest, "objectiveLines");
            migrateManualRefs(quest);
            migrateQuestPrerequisites(quest);
            migrateAcceptFlags(quest);
            normalizeMode(quest, "phaseMode", "phaseOrder");
            normalizeConditionArray(quest, "unlockConditions");
            normalizeActions(quest, "acceptActions");
            normalizeActions(quest, "completeActions");
            normalizeMarker(quest, "marker");
            // Manual quest-tree coordinates were a retired presentation format. Current runtime is auto-layout only.
            quest.remove("treeLayout");

            if (!quest.has("phases") || !quest.get("phases").isJsonArray() || quest.getAsJsonArray("phases").isEmpty()) {
                JsonObject phase = new JsonObject();
                phase.addProperty("phaseId", "phase_1");
                phase.addProperty("title", firstText(quest, "phaseTitle", "title", ""));
                normalizeMode(quest, "objectiveMode", "objectiveOrder");
                phase.addProperty("objectiveMode", firstString(quest, "objectiveMode"));
                JsonArray migratedObjectives = quest.has("objectives") && quest.get("objectives").isJsonArray()
                        ? quest.getAsJsonArray("objectives").deepCopy() : new JsonArray();
                phase.add("objectives", migratedObjectives);
                if (quest.has("objectiveLines")) phase.add("objectiveLines", quest.get("objectiveLines").deepCopy());
                if (quest.has("targetNodeIds")) phase.add("targetNodeIds", quest.get("targetNodeIds").deepCopy());
                if (quest.has("hintType")) phase.add("hintType", quest.get("hintType").deepCopy());
                if (quest.has("completeQuestOnObjectivesCompleted")) {
                    phase.add("completeQuestOnObjectivesCompleted", quest.get("completeQuestOnObjectivesCompleted").deepCopy());
                }
                JsonArray phases = new JsonArray();
                phases.add(phase);
                quest.add("phases", phases);
                quest.addProperty("initialPhaseId", "phase_1");
                quest.remove("phaseTitle");
                quest.remove("objectives");
                quest.remove("targetNodeIds");
                quest.remove("hintType");
                quest.remove("completeQuestOnObjectivesCompleted");
                quest.remove("objectiveMode");
                quest.remove("objectiveOrder");
            }

            JsonArray phases = quest.getAsJsonArray("phases");
            for (int phaseIndex = 0; phaseIndex < phases.size(); phaseIndex++) {
                if (!phases.get(phaseIndex).isJsonObject()) continue;
                JsonObject phase = phases.get(phaseIndex).getAsJsonObject();
                if (!phase.has("phaseId") && phase.has("id")) phase.add("phaseId", phase.remove("id"));
                if (firstString(phase, "phaseId").isBlank()) phase.addProperty("phaseId", "phase_" + (phaseIndex + 1));
                copyTextField(phase, "title", "name");
                normalizeTextArray(phase, "objectiveLines");
                normalizeMode(phase, "objectiveMode", "objectiveOrder");
                normalizeActions(phase, "actions");
                normalizeActions(phase, "completionActions");
                normalizeConditions(phase);
                if (!phase.has("objectives") || !phase.get("objectives").isJsonArray()) phase.add("objectives", new JsonArray());

                JsonArray objectives = phase.getAsJsonArray("objectives");
                for (int objectiveIndex = 0; objectiveIndex < objectives.size(); objectiveIndex++) {
                    if (!objectives.get(objectiveIndex).isJsonObject()) continue;
                    JsonObject objective = objectives.get(objectiveIndex).getAsJsonObject();
                    if (!objective.has("objectiveId") && objective.has("id")) objective.add("objectiveId", objective.remove("id"));
                    if (firstString(objective, "objectiveId").isBlank()) objective.addProperty("objectiveId", "objective_" + (objectiveIndex + 1));
                    copyTextField(objective, "title", "name");
                    normalizeTextArray(objective, "objectiveLines");
                    if (!objective.has("requiredCount") && objective.has("count")) objective.add("requiredCount", objective.remove("count"));
                    String type = canonicalObjectiveType(firstString(objective, "type"));
                    objective.addProperty("type", type);
                    migrateManualRefs(objective);
                    normalizeConditions(objective);
                    normalizeActions(objective, "actions");
                    normalizeMarker(objective, "marker");

                    if ("read_manual".equals(type)) {
                        ensureReadManualReference(
                                quest,
                                phase,
                                objective,
                                manualDir,
                                manualIds,
                                generatedFiles
                        );
                    }
                    if (objective.has("tutorialPages")) {
                        if (!objective.has("manualRefs") || !objective.get("manualRefs").isJsonArray()
                                || objective.getAsJsonArray("manualRefs").isEmpty()) {
                            throw new IllegalArgumentException("Old tutorial objective could not be migrated without losing its pages");
                        }
                        objective.remove("tutorialPages");
                    }
                }
            }
        }
    }

    private static void ensureReadManualReference(
            JsonObject quest,
            JsonObject phase,
            JsonObject objective,
            Path manualDir,
            Set<String> manualIds,
            Map<Path, byte[]> generatedFiles
    ) {
        if (objective.has("manualRefs") && objective.get("manualRefs").isJsonArray()
                && !objective.getAsJsonArray("manualRefs").isEmpty()) {
            return;
        }
        if (!objective.has("tutorialPages") || !objective.get("tutorialPages").isJsonArray()
                || objective.getAsJsonArray("tutorialPages").isEmpty()) {
            return;
        }

        String questId = firstString(quest, "questId");
        String phaseId = firstString(phase, "phaseId");
        String objectiveId = firstString(objective, "objectiveId");
        String stem = slug(localPart(questId) + "_" + phaseId + "_" + objectiveId);
        String candidate = "phantasmbriefing:" + stem;
        int suffix = 2;
        while (manualIds.contains(candidate)) candidate = "phantasmbriefing:" + stem + "_" + suffix++;
        manualIds.add(candidate);

        JsonObject manual = new JsonObject();
        manual.addProperty("manualId", candidate);
        manual.addProperty("title", firstText(objective, "title", firstText(quest, "title", objectiveId)));
        manual.addProperty("description", "");
        manual.addProperty("category", "");
        manual.addProperty("coverImage", "");
        manual.addProperty("sortOrder", 0);
        JsonObject lesson = new JsonObject();
        lesson.addProperty("type", "lesson");
        lesson.addProperty("nodeId", "lesson_main");
        lesson.addProperty("title", firstText(objective, "title", firstText(quest, "title", objectiveId)));
        lesson.add("unlock", alwaysUnlock());
        lesson.add("pages", normalizePages(objective.getAsJsonArray("tutorialPages")));
        JsonArray sections = new JsonArray();
        sections.add(lesson);
        manual.add("sections", sections);

        Path file = manualDir.resolve(slug(localPart(candidate)) + ".json");
        generatedFiles.put(file, bytes(manual));
        JsonArray refs = new JsonArray();
        JsonObject ref = new JsonObject();
        ref.addProperty("manualId", candidate);
        ref.addProperty("lessonId", "lesson_main");
        ref.addProperty("pageId", "");
        refs.add(ref);
        objective.add("manualRefs", refs);
    }

    private static void migrateNpcFiles(Map<Path, JsonObject> files, Path npcDir) {
        for (Map.Entry<Path, JsonObject> entry : files.entrySet()) {
            if (!entry.getKey().startsWith(npcDir)) continue;
            JsonObject npc = entry.getValue();
            if (!npc.has("bindingId") && npc.has("id")) npc.add("bindingId", npc.remove("id"));
            if (!npc.has("questIds")) {
                JsonArray ids = new JsonArray();
                String old = firstString(npc, "questId");
                if (!old.isBlank()) ids.add(old);
                npc.add("questIds", ids);
            }
            npc.remove("questId");
            if (npc.has("spawn") && npc.get("spawn").isJsonObject() && !npc.has("spawns")) {
                JsonArray spawns = new JsonArray();
                spawns.add(npc.get("spawn").deepCopy());
                npc.add("spawns", spawns);
            }
            npc.remove("spawn");
            if (npc.has("spawns") && npc.get("spawns").isJsonArray()) {
                int index = 0;
                for (JsonElement e : npc.getAsJsonArray("spawns")) {
                    if (!e.isJsonObject()) continue;
                    JsonObject spawn = e.getAsJsonObject();
                    if (spawn.has("offset") && spawn.get("offset").isJsonArray()) {
                        JsonArray offset = spawn.getAsJsonArray("offset");
                        if (offset.size() > 0) spawn.add("offsetX", offset.get(0).deepCopy());
                        if (offset.size() > 1) spawn.add("offsetY", offset.get(1).deepCopy());
                        if (offset.size() > 2) spawn.add("offsetZ", offset.get(2).deepCopy());
                        spawn.remove("offset");
                    }
                    if (!spawn.has("ruleId")) spawn.addProperty("ruleId", firstString(npc, "bindingId") + "/spawn_" + index);
                    normalizeConditions(spawn);
                    index++;
                }
            }
        }
    }


    private static void normalizeMarker(JsonObject owner, String key) {
        if (!owner.has(key) || !owner.get(key).isJsonObject()) return;
        JsonObject marker = owner.getAsJsonObject(key);
        copyTextField(marker, "label", "title", "name");
    }

    private static void migrateWalletFiles(Map<Path, JsonObject> files, Path walletDir) {
        for (Map.Entry<Path, JsonObject> entry : files.entrySet()) {
            if (!entry.getKey().startsWith(walletDir)) continue;
            JsonObject wallet = entry.getValue();
            if (!wallet.has("walletId")) {
                String id = firstNonBlank(firstString(wallet, "currencyId"), firstString(wallet, "id"));
                if (!id.isBlank()) wallet.addProperty("walletId", id);
            }
            String icon = firstNonBlank(firstString(wallet, "iconItemId"), firstString(wallet, "itemId"));
            if (!icon.isBlank()) wallet.addProperty("iconItemId", icon);
            if (!wallet.has("itemIds") || !wallet.get("itemIds").isJsonArray()) {
                JsonArray itemIds = new JsonArray();
                if (!icon.isBlank()) itemIds.add(icon);
                wallet.add("itemIds", itemIds);
            }
            copyTextField(wallet, "title", "displayName", "name");
            if (firstString(wallet, "currencyName").isBlank()) {
                String title = firstText(wallet, "title", firstString(wallet, "walletId"));
                if (!title.isBlank()) wallet.addProperty("currencyName", title);
            }
            wallet.remove("currencyId");
            wallet.remove("itemId");
            wallet.remove("id");
            wallet.remove("displayName");
            wallet.remove("name");
        }
    }

    private static void migrateShopFiles(Map<Path, JsonObject> files, Path shopDir) {
        for (Map.Entry<Path, JsonObject> entry : files.entrySet()) {
            if (!entry.getKey().startsWith(shopDir)) continue;
            JsonObject shop = entry.getValue();
            if (!shop.has("shopId") && shop.has("id")) shop.add("shopId", shop.remove("id"));
            copyTextField(shop, "title", "displayName", "name");
            normalizeSingleCondition(shop, "openCondition");
            normalizeCategories(shop);
            if (shop.has("offers") && shop.get("offers").isJsonArray()) {
                int index = 1;
                for (JsonElement element : shop.getAsJsonArray("offers")) {
                    if (!element.isJsonObject()) continue;
                    JsonObject offer = element.getAsJsonObject();
                    if (!offer.has("offerId") && offer.has("id")) offer.add("offerId", offer.remove("id"));
                    if (firstString(offer, "offerId").isBlank()) offer.addProperty("offerId", "offer_" + index);
                    copyTextField(offer, "displayName", "title", "name");
                    copyTextField(offer, "description");
                    if (!offer.has("category") && offer.has("categoryId")) offer.add("category", offer.remove("categoryId"));
                    if (offer.has("costA") && offer.get("costA").isJsonObject()) normalizeItemObject(offer.getAsJsonObject("costA"));
                    if (offer.has("costB") && offer.get("costB").isJsonObject()) normalizeItemObject(offer.getAsJsonObject("costB"));
                    if (offer.has("result") && offer.get("result").isJsonObject()) {
                        normalizeItemObject(offer.getAsJsonObject("result"));
                    } else {
                        String resultId = firstNonBlank(firstString(offer, "resultItemId"), firstString(offer, "itemId"));
                        if (!resultId.isBlank()) {
                            JsonObject result = new JsonObject();
                            result.addProperty("itemId", resultId);
                            result.addProperty("count", Math.max(1, intValue(offer, "resultCount", 1)));
                            offer.add("result", result);
                        }
                    }
                    offer.remove("resultItemId");
                    offer.remove("resultCount");
                    offer.remove("itemId");
                    normalizeSingleCondition(offer, "visibleCondition");
                    index++;
                }
            }
        }
    }

    private static void migrateTradeFiles(Map<Path, JsonObject> files, Path tradeDir) {
        for (Map.Entry<Path, JsonObject> entry : files.entrySet()) {
            if (!entry.getKey().startsWith(tradeDir)) continue;
            JsonObject trade = entry.getValue();
            if (!trade.has("tradeId")) {
                String id = firstNonBlank(firstString(trade, "shopId"), firstString(trade, "id"));
                if (!id.isBlank()) trade.addProperty("tradeId", id);
            }
            copyTextField(trade, "displayName", "title", "name");
            copyTextField(trade, "description");
            trade.remove("shopId");
            trade.remove("id");
            normalizeSingleCondition(trade, "openCondition");
            normalizeCategories(trade);

            if (trade.has("entries") && trade.get("entries").isJsonArray()) {
                JsonObject objectEntries = new JsonObject();
                int index = 1;
                for (JsonElement element : trade.getAsJsonArray("entries")) {
                    if (!element.isJsonObject()) continue;
                    JsonObject tradeEntry = element.getAsJsonObject().deepCopy();
                    String id = firstNonBlank(firstString(tradeEntry, "entryId"), firstString(tradeEntry, "id"));
                    if (id.isBlank()) id = "entry_" + index;
                    tradeEntry.addProperty("entryId", id);
                    tradeEntry.remove("id");
                    normalizeTradeEntry(tradeEntry);
                    objectEntries.add(id, tradeEntry);
                    index++;
                }
                trade.add("entries", objectEntries);
            } else if (trade.has("entries") && trade.get("entries").isJsonObject()) {
                JsonObject entries = trade.getAsJsonObject("entries");
                for (Map.Entry<String, JsonElement> child : new ArrayList<>(entries.entrySet())) {
                    if (!child.getValue().isJsonObject()) continue;
                    JsonObject tradeEntry = child.getValue().getAsJsonObject();
                    if (firstString(tradeEntry, "entryId").isBlank()) tradeEntry.addProperty("entryId", child.getKey());
                    tradeEntry.remove("id");
                    normalizeTradeEntry(tradeEntry);
                }
            }
        }
    }

    private static void normalizeTradeEntry(JsonObject entry) {
        copyTextField(entry, "displayName", "title", "name");
        if (!entry.has("category") && entry.has("categoryId")) entry.add("category", entry.remove("categoryId"));
        normalizeItemArray(entry, "costs");
        normalizeItemArray(entry, "rewards");
        normalizeSingleCondition(entry, "visibleCondition");
    }

    private static void normalizeCategories(JsonObject owner) {
        if (!owner.has("categories") || !owner.get("categories").isJsonArray()) return;
        int index = 1;
        for (JsonElement element : owner.getAsJsonArray("categories")) {
            if (!element.isJsonObject()) continue;
            JsonObject category = element.getAsJsonObject();
            if (!category.has("categoryId") && category.has("id")) category.add("categoryId", category.remove("id"));
            if (firstString(category, "categoryId").isBlank()) category.addProperty("categoryId", "category_" + index);
            copyTextField(category, "displayName", "title", "name");
            index++;
        }
    }

    private static void normalizeItemArray(JsonObject owner, String key) {
        if (!owner.has(key) || !owner.get(key).isJsonArray()) return;
        for (JsonElement element : owner.getAsJsonArray(key)) {
            if (element.isJsonObject()) normalizeItemObject(element.getAsJsonObject());
        }
    }

    private static void normalizeItemObject(JsonObject item) {
        if (!item.has("itemId") && item.has("item")) item.add("itemId", item.remove("item"));
        if (!item.has("count")) item.addProperty("count", 1);
        if (item.has("type")) item.addProperty("type", "item");
    }

    private static int intValue(JsonObject owner, String key, int fallback) {
        if (!owner.has(key) || !owner.get(key).isJsonPrimitive()) return fallback;
        try { return owner.get(key).getAsInt(); } catch (RuntimeException ignored) { return fallback; }
    }

    private static void rewriteNodeReferences(JsonElement element, Map<String, String> mapping) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) rewriteNodeReferences(child, mapping);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();

        rewriteStringProperty(object, "questNodeId", mapping);
        rewriteStringProperty(object, "structureNodeId", mapping);
        rewriteStringProperty(object, "nextNodeId", mapping);
        if (object.has("targetNodeIds") && object.get("targetNodeIds").isJsonArray()) {
            JsonArray source = object.getAsJsonArray("targetNodeIds");
            JsonArray target = new JsonArray();
            for (JsonElement item : source) {
                String raw = item.isJsonPrimitive() ? item.getAsString() : "";
                target.add(mapping.getOrDefault(raw, raw));
            }
            object.add("targetNodeIds", target);
        }
        String actionType = firstString(object, "type").toLowerCase(Locale.ROOT).replace('-', '_');
        if ("open_node".equals(actionType)) {
            rewriteStringProperty(object, "nodeId", mapping);
            rewriteStringProperty(object, "targetId", mapping);
        }
        for (Map.Entry<String, JsonElement> child : new ArrayList<>(object.entrySet())) {
            rewriteNodeReferences(child.getValue(), mapping);
        }
    }

    private static void rewriteStringProperty(JsonObject object, String key, Map<String, String> mapping) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) return;
        String value = object.get(key).getAsString();
        String migrated = mapping.get(value);
        if (migrated != null) object.addProperty(key, migrated);
    }

    private static void migrateQuestPrerequisites(JsonObject quest) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (quest.has("parentQuestIds") && quest.get("parentQuestIds").isJsonArray()) {
            for (JsonElement e : quest.getAsJsonArray("parentQuestIds")) if (e.isJsonPrimitive()) values.add(e.getAsString());
        }
        String required = firstString(quest, "requiredQuestId");
        if (!required.isBlank()) values.add(required);
        JsonArray out = new JsonArray();
        values.stream().filter(v -> v != null && !v.isBlank()).forEach(out::add);
        quest.add("parentQuestIds", out);
        quest.remove("requiredQuestId");
    }

    private static void migrateAcceptFlags(JsonObject quest) {
        JsonArray actions = quest.has("acceptActions") && quest.get("acceptActions").isJsonArray()
                ? quest.getAsJsonArray("acceptActions") : new JsonArray();
        Set<String> existing = new HashSet<>();
        for (JsonElement e : actions) {
            if (!e.isJsonObject()) continue;
            JsonObject a = e.getAsJsonObject();
            String type = canonicalActionType(firstString(a, "type"));
            if ("set_flag".equals(type)) existing.add(firstNonBlank(firstString(a, "flagName"), firstString(a, "flag", "targetId")));
        }
        if (quest.has("flagsToSetOnAccept") && quest.get("flagsToSetOnAccept").isJsonArray()) {
            for (JsonElement e : quest.getAsJsonArray("flagsToSetOnAccept")) {
                if (!e.isJsonPrimitive()) continue;
                String flag = e.getAsString().trim();
                if (flag.isBlank() || !existing.add(flag)) continue;
                JsonObject action = new JsonObject();
                action.addProperty("type", "set_flag");
                action.addProperty("flagName", flag);
                actions.add(action);
            }
        }
        quest.add("acceptActions", actions);
        quest.remove("flagsToSetOnAccept");
    }

    private static void migrateManualRefs(JsonObject owner) {
        if (owner.has("manualRefs") && owner.get("manualRefs").isJsonArray()) {
            owner.remove("manualIds");
            return;
        }
        JsonArray refs = new JsonArray();
        if (owner.has("manualIds") && owner.get("manualIds").isJsonArray()) {
            for (JsonElement e : owner.getAsJsonArray("manualIds")) {
                if (!e.isJsonPrimitive()) continue;
                String id = e.getAsString().trim();
                if (id.isBlank()) continue;
                JsonObject ref = new JsonObject();
                ref.addProperty("manualId", id);
                ref.addProperty("lessonId", "");
                ref.addProperty("pageId", "");
                refs.add(ref);
            }
        }
        if (!refs.isEmpty()) owner.add("manualRefs", refs);
        owner.remove("manualIds");
    }

    private static void convertRequiredQuestToCondition(JsonObject owner) {
        String questId = firstString(owner, "requiredQuestId");
        if (questId.isBlank()) return;
        JsonArray conditions = owner.has("conditions") && owner.get("conditions").isJsonArray()
                ? owner.getAsJsonArray("conditions") : new JsonArray();
        JsonObject condition = new JsonObject();
        condition.addProperty("condition", "phantasmbriefing:pb_quest_completed");
        condition.addProperty("questId", questId);
        conditions.add(condition);
        owner.add("conditions", conditions);
        owner.remove("requiredQuestId");
    }

    private static void normalizeConditions(JsonObject owner) {
        normalizeConditionArray(owner, "conditions");
    }

    private static void normalizeConditionArray(JsonObject owner, String key) {
        if (!owner.has(key) || !owner.get(key).isJsonArray()) return;
        for (JsonElement e : owner.getAsJsonArray(key)) normalizeConditionElement(e);
    }

    private static void normalizeSingleCondition(JsonObject owner, String key) {
        if (owner.has(key) && owner.get(key).isJsonObject()) normalizeConditionElement(owner.get(key));
    }

    private static void normalizeConditionElement(JsonElement element) {
        if (!element.isJsonObject()) return;
        JsonObject condition = element.getAsJsonObject();
        String raw = firstString(condition, "condition").trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!raw.isBlank()) {
            String local = raw;
            if (local.startsWith("chronicle_engine:")) local = local.substring("chronicle_engine:".length());
            else if (local.startsWith("phantasmbriefing:")) local = local.substring("phantasmbriefing:".length());
            local = switch (local) {
                case "quest_accepted" -> "pb_quest_accepted";
                case "quest_available" -> "pb_quest_available";
                case "quest_ready_to_turn_in" -> "pb_quest_ready_to_turn_in";
                case "quest_completed" -> "pb_quest_completed";
                case "quest_not_started" -> "pb_quest_not_started";
                case "quest_phase" -> "pb_quest_phase";
                case "quest_phase_completed" -> "pb_quest_phase_completed";
                case "quest_objective_completed" -> "pb_quest_objective_completed";
                default -> local;
            };
            condition.addProperty("condition", "phantasmbriefing:" + local);
        }
        if (!condition.has("flag") && condition.has("flagName")) condition.add("flag", condition.remove("flagName"));
        if (condition.has("conditions") && condition.get("conditions").isJsonArray()) {
            for (JsonElement child : condition.getAsJsonArray("conditions")) normalizeConditionElement(child);
        }
        if (condition.has("inner")) normalizeConditionElement(condition.get("inner"));
    }

    private static void normalizeActions(JsonObject owner, String key) {
        if (!owner.has(key) || !owner.get(key).isJsonArray()) return;
        for (JsonElement e : owner.getAsJsonArray(key)) {
            if (!e.isJsonObject()) continue;
            normalizeAction(e.getAsJsonObject());
        }
    }

    private static void normalizeAction(JsonObject action) {
        String type = canonicalActionType(firstString(action, "type"));
        if (type.isBlank()) return;
        action.addProperty("type", type);
        String targetId = firstString(action, "targetId");
        switch (type) {
            case "complete_quest", "start_quest", "complete_ftb_quest" -> {
                String questId = firstNonBlank(firstString(action, "questId"), targetId);
                if (!questId.isBlank()) action.addProperty("questId", questId);
            }
            case "complete_objective" -> {
                String questId = firstString(action, "questId");
                String objectiveId = firstString(action, "objectiveId");
                if ((questId.isBlank() || objectiveId.isBlank()) && !targetId.isBlank()) {
                    String[] split = splitCompositeTarget(targetId);
                    if (questId.isBlank()) questId = split[0];
                    if (objectiveId.isBlank()) objectiveId = split[1];
                }
                if (!questId.isBlank()) action.addProperty("questId", questId);
                if (!objectiveId.isBlank()) action.addProperty("objectiveId", objectiveId);
            }
            case "set_quest_phase" -> {
                String questId = firstString(action, "questId");
                String phaseId = firstString(action, "phaseId");
                if ((questId.isBlank() || phaseId.isBlank()) && !targetId.isBlank()) {
                    String[] split = splitCompositeTarget(targetId);
                    if (questId.isBlank()) questId = split[0];
                    if (phaseId.isBlank()) phaseId = split[1];
                }
                if (!questId.isBlank()) action.addProperty("questId", questId);
                if (!phaseId.isBlank()) action.addProperty("phaseId", phaseId);
            }
            case "open_node" -> {
                String nodeId = firstNonBlank(firstString(action, "nodeId"), targetId);
                if (!nodeId.isBlank()) action.addProperty("nodeId", nodeId);
            }
            case "open_shop" -> {
                String shopId = firstNonBlank(firstString(action, "shopId"), targetId);
                if (!shopId.isBlank()) action.addProperty("shopId", shopId);
            }
            case "give_item" -> {
                String itemId = firstNonBlank(firstString(action, "itemId"), targetId);
                if (!itemId.isBlank()) action.addProperty("itemId", itemId);
            }
            case "set_flag" -> {
                String flagName = firstNonBlank(firstString(action, "flagName"), firstNonBlank(firstString(action, "flag"), targetId));
                if (!flagName.isBlank()) action.addProperty("flagName", flagName);
            }
            default -> { }
        }
        action.remove("targetId");
        action.remove("flag");
    }

    private static String[] splitCompositeTarget(String value) {
        String raw = value == null ? "" : value.trim();
        int separator = raw.indexOf("::");
        if (separator <= 0 || separator >= raw.length() - 2) return new String[]{"", ""};
        return new String[]{raw.substring(0, separator), raw.substring(separator + 2)};
    }

    private static String canonicalActionType(String raw) {
        String type = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (type) {
            case "close" -> "close_dialogue";
            case "open_trade" -> "open_shop";
            case "give" -> "give_item";
            default -> type;
        };
    }

    private static String canonicalObjectiveType(String raw) {
        String type = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (type) {
            case "collect", "has_item", "has_items" -> "collect";
            case "interact", "talk", "talk_to" -> "interact";
            case "visit", "location", "go_to", "reach" -> "visit";
            case "craft", "craft_item", "craft_items" -> "craft";
            case "ftb_quest", "ftb", "ftbquest", "complete_ftb_quest" -> "ftb_quest";
            case "tutorial", "image_tutorial", "view_tutorial", "read_manual" -> "read_manual";
            case "kill" -> "kill";
            default -> "manual";
        };
    }

    private static void normalizeMode(JsonObject owner, String currentKey, String oldKey) {
        String raw = firstNonBlank(firstString(owner, currentKey), firstString(owner, oldKey));
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        boolean free = normalized.equals("free") || normalized.equals("parallel") || normalized.equals("non_linear")
                || normalized.equals("nonlinear") || normalized.equals("any_order");
        owner.addProperty(currentKey, free ? "free" : "sequential");
        owner.remove(oldKey);
    }

    private static ImageMigrationPlan planImageMigration(Path modConfig) throws IOException {
        Path oldDir = modConfig.resolve("tutorial_images");
        Path currentDir = modConfig.resolve("manual_images");
        Map<Path, Path> copies = new LinkedHashMap<>();
        if (!Files.isDirectory(oldDir)) return new ImageMigrationPlan(copies, null);
        try (Stream<Path> paths = Files.walk(oldDir)) {
            for (Path source : paths.filter(Files::isRegularFile).sorted().toList()) {
                Path relative = oldDir.relativize(source);
                Path target = currentDir.resolve(relative);
                if (!Files.exists(target)) {
                    copies.put(source, target);
                    continue;
                }
                if (!Files.isRegularFile(target) || Files.mismatch(source, target) != -1L) {
                    throw new IOException("Cannot migrate old tutorial image because a different current manual image already exists: " + relative);
                }
            }
        }
        return new ImageMigrationPlan(copies, oldDir);
    }

    private static void applyPlan(Path modConfig, Path packRoot, MigrationPlan plan) throws IOException {
        Path backupRoot = modConfig.resolve("migration_backup").resolve(LocalDateTime.now().format(BACKUP_TIME));
        for (Path file : plan.rewrites.keySet()) {
            if (Files.exists(file)) {
                Path relative = packRoot.relativize(file);
                Path backup = backupRoot.resolve("briefing_pack").resolve(relative);
                Files.createDirectories(backup.getParent());
                Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
        if (plan.legacyImageDirectory != null && Files.isDirectory(plan.legacyImageDirectory)) {
            try (Stream<Path> paths = Files.walk(plan.legacyImageDirectory)) {
                for (Path source : paths.filter(Files::isRegularFile).sorted().toList()) {
                    Path relative = plan.legacyImageDirectory.relativize(source);
                    Path backup = backupRoot.resolve("tutorial_images").resolve(relative);
                    Files.createDirectories(backup.getParent());
                    Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }

        for (Map.Entry<Path, byte[]> rewrite : plan.rewrites.entrySet()) {
            Files.createDirectories(rewrite.getKey().getParent());
            Path temp = rewrite.getKey().resolveSibling(rewrite.getKey().getFileName() + ".migrating");
            Files.write(temp, rewrite.getValue());
            try {
                Files.move(temp, rewrite.getKey(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(temp, rewrite.getKey(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        for (Map.Entry<Path, Path> copy : plan.copies.entrySet()) {
            Files.createDirectories(copy.getValue().getParent());
            Files.copy(copy.getKey(), copy.getValue(), StandardCopyOption.COPY_ATTRIBUTES);
        }
        if (plan.legacyImageDirectory != null && Files.isDirectory(plan.legacyImageDirectory)) {
            deleteRecursively(plan.legacyImageDirectory);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static Set<String> collectManualIds(Map<Path, JsonObject> files, Path manualDir) {
        Set<String> ids = new HashSet<>();
        for (Map.Entry<Path, JsonObject> entry : files.entrySet()) {
            if (!entry.getKey().startsWith(manualDir)) continue;
            String id = firstString(entry.getValue(), "manualId", "id");
            if (!id.isBlank()) ids.add(id);
        }
        return ids;
    }

    private static String domainFor(Path packRoot, Path file) {
        Path relative;
        try { relative = packRoot.relativize(file); } catch (IllegalArgumentException e) { return ""; }
        if (relative.getNameCount() == 0) return "";
        return switch (relative.getName(0).toString()) {
            case "phantasm_quests" -> QUESTS;
            case "phantasm_manuals" -> MANUALS;
            case "phantasm_dialogues" -> DIALOGUES;
            case "phantasm_npc_bindings" -> NPCS;
            case "phantasm_shops" -> SHOPS;
            case "phantasm_trades" -> TRADES;
            case "phantasm_wallet" -> WALLETS;
            default -> "";
        };
    }

    private static JsonObject alwaysUnlock() {
        JsonObject unlock = new JsonObject();
        unlock.addProperty("type", "always");
        return unlock;
    }

    private static String uniqueDialogueId(String oldNodeId, Set<String> used) {
        String raw = oldNodeId == null || oldNodeId.isBlank() ? "migrated_dialogue" : oldNodeId.trim();
        String base = raw.contains(":") ? raw : "phantasmbriefing:" + slug(raw);
        String id = base;
        int index = 2;
        while (used.contains(id)) id = base + "_" + index++;
        return id;
    }

    private static String localPart(String id) {
        int colon = id == null ? -1 : id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : (id == null ? "" : id);
    }

    private static String slug(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "migrated" : normalized;
    }

    private static void copyTextField(JsonObject owner, String target, String... sources) {
        String value = owner.has(target) ? textValue(owner.get(target)) : "";
        if (value.isBlank()) {
            for (String source : sources) {
                if (!owner.has(source)) continue;
                value = textValue(owner.get(source));
                if (!value.isBlank()) break;
            }
        }
        if (!value.isBlank()) owner.addProperty(target, value);
        else if (owner.has(target) && !owner.get(target).isJsonPrimitive()) owner.remove(target);
        for (String source : sources) if (!source.equals(target)) owner.remove(source);
    }

    private static void normalizeTextArray(JsonObject owner, String key) {
        if (!owner.has(key) || !owner.get(key).isJsonArray()) return;
        JsonArray normalized = new JsonArray();
        for (JsonElement element : owner.getAsJsonArray(key)) {
            String value = textValue(element);
            if (!value.isBlank()) normalized.add(value);
        }
        owner.add(key, normalized);
    }

    private static String firstText(JsonObject owner, String... keysOrFallback) {
        for (String key : keysOrFallback) {
            if (owner.has(key)) {
                String value = textValue(owner.get(key));
                if (!value.isBlank()) return value;
            }
        }
        return keysOrFallback.length == 0 ? "" : keysOrFallback[keysOrFallback.length - 1];
    }

    private static String textValue(JsonElement element) {
        if (element == null || element.isJsonNull()) return "";
        if (element.isJsonPrimitive()) return element.getAsString().trim();
        if (!element.isJsonObject()) return "";
        JsonObject object = element.getAsJsonObject();
        for (String key : List.of("value", "text", "key")) {
            if (object.has(key) && object.get(key).isJsonPrimitive()) return object.get(key).getAsString().trim();
        }
        return "";
    }

    private static String firstString(JsonObject owner, String... keys) {
        for (String key : keys) {
            if (owner.has(key) && owner.get(key).isJsonPrimitive()) {
                String value = owner.get(key).getAsString().trim();
                if (!value.isBlank()) return value;
            }
        }
        return "";
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? (second == null ? "" : second) : first;
    }

    private static void replaceObject(JsonObject destination, JsonObject source) {
        for (String key : new ArrayList<>(destination.keySet())) destination.remove(key);
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) destination.add(entry.getKey(), entry.getValue());
    }

    private static boolean sameJson(JsonObject a, JsonObject b) {
        return a != null && a.equals(b);
    }

    private static byte[] bytes(JsonObject object) {
        return (GSON.toJson(object) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static boolean isJson(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private record ImageMigrationPlan(Map<Path, Path> copies, Path legacyDirectory) {
    }

    private record MigrationPlan(
            Map<Path, byte[]> rewrites,
            Map<Path, Path> copies,
            Set<String> changedDomains,
            Path legacyImageDirectory
    ) {
    }
}
