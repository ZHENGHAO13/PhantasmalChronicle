package com.phantasm.briefing.event;

import com.phantasm.briefing.data.DialogueDataManager;
import com.phantasm.briefing.data.DialogueVoiceDataManager;
import com.phantasm.briefing.data.NpcBindingDataManager;
import com.phantasm.briefing.data.ManualDataManager;
import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.ShopDataManager;
import com.phantasm.briefing.data.TradeDataManager;
import com.phantasm.briefing.data.WalletDataManager;
import com.phantasm.briefing.data.migration.ContentMigrationService;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class DialogueDataReloadEvents {

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        ContentMigrationService.migrateExternalPack();
        event.addListener(DialogueVoiceDataManager.getInstance());
        event.addListener(DialogueDataManager.getInstance());
        event.addListener(NpcBindingDataManager.getInstance());
        event.addListener(QuestDataManager.getInstance());
        event.addListener(ManualDataManager.getInstance());
        event.addListener(ShopDataManager.getInstance());
        event.addListener(TradeDataManager.getInstance());
        event.addListener(WalletDataManager.getInstance());
    }
}
