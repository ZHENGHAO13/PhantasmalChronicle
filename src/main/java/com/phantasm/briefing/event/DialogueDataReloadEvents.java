package com.phantasm.briefing.event;

import com.phantasm.briefing.data.DialogueDataManager;
import com.phantasm.briefing.data.NpcBindingDataManager;
import com.phantasm.briefing.data.QuestDataManager;
import com.phantasm.briefing.data.ShopDataManager;
import com.phantasm.briefing.data.TradeDataManager;
import com.phantasm.briefing.data.WalletDataManager;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class DialogueDataReloadEvents {

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(DialogueDataManager.getInstance());
        event.addListener(NpcBindingDataManager.getInstance());
        event.addListener(QuestDataManager.getInstance());
        event.addListener(ShopDataManager.getInstance());
        event.addListener(TradeDataManager.getInstance());
        event.addListener(WalletDataManager.getInstance());
    }
}
