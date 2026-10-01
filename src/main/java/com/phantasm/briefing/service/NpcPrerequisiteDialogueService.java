package com.phantasm.briefing.service;

import com.phantasm.briefing.data.NpcBindingDataManager;
import com.phantasm.briefing.data.NpcBindingSpec;
import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.QuestSpec;
import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.network.packet.OpenDialogueNodeS2CPacket;
import com.phantasm.briefing.util.QuestNpcHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class NpcPrerequisiteDialogueService {
    private static final String DEFAULT_LOCKED_TEXT = "现在还不到时候，等你完成前面的事情再来找我吧。";

    private NpcPrerequisiteDialogueService() {
    }

    public static boolean openLockedNoticeIfNeeded(ServerPlayer player, Entity npc) {
        Optional<NpcBindingSpec> binding = resolveBinding(npc);
        if (binding.isEmpty() || binding.get().questIds().isEmpty()) {
            return false;
        }

        boolean foundLinkedQuest = false;
        for (String questId : binding.get().questIds()) {
            QuestSpec quest = QuestDataManager.getInstance().getQuest(questId).orElse(null);
            if (quest == null) {
                continue;
            }
            foundLinkedQuest = true;
            if (quest.allParentQuestIds().isEmpty()) {
                return false;
            }
            boolean blocked = quest.allParentQuestIds().stream()
                    .anyMatch(prerequisiteId -> !QuestRuntimeService.isQuestCompleted(player, prerequisiteId));
            if (!blocked) {
                return false;
            }
        }
        if (!foundLinkedQuest) {
            return false;
        }

        String configuredText = binding.get().prerequisiteLockedText();
        List<String> lines = splitLines(configuredText.isBlank() ? DEFAULT_LOCKED_TEXT : configuredText);
        String title = npc.getDisplayName().getString().trim();
        if (title.isBlank()) {
            title = "任务 NPC";
        }

        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                OpenDialogueNodeS2CPacket.notice(
                        "prerequisite_locked:" + binding.get().bindingId(),
                        title,
                        lines,
                        npc.getId(),
                        binding.get().typewriterSound()
                )
        );
        return true;
    }

    private static Optional<NpcBindingSpec> resolveBinding(Entity npc) {
        NpcBindingDataManager manager = NpcBindingDataManager.getInstance();
        String npcIdentity = QuestNpcHelper.getNpcIdentity(npc);
        Optional<NpcBindingSpec> identifiedBinding = manager.getBinding(npcIdentity);
        return identifiedBinding.isPresent() ? identifiedBinding : manager.findBinding(npc);
    }

    private static List<String> splitLines(String text) {
        List<String> lines = Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
        return lines.isEmpty() ? List.of(DEFAULT_LOCKED_TEXT) : lines;
    }
}
