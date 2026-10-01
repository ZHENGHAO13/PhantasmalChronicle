package com.phantasm.briefing.service;

import com.phantasm.briefing.data.NpcBindingDataManager;
import com.phantasm.briefing.data.NpcBindingSpec;
import com.phantasm.briefing.data.NpcTypewriterSoundSpec;
import com.phantasm.briefing.util.QuestNpcHelper;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Resolves the current, hot-reloaded typewriter voice for an NPC entity. */
public final class NpcDialogueVoiceService {
    private NpcDialogueVoiceService() {
    }

    public static NpcTypewriterSoundSpec resolve(@Nullable Entity entity) {
        if (entity == null) {
            return NpcTypewriterSoundSpec.defaults();
        }

        NpcBindingDataManager manager = NpcBindingDataManager.getInstance();
        String identity = QuestNpcHelper.getNpcIdentity(entity);
        Optional<NpcBindingSpec> binding = manager.getBinding(identity);
        if (binding.isEmpty()) {
            binding = manager.findBinding(entity);
        }
        return binding.map(NpcBindingSpec::typewriterSound)
                .orElseGet(NpcTypewriterSoundSpec::defaults);
    }
}
