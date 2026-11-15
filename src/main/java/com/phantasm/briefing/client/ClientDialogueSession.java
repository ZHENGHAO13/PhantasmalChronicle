package com.phantasm.briefing.client;

import com.phantasm.briefing.client.vfx.VFXManager;
import com.phantasm.briefing.data.DialogueOption;
import com.phantasm.briefing.network.packet.DialogueOptionSelectedC2SPacket;
import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.network.packet.DialogueNodeFinishedC2SPacket;
import com.phantasm.briefing.network.packet.OpenDialogueNodeS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;

public final class ClientDialogueSession {
    private static final ClientDialogueSession INSTANCE = new ClientDialogueSession();

    private OpenDialogueNodeS2CPacket activeNode;
    private int currentLineIndex;
    private int visibleCharacters;
    private int revealTick;

    private ClientDialogueSession() {
    }

    public static ClientDialogueSession getInstance() {
        return INSTANCE;
    }

    public void openNode(OpenDialogueNodeS2CPacket node) {
        this.activeNode = node;
        this.currentLineIndex = 0;
        this.visibleCharacters = 0;
        this.revealTick = 0;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != null && node.triggerVFX()) {
            VFXManager.getInstance().spawnSuspensionParticles(
                    player.getX(),
                    player.getY() + 1.0D,
                    player.getZ(),
                    64,
                    0x6FB9FF
            );
        }

        // 对话改为 HUD 气泡，不再打开 Screen。这样鼠标捕获、移动、跳跃和战斗输入均保持原样。
    }

    public void completeActiveNode() {
        if (this.activeNode == null) {
            return;
        }

        if (this.activeNode.completionRequired()) {
            ModNetwork.CHANNEL.sendToServer(new DialogueNodeFinishedC2SPacket(this.activeNode.nodeId()));
        }
        this.activeNode = null;
    }

    public void selectOption(String optionId) {
        if (this.activeNode == null || optionId == null || optionId.isBlank()) {
            return;
        }

        ModNetwork.CHANNEL.sendToServer(new DialogueOptionSelectedC2SPacket(this.activeNode.nodeId(), optionId));
        this.activeNode = null;
    }

    public void abortActiveNode() {
        if (this.activeNode == null) {
            return;
        }

        if (this.activeNode.completionRequired()) {
            ModNetwork.CHANNEL.sendToServer(new com.phantasm.briefing.network.packet.DialogueSessionAbortedC2SPacket(this.activeNode.nodeId()));
        }
        this.activeNode = null;
    }

    public boolean isActive() {
        return this.activeNode != null;
    }

    public boolean hasSpeakerEntity() {
        return this.activeNode != null && this.activeNode.speakerEntityId() >= 0;
    }

    public int getSpeakerEntityId() {
        return this.activeNode == null ? -1 : this.activeNode.speakerEntityId();
    }

    public void clear() {
        this.activeNode = null;
        this.currentLineIndex = 0;
        this.visibleCharacters = 0;
        this.revealTick = 0;
    }

    public String getTitle() {
        return this.activeNode == null ? "" : this.activeNode.title();
    }

    public String getVisibleLine() {
        String line = this.getCurrentLine();
        return line.substring(0, Math.min(this.visibleCharacters, line.length()));
    }

    public boolean isAwaitingChoice() {
        return this.activeNode != null
                && this.currentLineIndex >= this.activeNode.lines().size() - 1
                && this.isLineFullyVisible()
                && !this.activeNode.options().isEmpty();
    }

    public List<DialogueOption> getOptions() {
        return this.activeNode == null ? List.of() : this.activeNode.options();
    }

    public void tick() {
        if (this.activeNode == null || this.isLineFullyVisible()) {
            return;
        }
        if (++this.revealTick >= 2) {
            this.revealTick = 0;
            this.visibleCharacters++;
        }
    }

    public void advance() {
        if (this.activeNode == null || this.isAwaitingChoice()) {
            return;
        }
        if (!this.isLineFullyVisible()) {
            this.visibleCharacters = this.getCurrentLine().length();
            return;
        }
        if (this.currentLineIndex + 1 < this.activeNode.lines().size()) {
            this.currentLineIndex++;
            this.visibleCharacters = 0;
            this.revealTick = 0;
        } else {
            this.completeActiveNode();
        }
    }

    public void selectOptionByIndex(int index) {
        if (!this.isAwaitingChoice() || index < 0 || index >= this.activeNode.options().size()) {
            return;
        }
        this.selectOption(this.activeNode.options().get(index).optionId());
    }

    public String getCurrentLine() {
        if (this.activeNode == null || this.activeNode.lines().isEmpty()) {
            return "";
        }
        return this.activeNode.lines().get(Math.min(this.currentLineIndex, this.activeNode.lines().size() - 1));
    }

    public boolean isLineFullyVisible() {
        return this.visibleCharacters >= this.getCurrentLine().length();
    }
}
