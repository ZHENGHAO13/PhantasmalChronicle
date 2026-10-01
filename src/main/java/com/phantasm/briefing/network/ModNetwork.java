package com.phantasm.briefing.network;

import com.phantasm.briefing.PhantasmBriefing;
import com.phantasm.briefing.network.packet.CloseDialogueSessionS2CPacket;
import com.phantasm.briefing.network.packet.AcceptQuestC2SPacket;
import com.phantasm.briefing.network.packet.DialogueNodeFinishedC2SPacket;
import com.phantasm.briefing.network.packet.DialogueOptionSelectedC2SPacket;
import com.phantasm.briefing.network.packet.DialogueSessionAbortedC2SPacket;
import com.phantasm.briefing.network.packet.CloseWalletShopC2SPacket;
import com.phantasm.briefing.network.packet.CompleteManualReadObjectiveC2SPacket;
import com.phantasm.briefing.network.packet.OpenQuestJournalC2SPacket;
import com.phantasm.briefing.network.packet.OpenQuestJournalS2CPacket;
import com.phantasm.briefing.network.packet.OpenQuestTreeS2CPacket;
import com.phantasm.briefing.network.packet.OpenDialogueNodeS2CPacket;
import com.phantasm.briefing.network.packet.OpenWalletScreenS2CPacket;
import com.phantasm.briefing.network.packet.OpenWalletShopS2CPacket;
import com.phantasm.briefing.network.packet.PreviewDialogueVoiceS2CPacket;
import com.phantasm.briefing.network.packet.PurchaseWalletShopOfferC2SPacket;
import com.phantasm.briefing.network.packet.RefreshQuestContentS2CPacket;
import com.phantasm.briefing.network.packet.SetQuestTrackerHudVisibilityS2CPacket;
import com.phantasm.briefing.network.packet.SetTrackedQuestC2SPacket;
import com.phantasm.briefing.network.packet.SyncQuestHintTargetS2CPacket;
import com.phantasm.briefing.network.packet.SyncQuestTrackerS2CPacket;
import com.phantasm.briefing.network.packet.StartNpcDialogueC2SPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "14";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(PhantasmBriefing.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private static int packetId;

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(OpenDialogueNodeS2CPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenDialogueNodeS2CPacket::encode)
                .decoder(OpenDialogueNodeS2CPacket::decode)
                .consumerMainThread(OpenDialogueNodeS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(CloseDialogueSessionS2CPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CloseDialogueSessionS2CPacket::encode)
                .decoder(CloseDialogueSessionS2CPacket::decode)
                .consumerMainThread(CloseDialogueSessionS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(PreviewDialogueVoiceS2CPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PreviewDialogueVoiceS2CPacket::encode)
                .decoder(PreviewDialogueVoiceS2CPacket::decode)
                .consumerMainThread(PreviewDialogueVoiceS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(DialogueNodeFinishedC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(DialogueNodeFinishedC2SPacket::encode)
                .decoder(DialogueNodeFinishedC2SPacket::decode)
                .consumerMainThread(DialogueNodeFinishedC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(DialogueOptionSelectedC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(DialogueOptionSelectedC2SPacket::encode)
                .decoder(DialogueOptionSelectedC2SPacket::decode)
                .consumerMainThread(DialogueOptionSelectedC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(DialogueSessionAbortedC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(DialogueSessionAbortedC2SPacket::encode)
                .decoder(DialogueSessionAbortedC2SPacket::decode)
                .consumerMainThread(DialogueSessionAbortedC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenQuestJournalS2CPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenQuestJournalS2CPacket::encode)
                .decoder(OpenQuestJournalS2CPacket::decode)
                .consumerMainThread(OpenQuestJournalS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(RefreshQuestContentS2CPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RefreshQuestContentS2CPacket::encode)
                .decoder(RefreshQuestContentS2CPacket::decode)
                .consumerMainThread(RefreshQuestContentS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenQuestJournalC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenQuestJournalC2SPacket::encode)
                .decoder(OpenQuestJournalC2SPacket::decode)
                .consumerMainThread(OpenQuestJournalC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(AcceptQuestC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(AcceptQuestC2SPacket::encode)
                .decoder(AcceptQuestC2SPacket::decode)
                .consumerMainThread(AcceptQuestC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(SetTrackedQuestC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetTrackedQuestC2SPacket::encode)
                .decoder(SetTrackedQuestC2SPacket::decode)
                .consumerMainThread(SetTrackedQuestC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(CompleteManualReadObjectiveC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(CompleteManualReadObjectiveC2SPacket::encode)
                .decoder(CompleteManualReadObjectiveC2SPacket::decode)
                .consumerMainThread(CompleteManualReadObjectiveC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenQuestTreeS2CPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenQuestTreeS2CPacket::encode)
                .decoder(OpenQuestTreeS2CPacket::decode)
                .consumerMainThread(OpenQuestTreeS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncQuestTrackerS2CPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncQuestTrackerS2CPacket::encode)
                .decoder(SyncQuestTrackerS2CPacket::decode)
                .consumerMainThread(SyncQuestTrackerS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(SetQuestTrackerHudVisibilityS2CPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SetQuestTrackerHudVisibilityS2CPacket::encode)
                .decoder(SetQuestTrackerHudVisibilityS2CPacket::decode)
                .consumerMainThread(SetQuestTrackerHudVisibilityS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncQuestHintTargetS2CPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncQuestHintTargetS2CPacket::encode)
                .decoder(SyncQuestHintTargetS2CPacket::decode)
                .consumerMainThread(SyncQuestHintTargetS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenWalletScreenS2CPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenWalletScreenS2CPacket::encode)
                .decoder(OpenWalletScreenS2CPacket::decode)
                .consumerMainThread(OpenWalletScreenS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(OpenWalletShopS2CPacket.class, nextId(), NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenWalletShopS2CPacket::encode)
                .decoder(OpenWalletShopS2CPacket::decode)
                .consumerMainThread(OpenWalletShopS2CPacket::handle)
                .add();

        CHANNEL.messageBuilder(PurchaseWalletShopOfferC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(PurchaseWalletShopOfferC2SPacket::encode)
                .decoder(PurchaseWalletShopOfferC2SPacket::decode)
                .consumerMainThread(PurchaseWalletShopOfferC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(CloseWalletShopC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(CloseWalletShopC2SPacket::encode)
                .decoder(CloseWalletShopC2SPacket::decode)
                .consumerMainThread(CloseWalletShopC2SPacket::handle)
                .add();

        CHANNEL.messageBuilder(StartNpcDialogueC2SPacket.class, nextId(), NetworkDirection.PLAY_TO_SERVER)
                .encoder(StartNpcDialogueC2SPacket::encode)
                .decoder(StartNpcDialogueC2SPacket::decode)
                .consumerMainThread(StartNpcDialogueC2SPacket::handle)
                .add();
    }

    private static int nextId() {
        return packetId++;
    }
}
