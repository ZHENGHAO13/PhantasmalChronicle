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

import static com.phantasm.briefing.data.JsonDataUtil.sanitize;
import static com.phantasm.briefing.data.JsonDataUtil.readText;
import static com.phantasm.briefing.data.JsonDataUtil.readObjectArray;
import static com.phantasm.briefing.data.JsonDataUtil.normalizeDialogueAutoTrigger;

public record DialogueSpec(
        String dialogueId,
        String defaultNpcName,
        String startNodeId,
        List<String> entryNodeIds,
        List<DialogueSpecNode> nodes
) {
    private static final String NODE_ID_SEPARATOR = "::";

    public static DialogueSpec fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String dialogueId = sanitize(GsonHelper.getAsString(json, "id", ""));
        String defaultNpcName = readText(json, "defaultNpc");
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

        String resolvedStartNodeId = startNodeId.isBlank() ? "root" : startNodeId;
        List<String> entryNodeIds = new ArrayList<>();
        if (json.has("entryNodeIds") && json.get("entryNodeIds").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("entryNodeIds")) {
                String entryNodeId = sanitize(element.getAsString());
                if (!entryNodeId.isBlank() && !entryNodeIds.contains(entryNodeId)) {
                    entryNodeIds.add(entryNodeId);
                }
            }
        }
        if (entryNodeIds.isEmpty()) {
            entryNodeIds.add(resolvedStartNodeId);
        }

        return new DialogueSpec(
                dialogueId,
                defaultNpcName.isBlank() ? dialogueId : defaultNpcName,
                resolvedStartNodeId,
                List.copyOf(entryNodeIds),
                List.copyOf(nodes)
        );
    }

    public String getScopedStartNodeId() {
        return scopeNodeId(this.dialogueId, this.startNodeId);
    }

    public List<String> getScopedEntryNodeIds() {
        Set<String> bottomNodeIds = new LinkedHashSet<>();
        for (DialogueSpecNode node : this.nodes) {
            if (node.keepAtBottom()) {
                bottomNodeIds.add(node.nodeId());
            }
        }

        List<String> ordered = new ArrayList<>();
        for (String nodeId : this.entryNodeIds) {
            if (!bottomNodeIds.contains(nodeId)) {
                ordered.add(nodeId);
            }
        }
        for (String nodeId : this.entryNodeIds) {
            if (bottomNodeIds.contains(nodeId)) {
                ordered.add(nodeId);
            }
        }
        return ordered.stream()
                .map(nodeId -> scopeNodeId(this.dialogueId, nodeId))
                .toList();
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
            List<DialogueContentEntry> contents,
            boolean keepAtBottom,
            boolean triggerVfx,
            String nextNodeId,
            String autoTrigger,
            DialoguePresentationMode presentationMode,
            List<DialogueOption> options,
            List<JsonObject> conditions,
            List<DialogueAction> completionActions
    ) {
        public static DialogueSpecNode fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
            String nodeId = sanitize(GsonHelper.getAsString(json, "nodeId", ""));
            if (nodeId.isBlank()) {
                throw new IllegalArgumentException("Dialogue spec node id is blank: " + sourceDescription);
            }

            String title = readText(json, "title");
            List<DialogueContentEntry> contents = new ArrayList<>();
            if (json.has("contents") && json.get("contents").isJsonArray()) {
                for (JsonElement element : GsonHelper.getAsJsonArray(json, "contents")) {
                    if (element.isJsonObject()) {
                        DialogueContentEntry entry = DialogueContentEntry.fromJson(element.getAsJsonObject());
                        if (!entry.value().isBlank()) contents.add(entry);
                    }
                }
            }
            if (contents.isEmpty()) {
                throw new IllegalArgumentException("Dialogue spec node contents are empty: " + sourceDescription);
            }

            String nextNodeId = sanitize(GsonHelper.getAsString(json, "nextNodeId", ""));
            boolean keepAtBottom = GsonHelper.getAsBoolean(json, "keepAtBottom", false);
            boolean triggerVfx = GsonHelper.getAsBoolean(json, "triggerVFX", false);
            String autoTrigger = normalizeDialogueAutoTrigger(GsonHelper.getAsString(json, "autoTrigger", "none"));
            DialoguePresentationMode presentationMode = DialoguePresentationMode.fromSerializedName(
                    GsonHelper.getAsString(json, "presentation", "briefing")
            );

            List<DialogueOption> options = new ArrayList<>();
            if (json.has("choices") && json.get("choices").isJsonArray()) {
                for (JsonElement element : GsonHelper.getAsJsonArray(json, "choices")) {
                    if (element.isJsonObject()) {
                        options.add(DialogueOption.fromJson(sourceDescription + "#" + nodeId, element.getAsJsonObject()));
                    }
                }
            }
            List<JsonObject> conditions = readObjectArray(json, "conditions");
            List<DialogueAction> completionActions = JsonDataUtil.readActions(sourceDescription + "#" + nodeId, json, "completionActions");

            return new DialogueSpecNode(
                    nodeId,
                    title,
                    List.copyOf(contents),
                    keepAtBottom,
                    triggerVfx,
                    nextNodeId,
                    autoTrigger,
                    presentationMode,
                    List.copyOf(options),
                    List.copyOf(conditions),
                    List.copyOf(completionActions)
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
                    rewrittenActions.add(new DialogueAction(action.type(), targetId, action.count()));
                }
                rewrittenOptions.add(new DialogueOption(
                        option.optionId(),
                        option.label(),
                        List.copyOf(rewrittenActions),
                        option.conditions()
                ));
            }

            String rewrittenNextNodeId = localNodeIds.contains(this.nextNodeId)
                    ? scopeNodeId(dialogueId, this.nextNodeId)
                    : this.nextNodeId;
            List<DialogueAction> rewrittenCompletionActions = new ArrayList<>();
            for (DialogueAction action : this.completionActions) {
                String targetId = action.targetId();
                if (action.type() == DialogueActionType.OPEN_NODE && localNodeIds.contains(targetId)) {
                    targetId = scopeNodeId(dialogueId, targetId);
                }
                rewrittenCompletionActions.add(new DialogueAction(action.type(), targetId, action.count()));
            }

            return new DialogueNode(
                    scopeNodeId(dialogueId, this.nodeId),
                    this.title.isBlank() ? defaultNpcName : this.title,
                    this.contents,
                    this.triggerVfx,
                    rewrittenNextNodeId,
                    this.autoTrigger,
                    this.presentationMode,
                    List.copyOf(rewrittenOptions),
                    this.conditions,
                    List.copyOf(rewrittenCompletionActions)
            );
        }

    }

}
