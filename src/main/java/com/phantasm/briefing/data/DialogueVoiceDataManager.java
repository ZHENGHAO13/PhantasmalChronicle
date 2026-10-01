package com.phantasm.briefing.data;

import com.mojang.logging.LogUtils;
import com.phantasm.briefing.PhantasmBriefing;
import com.phantasm.briefing.dialogue.sound.DialogueVoiceDefinition;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Server-side catalog of dialogue voices supplied by datapacks.
 *
 * <p>Voice metadata is loaded from {@code data/<namespace>/dialogue_voices/*.json}.
 * Actual audio remains a client resource-pack concern. The catalog is cached
 * after reload, so normal dialogue playback performs only constant-time map
 * lookups and does not scan files at runtime.</p>
 */
public final class DialogueVoiceDataManager extends AbstractJsonDataManager<DialogueVoiceDefinition> {
    public static final String DEFAULT_VOICE_ID = PhantasmBriefing.MOD_ID + ":default";
    private static final String DATA_DIRECTORY = "dialogue_voices";
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Compatibility fallback only; it does not bundle or register any audio. */
    private static final DialogueVoiceDefinition FALLBACK_DEFAULT = new DialogueVoiceDefinition(
            DEFAULT_VOICE_ID,
            PhantasmBriefing.MOD_ID + ":dialogue_text_tick",
            "模组默认音效",
            "Mod Default Sound",
            1.0F,
            0
    );

    private static final DialogueVoiceDataManager INSTANCE = new DialogueVoiceDataManager();

    private volatile List<DialogueVoiceDefinition> orderedVoices = List.of(FALLBACK_DEFAULT);

    private DialogueVoiceDataManager() {
        super(
                DATA_DIRECTORY,
                "dialogue voice",
                DialogueVoiceDefinition::fromJson,
                DialogueVoiceDefinition::id,
                LOGGER
        );
    }

    public static DialogueVoiceDataManager getInstance() {
        return INSTANCE;
    }

    public Optional<DialogueVoiceDefinition> getVoice(String id) {
        Optional<DialogueVoiceDefinition> loaded = find(id);
        if (loaded.isPresent()) {
            return loaded;
        }
        if (id != null && DEFAULT_VOICE_ID.equals(id.trim())) {
            return Optional.of(FALLBACK_DEFAULT);
        }
        return Optional.empty();
    }

    public DialogueVoiceDefinition defaultVoice() {
        return getVoice(DEFAULT_VOICE_ID).orElse(FALLBACK_DEFAULT);
    }

    public DialogueVoiceDefinition resolveOrDefault(String id) {
        return getVoice(id).orElseGet(this::defaultVoice);
    }

    public List<DialogueVoiceDefinition> getAllVoices() {
        return this.orderedVoices;
    }

    @Override
    protected void afterReload(Map<String, DialogueVoiceDefinition> loadedValues) {
        ArrayList<DialogueVoiceDefinition> voices = new ArrayList<>(loadedValues.values());
        boolean hasDefault = voices.stream().anyMatch(voice -> DEFAULT_VOICE_ID.equals(voice.id()));
        if (!hasDefault) {
            voices.add(FALLBACK_DEFAULT);
            LOGGER.warn(
                    "[PhantasmBriefing] No '{}' dialogue voice was supplied by datapacks; built-in default metadata fallback is active",
                    DEFAULT_VOICE_ID
            );
        }
        voices.sort(Comparator
                .comparingInt(DialogueVoiceDefinition::sortOrder)
                .thenComparing(DialogueVoiceDefinition::id));
        this.orderedVoices = List.copyOf(voices);
    }
}
