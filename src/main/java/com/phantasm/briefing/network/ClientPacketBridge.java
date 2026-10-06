package com.phantasm.briefing.network;

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

/**
 * Common-side bridge for clientbound packet execution.
 *
 * <p>The dedicated server loads packet classes while registering the network channel, so those
 * classes must not resolve Minecraft client GUI types. The physical client installs the real
 * handler during client setup; the dedicated server never invokes the bridge and never loads the
 * client implementation.</p>
 */
public final class ClientPacketBridge {
    private static volatile Handler handler;

    private ClientPacketBridge() {
    }

    public static void install(Handler clientHandler) {
        if (clientHandler == null) {
            throw new IllegalArgumentException("Client packet handler must not be null");
        }
        handler = clientHandler;
    }

    public static void handle(OpenDialogueNodeS2CPacket packet) {
        requireHandler().handle(packet);
    }

    public static void handle(CloseDialogueSessionS2CPacket packet) {
        requireHandler().handle(packet);
    }

    public static void handle(PreviewDialogueVoiceS2CPacket packet) {
        requireHandler().handle(packet);
    }

    public static void handle(OpenQuestJournalS2CPacket packet) {
        requireHandler().handle(packet);
    }

    public static void handle(RefreshQuestContentS2CPacket packet) {
        requireHandler().handle(packet);
    }

    public static void handle(OpenQuestTreeS2CPacket packet) {
        requireHandler().handle(packet);
    }

    public static void handle(SyncQuestTrackerS2CPacket packet) {
        requireHandler().handle(packet);
    }

    public static void handle(SetQuestTrackerHudVisibilityS2CPacket packet) {
        requireHandler().handle(packet);
    }

    public static void handle(SyncQuestHintTargetS2CPacket packet) {
        requireHandler().handle(packet);
    }

    public static void handle(OpenWalletScreenS2CPacket packet) {
        requireHandler().handle(packet);
    }

    public static void handle(OpenWalletShopS2CPacket packet) {
        requireHandler().handle(packet);
    }

    private static Handler requireHandler() {
        Handler installed = handler;
        if (installed == null) {
            throw new IllegalStateException("Client packet handler is not installed");
        }
        return installed;
    }

    public interface Handler {
        default void handle(OpenDialogueNodeS2CPacket packet) {
        }

        default void handle(CloseDialogueSessionS2CPacket packet) {
        }

        default void handle(PreviewDialogueVoiceS2CPacket packet) {
        }

        default void handle(OpenQuestJournalS2CPacket packet) {
        }

        default void handle(RefreshQuestContentS2CPacket packet) {
        }

        default void handle(OpenQuestTreeS2CPacket packet) {
        }

        default void handle(SyncQuestTrackerS2CPacket packet) {
        }

        default void handle(SetQuestTrackerHudVisibilityS2CPacket packet) {
        }

        default void handle(SyncQuestHintTargetS2CPacket packet) {
        }

        default void handle(OpenWalletScreenS2CPacket packet) {
        }

        default void handle(OpenWalletShopS2CPacket packet) {
        }
    }
}
