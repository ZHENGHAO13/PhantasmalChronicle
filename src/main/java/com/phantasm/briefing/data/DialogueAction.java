package com.phantasm.briefing.data;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import javax.annotation.Nonnull;

public record DialogueAction(
        DialogueActionType type,
        String targetId
) {

    public static DialogueAction fromJson(@Nonnull String sourceDescription, @Nonnull JsonObject json) {
        String rawType = sanitize(GsonHelper.getAsString(json, "type", ""));
        if (rawType.isBlank()) {
            throw new IllegalArgumentException("Dialogue action type is blank: " + sourceDescription);
        }

        DialogueActionType type = DialogueActionType.fromSerializedName(normalizeSerializedType(rawType));
        String targetId = switch (type) {
            case COMPLETE_QUEST -> firstNonBlank(
                    sanitize(GsonHelper.getAsString(json, "questId", "")),
                    sanitize(GsonHelper.getAsString(json, "targetId", ""))
            );
            case COMPLETE_OBJECTIVE -> encodeQuestObjectiveTarget(
                    sanitize(GsonHelper.getAsString(json, "questId", "")),
                    sanitize(GsonHelper.getAsString(json, "objectiveId", ""))
            );
            case START_QUEST -> firstNonBlank(
                    sanitize(GsonHelper.getAsString(json, "questId", "")),
                    sanitize(GsonHelper.getAsString(json, "targetId", ""))
            );
            case SET_QUEST_PHASE -> encodeQuestPhaseTarget(
                    sanitize(GsonHelper.getAsString(json, "questId", "")),
                    sanitize(GsonHelper.getAsString(json, "phaseId", ""))
            );
            case OPEN_NODE -> firstNonBlank(
                    sanitize(GsonHelper.getAsString(json, "nodeId", "")),
                    sanitize(GsonHelper.getAsString(json, "targetId", ""))
            );
            case OPEN_SHOP -> firstNonBlank(
                    sanitize(GsonHelper.getAsString(json, "shopId", "")),
                    sanitize(GsonHelper.getAsString(json, "targetId", ""))
            );
            case SET_FLAG -> firstNonBlank(
                    sanitize(GsonHelper.getAsString(json, "flagName", "")),
                    firstNonBlank(
                            sanitize(GsonHelper.getAsString(json, "flag", "")),
                            sanitize(GsonHelper.getAsString(json, "targetId", ""))
                    )
            );
            case CLOSE_DIALOGUE -> "";
        };

        if ((type == DialogueActionType.COMPLETE_QUEST
                || type == DialogueActionType.COMPLETE_OBJECTIVE
                || type == DialogueActionType.START_QUEST
                || type == DialogueActionType.SET_QUEST_PHASE
                || type == DialogueActionType.OPEN_NODE
                || type == DialogueActionType.OPEN_SHOP
                || type == DialogueActionType.SET_FLAG) && targetId.isBlank()) {
            throw new IllegalArgumentException("Dialogue action targetId is blank for " + type + ": " + sourceDescription);
        }

        return new DialogueAction(type, targetId);
    }

    @Nonnull
    private static String firstNonBlank(@Nonnull String first, @Nonnull String second) {
        return first.isBlank() ? second : first;
    }

    @Nonnull
    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    @Nonnull
    private static String normalizeSerializedType(String rawType) {
        return switch (sanitize(rawType)) {
            case "close" -> "close_dialogue";
            case "open_trade" -> "open_shop";
            default -> rawType;
        };
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
        String[] parts = splitQuestPhaseTarget(encodedTargetId);
        return parts.length == 2 ? parts[0] : "";
    }

    @Nonnull
    public static String decodePhaseId(String encodedTargetId) {
        String[] parts = splitQuestPhaseTarget(encodedTargetId);
        return parts.length == 2 ? parts[1] : "";
    }

    @Nonnull
    public static String decodeObjectiveId(String encodedTargetId) {
        String[] parts = splitQuestPhaseTarget(encodedTargetId);
        return parts.length == 2 ? parts[1] : "";
    }

    private static String[] splitQuestPhaseTarget(String encodedTargetId) {
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
