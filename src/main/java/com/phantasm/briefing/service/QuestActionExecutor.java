package com.phantasm.briefing.service;

import com.mojang.logging.LogUtils;
import com.phantasm.briefing.data.DialogueAction;
import com.phantasm.briefing.data.DialogueActionType;
import com.phantasm.briefing.integration.FTBIntegrationHelper;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.List;

/** Executes quest-owned actions without coupling action parsing to quest flow code. */
final class QuestActionExecutor {
    private static final Logger LOGGER = LogUtils.getLogger();

    private QuestActionExecutor() {
    }

    static void execute(ServerPlayer player, String questId, List<DialogueAction> actions,
                        String ownerType, String ownerId, boolean allowSelfCompleteQuestAction) {
        for (DialogueAction action : actions) {
            if (action.type() == DialogueActionType.SET_FLAG) {
                PlayerFlagService.setFlag(player, action.targetId());
                continue;
            }
            if (action.type() == DialogueActionType.START_QUEST) {
                QuestRuntimeService.accept(player, action.targetId());
                continue;
            }
            if (action.type() == DialogueActionType.COMPLETE_QUEST) {
                if (!allowSelfCompleteQuestAction && questId.equals(action.targetId())) {
                    LOGGER.warn("[PhantasmBriefing] Ignored recursive complete_quest on {} '{}' of quest '{}'",
                            ownerType, ownerId, questId);
                    continue;
                }
                QuestRuntimeService.complete(player, action.targetId());
                continue;
            }
            if (action.type() == DialogueActionType.COMPLETE_FTB_QUEST) {
                FTBIntegrationHelper.completeQuestSilently(player, action.targetId());
                continue;
            }
            if (action.type() == DialogueActionType.OPEN_SHOP) {
                if (!DialogueShopService.openShop(player, action.targetId())) {
                    LOGGER.warn("[PhantasmBriefing] {} '{}' of quest '{}' failed to open shop '{}'",
                            ownerType, ownerId, questId, action.targetId());
                }
                continue;
            }
            if (action.type() == DialogueActionType.GIVE_ITEM) {
                if (!PlayerItemRewardService.giveItem(player, action.targetId(), action.count())) {
                    LOGGER.warn("[PhantasmBriefing] {} '{}' of quest '{}' failed to give item '{}' x{}",
                            ownerType, ownerId, questId, action.targetId(), action.count());
                }
                continue;
            }
            LOGGER.warn("[PhantasmBriefing] Unsupported {} action '{}' on {} '{}' of quest '{}'",
                    ownerType, action.type(), ownerType, ownerId, questId);
        }
    }
}
