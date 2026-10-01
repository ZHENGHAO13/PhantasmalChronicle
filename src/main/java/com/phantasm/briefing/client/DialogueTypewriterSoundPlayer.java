package com.phantasm.briefing.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Plays lightweight local dialogue sounds resolved directly from SoundManager resource ids. */
public final class DialogueTypewriterSoundPlayer {
    private static final RandomSource RANDOM = RandomSource.create();

    private DialogueTypewriterSoundPlayer() {
    }

    public static void play(
            String character,
            int characterIndex,
            boolean enabled,
            String soundEventId,
            float volume
    ) {
        if (!enabled || shouldSkip(character)) {
            return;
        }

        float pitch = 0.96F + (Math.floorMod(characterIndex, 5) * 0.018F);
        playUiSound(soundEventId, pitch, volume);
    }

    public static void playPreview(String soundEventId, float volume) {
        playUiSound(soundEventId, 1.0F, volume);
    }

    private static void playUiSound(String soundEventId, float pitch, float volume) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || soundEventId == null || soundEventId.isBlank()) {
            return;
        }

        ResourceLocation soundId = ResourceLocation.tryParse(soundEventId.trim());
        if (soundId == null) {
            return;
        }

        float safeVolume = Float.isFinite(volume) ? Math.max(0.0F, Math.min(2.0F, volume)) : 1.0F;
        if (safeVolume <= 0.0F) {
            return;
        }
        float safePitch = Float.isFinite(pitch) ? Math.max(0.5F, Math.min(2.0F, pitch)) : 1.0F;

        SimpleSoundInstance soundInstance = new SimpleSoundInstance(
                soundId,
                SoundSource.MASTER,
                safeVolume,
                safePitch,
                RANDOM,
                false,
                0,
                SoundInstance.Attenuation.NONE,
                0.0D,
                0.0D,
                0.0D,
                true
        );
        minecraft.getSoundManager().play(soundInstance);
    }

    private static boolean shouldSkip(String character) {
        if (character == null || character.isBlank()) {
            return true;
        }
        int codePoint = character.codePointAt(0);
        return switch (Character.getType(codePoint)) {
            case Character.CONNECTOR_PUNCTUATION,
                    Character.DASH_PUNCTUATION,
                    Character.START_PUNCTUATION,
                    Character.END_PUNCTUATION,
                    Character.INITIAL_QUOTE_PUNCTUATION,
                    Character.FINAL_QUOTE_PUNCTUATION,
                    Character.OTHER_PUNCTUATION -> true;
            default -> false;
        };
    }
}
