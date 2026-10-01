package com.phantasm.briefing.data;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Per-NPC typewriter voice selection.
 *
 * <p>The stored sound id is a dialogue-voice id from the datapack catalog, not
 * a Minecraft SoundEvent id. Unknown ids are deliberately preserved while
 * loading NPC data; the current voice catalog is resolved only when a dialogue
 * packet is built, so datapack reload order cannot erase a valid selection.</p>
 */
public record NpcTypewriterSoundSpec(boolean enabled, String soundId, float volume) {
    public static final float MIN_VOLUME = 0.0F;
    public static final float MAX_VOLUME = 2.0F;

    public NpcTypewriterSoundSpec {
        soundId = normalizeSoundId(soundId);
        volume = clampVolume(volume);
    }

    public static NpcTypewriterSoundSpec defaults() {
        return new NpcTypewriterSoundSpec(true, DialogueVoiceDataManager.DEFAULT_VOICE_ID, 1.0F);
    }

    public static NpcTypewriterSoundSpec disabled() {
        return new NpcTypewriterSoundSpec(false, DialogueVoiceDataManager.DEFAULT_VOICE_ID, 1.0F);
    }

    public static NpcTypewriterSoundSpec fromNpcJson(JsonObject npcJson) {
        if (npcJson == null || !npcJson.has("typewriterSound") || !npcJson.get("typewriterSound").isJsonObject()) {
            return defaults();
        }

        JsonObject soundJson = npcJson.getAsJsonObject("typewriterSound");
        boolean enabled = GsonHelper.getAsBoolean(soundJson, "enabled", true);
        String soundId = GsonHelper.getAsString(soundJson, "soundId", DialogueVoiceDataManager.DEFAULT_VOICE_ID);
        float volume = GsonHelper.getAsFloat(soundJson, "volume", 1.0F);
        return new NpcTypewriterSoundSpec(enabled, soundId, volume);
    }

    private static String normalizeSoundId(String value) {
        if (value == null || value.isBlank()) {
            return DialogueVoiceDataManager.DEFAULT_VOICE_ID;
        }
        return value.trim();
    }

    private static float clampVolume(float value) {
        if (!Float.isFinite(value)) {
            return 1.0F;
        }
        return Math.max(MIN_VOLUME, Math.min(MAX_VOLUME, value));
    }
}
