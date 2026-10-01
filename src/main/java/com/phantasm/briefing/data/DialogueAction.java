package com.phantasm.briefing.data;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import javax.annotation.Nonnull;

import static com.phantasm.briefing.data.JsonDataUtil.sanitize;

/** Current runtime action model. Old action shapes are normalized by ContentMigrationService. */
public record DialogueAction(
        DialogueActionType type,
        String targetId,
        int count
) {
    public DialogueAction(DialogueActionType type, String targetId) {
        this(type, targetId, 1);
    }

    public static DialogueAction fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String rawType = sanitize(GsonHelper.getAsString(json, "type", ""));
        if (rawType.isBlank()) {
            throw new IllegalArgumentException("Dialogue action type is blank: " + sourceDescription);
        }

        DialogueActionType type = DialogueActionType.fromSerializedName(rawType);
        String targetId = switch (type) {
            case COMPLETE_QUEST, COMPLETE_FTB_QUEST, START_QUEST ->
                    sanitize(GsonHelper.getAsString(json, "questId", ""));
            case COMPLETE_OBJECTIVE -> encodeQuestObjectiveTarget(
                    sanitize(GsonHelper.getAsString(json, "questId", "")),
                    sanitize(GsonHelper.getAsString(json, "objectiveId", ""))
            );
            case SET_QUEST_PHASE -> encodeQuestPhaseTarget(
                    sanitize(GsonHelper.getAsString(json, "questId", "")),
                    sanitize(GsonHelper.getAsString(json, "phaseId", ""))
            );
            case OPEN_NODE -> sanitize(GsonHelper.getAsString(json, "nodeId", ""));
            case OPEN_SHOP -> sanitize(GsonHelper.getAsString(json, "shopId", ""));
            case GIVE_ITEM -> sanitize(GsonHelper.getAsString(json, "itemId", ""));
            case SET_FLAG -> sanitize(GsonHelper.getAsString(json, "flagName", ""));
            case REMOVE_CONTEXT_NPC, CLOSE_DIALOGUE -> "";
        };
        int count = type == DialogueActionType.GIVE_ITEM
                ? Math.max(1, GsonHelper.getAsInt(json, "count", 1))
                : 1;

        if ((type == DialogueActionType.COMPLETE_QUEST
                || type == DialogueActionType.COMPLETE_FTB_QUEST
                || type == DialogueActionType.COMPLETE_OBJECTIVE
                || type == DialogueActionType.START_QUEST
                || type == DialogueActionType.SET_QUEST_PHASE
                || type == DialogueActionType.OPEN_NODE
                || type == DialogueActionType.OPEN_SHOP
                || type == DialogueActionType.GIVE_ITEM
                || type == DialogueActionType.SET_FLAG) && targetId.isBlank()) {
            throw new IllegalArgumentException("Dialogue action target is blank for " + type + ": " + sourceDescription);
        }

        return new DialogueAction(type, targetId, count);
    }

    @Nonnull
    public static String encodeQuestPhaseTarget(@Nonnull String questId, @Nonnull String phaseId) {
        String sanitizedQuestId = sanitize(questId);
        String sanitizedPhaseId = sanitize(phaseId);
        if (sanitizedQuestId.isBlank() || sanitizedPhaseId.isBlank()) {
            return "";
        }
        return sanitizedQuestId + "::" + sanitizedPhaseId;
    }

    @Nonnull
    public static String encodeQuestObjectiveTarget(@Nonnull String questId, @Nonnull String objectiveId) {
        String sanitizedQuestId = sanitize(questId);
        String sanitizedObjectiveId = sanitize(objectiveId);
        if (sanitizedQuestId.isBlank() || sanitizedObjectiveId.isBlank()) {
            return "";
        }
        return sanitizedQuestId + "::" + sanitizedObjectiveId;
    }

    @Nonnull
    public static String decodeQuestId(String encodedTargetId) {
        String[] parts = splitQuestTarget(encodedTargetId);
        return parts.length == 2 ? parts[0] : "";
    }

    @Nonnull
    public static String decodePhaseId(String encodedTargetId) {
        String[] parts = splitQuestTarget(encodedTargetId);
        return parts.length == 2 ? parts[1] : "";
    }

    @Nonnull
    public static String decodeObjectiveId(String encodedTargetId) {
        String[] parts = splitQuestTarget(encodedTargetId);
        return parts.length == 2 ? parts[1] : "";
    }

    private static String[] splitQuestTarget(String encodedTargetId) {
        String sanitized = sanitize(encodedTargetId);
        int separatorIndex = sanitized.indexOf("::");
        if (separatorIndex <= 0 || separatorIndex >= sanitized.length() - 2) {
            return new String[0];
        }
        return new String[]{
                sanitized.substring(0, separatorIndex),
                sanitized.substring(separatorIndex + 2)
        };
    }
}
