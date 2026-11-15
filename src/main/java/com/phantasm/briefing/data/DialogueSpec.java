package com.phantasm.briefing.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public record DialogueSpec(
        String dialogueId,
        String defaultNpcName,
        String startNodeId,
        List<DialogueSpecNode> nodes
) {
    private static final String NODE_ID_SEPARATOR = "::";

    public static DialogueSpec fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String dialogueId = sanitize(GsonHelper.getAsString(json, "id", ""));
        String defaultNpcName = parseText(json.get("defaultNpc"));
        String startNodeId = sanitize(GsonHelper.getAsString(json, "startNodeId", "root"));
        if (dialogueId.isBlank()) {
            throw new IllegalArgumentException("Dialogue id is blank: " + sourceDescription);
        }

        JsonArray nodeArray = GsonHelper.getAsJsonArray(json, "nodes");
        List<DialogueSpecNode> nodes = new ArrayList<>();
        for (JsonElement element : nodeArray) {
            nodes.add(DialogueSpecNode.fromJson(sourceDescription + "#" + dialogueId, element.getAsJsonObject()));
        }
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("Dialogue nodes are empty: " + sourceDescription);
        }

        return new DialogueSpec(
                dialogueId,
                defaultNpcName.isBlank() ? dialogueId : defaultNpcName,
                startNodeId.isBlank() ? "root" : startNodeId,
                List.copyOf(nodes)
        );
    }

    public String getScopedStartNodeId() {
        return scopeNodeId(this.dialogueId, this.startNodeId);
    }

    public Map<String, DialogueNode> flattenNodes() {
        Set<String> localNodeIds = new LinkedHashSet<>();
        for (DialogueSpecNode node : this.nodes) {
            localNodeIds.add(node.nodeId());
        }

        Map<String, DialogueNode> flattened = new LinkedHashMap<>();
        for (DialogueSpecNode node : this.nodes) {
            flattened.put(
                    scopeNodeId(this.dialogueId, node.nodeId()),
                    node.toRuntimeNode(this.dialogueId, this.defaultNpcName, localNodeIds)
            );
        }
        return flattened;
    }

    public static boolean looksLikeDialogueSpec(@Nonnull JsonObject json) {
        return json.has("id") && json.has("nodes");
    }

    @Nonnull
    public static String scopeNodeId(@Nonnull String dialogueId, @Nonnull String nodeId) {
        String normalizedDialogueId = sanitize(dialogueId);
        String normalizedNodeId = sanitize(nodeId);
        if (normalizedDialogueId.isBlank() || normalizedNodeId.isBlank()) {
            return normalizedNodeId;
        }
        return normalizedDialogueId + NODE_ID_SEPARATOR + normalizedNodeId;
    }

    public record DialogueSpecNode(
            String nodeId,
            String title,
            List<String> lines,
            boolean triggerVfx,
            String nextNodeId,
            String requiredQuestId,
            DialoguePresentationMode presentationMode,
            List<DialogueOption> options,
            List<JsonObject> conditions
    ) {
        public static DialogueSpecNode fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
            String nodeId = sanitize(GsonHelper.getAsString(json, "nodeId", ""));
            if (nodeId.isBlank()) {
                throw new IllegalArgumentException("Dialogue spec node id is blank: " + sourceDescription);
            }

            String title = sanitize(GsonHelper.getAsString(json, "title", ""));
            String textLine = parseText(json.get("text"));
            List<String> lines = new ArrayList<>();
            if (json.has("lines")) {
                GsonHelper.getAsJsonArray(json, "lines").forEach(element -> lines.add(sanitize(element.getAsString())));
            } else if (!textLine.isBlank()) {
                lines.add(textLine);
            }
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("Dialogue spec node lines are empty: " + sourceDescription);
            }

            String nextNodeId = sanitize(GsonHelper.getAsString(json, "nextNodeId", ""));
            String requiredQuestId = sanitize(GsonHelper.getAsString(json, "requiredQuestId", ""));
            boolean triggerVfx = GsonHelper.getAsBoolean(json, "triggerVFX", false);
            DialoguePresentationMode presentationMode = DialoguePresentationMode.fromSerializedName(
                    GsonHelper.getAsString(json, "presentation", "briefing")
            );

            List<DialogueOption> options = new ArrayList<>();
            if (json.has("choices")) {
                for (JsonElement element : GsonHelper.getAsJsonArray(json, "choices")) {
                    options.add(parseChoiceSpec(sourceDescription + "#" + nodeId, element.getAsJsonObject()));
                }
            } else if (json.has("options")) {
                GsonHelper.getAsJsonArray(json, "options").forEach(element -> options.add(DialogueOption.fromJson(sourceDescription, element.getAsJsonObject())));
            }
            List<JsonObject> conditions = parseConditions(json);

            return new DialogueSpecNode(
                    nodeId,
                    title,
                    List.copyOf(lines),
                    triggerVfx,
                    nextNodeId,
                    requiredQuestId,
                    presentationMode,
                    List.copyOf(options),
                    List.copyOf(conditions)
            );
        }

        public DialogueNode toRuntimeNode(@Nonnull String dialogueId, @Nonnull String defaultNpcName, @Nonnull Set<String> localNodeIds) {
            List<DialogueOption> rewrittenOptions = new ArrayList<>();
            for (DialogueOption option : this.options) {
                List<DialogueAction> rewrittenActions = new ArrayList<>();
                for (DialogueAction action : option.actions()) {
                    String targetId = action.targetId();
                    if (action.type() == DialogueActionType.OPEN_NODE && localNodeIds.contains(targetId)) {
                        targetId = scopeNodeId(dialogueId, targetId);
                    }
                    rewrittenActions.add(new DialogueAction(action.type(), targetId));
                }
                rewrittenOptions.add(new DialogueOption(
                        option.optionId(),
                        option.label(),
                        option.requiredQuestId(),
                        List.copyOf(rewrittenActions),
                        option.conditions()
                ));
            }

            String rewrittenNextNodeId = localNodeIds.contains(this.nextNodeId)
                    ? scopeNodeId(dialogueId, this.nextNodeId)
                    : this.nextNodeId;

            return new DialogueNode(
                    scopeNodeId(dialogueId, this.nodeId),
                    this.title.isBlank() ? defaultNpcName : this.title,
                    this.lines,
                    this.triggerVfx,
                    "",
                    rewrittenNextNodeId,
                    this.requiredQuestId,
                    this.presentationMode,
                    List.copyOf(rewrittenOptions),
                    this.conditions
            );
        }

        private static DialogueOption parseChoiceSpec(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
            String optionId = sanitize(GsonHelper.getAsString(json, "choiceId", ""));
            String label = parseText(json.get("text"));
            String nextNodeId = sanitize(GsonHelper.getAsString(json, "nextNodeId", ""));
            if (optionId.isBlank()) {
                throw new IllegalArgumentException("Dialogue spec choice id is blank: " + sourceDescription);
            }
            if (label.isBlank()) {
                throw new IllegalArgumentException("Dialogue spec choice text is blank: " + sourceDescription);
            }

            List<DialogueAction> actions = new ArrayList<>();
            if (json.has("actions")) {
                for (JsonElement element : GsonHelper.getAsJsonArray(json, "actions")) {
                    DialogueAction action = parseActionSpec(element.getAsJsonObject());
                    if (action != null) {
                        actions.add(action);
                    }
                }
            }
            if (!nextNodeId.isBlank()) {
                actions.add(new DialogueAction(DialogueActionType.OPEN_NODE, nextNodeId));
            }
            if (actions.isEmpty()) {
                actions.add(new DialogueAction(DialogueActionType.CLOSE_DIALOGUE, ""));
            }

            return new DialogueOption(optionId, label, "", List.copyOf(actions), List.copyOf(parseConditions(json)));
        }

        @Nullable
        private static DialogueAction parseActionSpec(@Nonnull JsonObject json) {
            String rawType = sanitize(GsonHelper.getAsString(json, "type", ""));
            if (rawType.isBlank()) {
                return null;
            }

            return switch (rawType) {
                case "close", "close_dialogue" -> new DialogueAction(DialogueActionType.CLOSE_DIALOGUE, "");
                case "open_shop", "open_trade" -> new DialogueAction(
                        DialogueActionType.OPEN_SHOP,
                        firstNonBlank(
                                sanitize(GsonHelper.getAsString(json, "shopId", "")),
                                sanitize(GsonHelper.getAsString(json, "targetId", ""))
                        )
                );
                case "open_node" -> new DialogueAction(
                        DialogueActionType.OPEN_NODE,
                        firstNonBlank(
                                sanitize(GsonHelper.getAsString(json, "nodeId", "")),
                                sanitize(GsonHelper.getAsString(json, "targetId", ""))
                        )
                );
                case "complete_quest" -> new DialogueAction(
                        DialogueActionType.COMPLETE_QUEST,
                        firstNonBlank(
                                sanitize(GsonHelper.getAsString(json, "questId", "")),
                                sanitize(GsonHelper.getAsString(json, "targetId", ""))
                        )
                );
                case "complete_objective" -> new DialogueAction(
                        DialogueActionType.COMPLETE_OBJECTIVE,
                        DialogueAction.encodeQuestObjectiveTarget(
                                sanitize(GsonHelper.getAsString(json, "questId", "")),
                                sanitize(GsonHelper.getAsString(json, "objectiveId", ""))
                        )
                );
                case "start_quest" -> new DialogueAction(
                        DialogueActionType.START_QUEST,
                        firstNonBlank(
                                sanitize(GsonHelper.getAsString(json, "questId", "")),
                                sanitize(GsonHelper.getAsString(json, "targetId", ""))
                        )
                );
                case "set_quest_phase" -> new DialogueAction(
                        DialogueActionType.SET_QUEST_PHASE,
                        DialogueAction.encodeQuestPhaseTarget(
                                sanitize(GsonHelper.getAsString(json, "questId", "")),
                                sanitize(GsonHelper.getAsString(json, "phaseId", ""))
                        )
                );
                case "set_flag" -> new DialogueAction(
                        DialogueActionType.SET_FLAG,
                        firstNonBlank(
                                sanitize(GsonHelper.getAsString(json, "flagName", "")),
                                firstNonBlank(
                                        sanitize(GsonHelper.getAsString(json, "flag", "")),
                                        sanitize(GsonHelper.getAsString(json, "targetId", ""))
                                )
                        )
                );
                default -> null;
            };
        }
    }

    @Nonnull
    private static List<JsonObject> parseConditions(@Nonnull JsonObject json) {
        List<JsonObject> conditions = new ArrayList<>();
        if (!json.has("conditions")) {
            return conditions;
        }

        for (JsonElement element : GsonHelper.getAsJsonArray(json, "conditions")) {
            if (element.isJsonObject()) {
                conditions.add(element.getAsJsonObject().deepCopy());
            }
        }
        return conditions;
    }

    @Nonnull
    private static String parseText(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return sanitize(element.getAsString());
        }
        if (!element.isJsonObject()) {
            return "";
        }

        JsonObject object = element.getAsJsonObject();
        String literal = sanitize(GsonHelper.getAsString(object, "value", ""));
        if (!literal.isBlank()) {
            return literal;
        }
        return sanitize(GsonHelper.getAsString(object, "key", ""));
    }

    @Nonnull
    private static String firstNonBlank(@Nonnull String first, @Nonnull String second) {
        return first.isBlank() ? second : first;
    }

    @Nonnull
    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
