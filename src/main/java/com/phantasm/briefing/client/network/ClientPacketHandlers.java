package com.phantasm.briefing.client.network;

import com.phantasm.briefing.client.ClientDialogueSession;
import com.phantasm.briefing.client.ContentTextureCache;
import com.phantasm.briefing.client.DialogueTypewriterSoundPlayer;
import com.phantasm.briefing.client.QuestTrackerClientState;
import com.phantasm.briefing.client.screen.QuestJournalScreen;
import com.phantasm.briefing.client.screen.QuestTreeScreen;
import com.phantasm.briefing.client.screen.WalletScreen;
import com.phantasm.briefing.client.screen.WalletShopScreen;
import com.phantasm.briefing.data.QuestTreeSnapshot;
import com.phantasm.briefing.network.ClientPacketBridge;
import com.phantasm.briefing.network.packet.CloseDialogueSessionS2CPacket;
import com.phantasm.briefing.network.packet.OpenDialogueNodeS2CPacket;
import com.phantasm.briefing.network.packet.OpenQuestJournalS2CPacket;
import com.phantasm.briefing.network.packet.OpenQuestTreeS2CPacket;
import com.phantasm.briefing.network.packet.OpenWalletScreenS2CPacket;
import com.phantasm.briefing.network.packet.OpenWalletShopS2CPacket;
import com.phantasm.briefing.network.packet.PreviewDialogueVoiceS2CPacket;
import com.phantasm.briefing.network.packet.RefreshQuestContentS2CPacket;
import com.phantasm.briefing.network.packet.SetQuestTrackerHudVisibilityS2CPacket;
import com.phantasm.briefing.network.packet.SyncQuestHintTargetS2CPacket;
import com.phantasm.briefing.network.packet.SyncQuestTrackerS2CPacket;
import net.minecraft.client.Minecraft;

/**
 * Client-only execution for S2C packets. Packet definitions remain dedicated-server safe:
 * codecs and registration never resolve Minecraft GUI classes on the physical server.
 */
public final class ClientPacketHandlers implements ClientPacketBridge.Handler {
    public ClientPacketHandlers() {
    }

    @Override
    public void handle(OpenDialogueNodeS2CPacket packet) {
        ClientDialogueSession.getInstance().openNode(packet);
    }

    @Override
    public void handle(CloseDialogueSessionS2CPacket packet) {
        ClientDialogueSession.getInstance().clear();
    }

    @Override
    public void handle(PreviewDialogueVoiceS2CPacket packet) {
        DialogueTypewriterSoundPlayer.playPreview(packet.soundEventId(), packet.volume());
    }

    @Override
    public void handle(OpenQuestJournalS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        QuestJournalScreen current = minecraft.screen instanceof QuestJournalScreen journal ? journal : null;
        ContentTextureCache.clear();
        QuestJournalScreen next = new QuestJournalScreen(packet.entries(), packet.manuals());
        if (current != null) {
            next.restoreStateFrom(current);
        }
        if (packet.autoOpenTarget() != null) {
            next.openManualAutomatically(packet.autoOpenTarget());
        }
        minecraft.setScreen(next);
    }

    @Override
    public void handle(RefreshQuestContentS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof QuestJournalScreen current) {
            ContentTextureCache.clear();
            QuestJournalScreen next = new QuestJournalScreen(
                    packet.journal().entries(),
                    packet.journal().manuals()
            );
            next.restoreStateFrom(current);
            minecraft.setScreen(next);
            return;
        }
        if (minecraft.screen instanceof QuestTreeScreen) {
            minecraft.setScreen(new QuestTreeScreen(
                    new QuestTreeSnapshot(packet.tree().nodes(), packet.tree().edges())
            ));
        }
    }

    @Override
    public void handle(OpenQuestTreeS2CPacket packet) {
        Minecraft.getInstance().setScreen(new QuestTreeScreen(
                new QuestTreeSnapshot(packet.nodes(), packet.edges())
        ));
    }

    @Override
    public void handle(SyncQuestTrackerS2CPacket packet) {
        QuestTrackerClientState.setTrackedEntry(packet.entry());
        QuestTrackerClientState.setManualReadPrompt(packet.manualReadPrompt());
    }

    @Override
    public void handle(SetQuestTrackerHudVisibilityS2CPacket packet) {
        QuestTrackerClientState.setTrackerHudEnabled(packet.enabled());
    }

    @Override
    public void handle(SyncQuestHintTargetS2CPacket packet) {
        QuestTrackerClientState.setHints(packet.targets());
    }

    @Override
    public void handle(OpenWalletScreenS2CPacket packet) {
        Minecraft.getInstance().setScreen(new WalletScreen(packet.entries()));
    }

    @Override
    public void handle(OpenWalletShopS2CPacket packet) {
        Minecraft.getInstance().setScreen(new WalletShopScreen(
                packet.shopId(),
                packet.shopTitle(),
                packet.shopSubtitle(),
                packet.balanceLabel(),
                packet.balanceIconItemId(),
                packet.offers()
        ));
    }
}
