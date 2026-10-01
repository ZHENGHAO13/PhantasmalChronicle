package com.phantasm.briefing.dialogue.sound;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Data-driven metadata for one dialogue typewriter voice.
 *
 * <p>The voice id is what NPC configuration stores. The sound event id is the
 * logical id declared by an external resource pack's {@code sounds.json}. No
 * OGG file or SoundEvent registration is bundled in the mod itself.</p>
 */
public record DialogueVoiceDefinition(
        String id,
        String soundEventId,
        String displayNameZh,
        String displayNameEn,
        float defaultVolume,
        int sortOrder
) {
    public static DialogueVoiceDefinition fromJson(String sourceId, JsonObject json) {
        String fallbackId = sourceId == null ? "" : sourceId.trim();
        String id = GsonHelper.getAsString(json, "id", fallbackId).trim();
        String soundEventId = GsonHelper.getAsString(json, "soundEventId", "").trim();
        String displayNameZh = GsonHelper.getAsString(json, "nameZh", id).trim();
        String displayNameEn = GsonHelper.getAsString(json, "nameEn", displayNameZh).trim();
        float defaultVolume = GsonHelper.getAsFloat(json, "defaultVolume", 1.0F);
        int sortOrder = GsonHelper.getAsInt(json, "sortOrder", 1000);

        if (id.isBlank() || ResourceLocation.tryParse(id) == null) {
            throw new IllegalArgumentException("Invalid dialogue voice id: " + id);
        }
        if (soundEventId.isBlank() || ResourceLocation.tryParse(soundEventId) == null) {
            throw new IllegalArgumentException("Invalid dialogue voice soundEventId for " + id + ": " + soundEventId);
        }
        if (!Float.isFinite(defaultVolume)) {
            defaultVolume = 1.0F;
        }
        defaultVolume = Math.max(0.0F, Math.min(2.0F, defaultVolume));
        if (displayNameZh.isBlank()) {
            displayNameZh = id;
        }
        if (displayNameEn.isBlank()) {
            displayNameEn = displayNameZh;
        }

        return new DialogueVoiceDefinition(
                id,
                soundEventId,
                displayNameZh,
                displayNameEn,
                defaultVolume,
                sortOrder
        );
    }
}
