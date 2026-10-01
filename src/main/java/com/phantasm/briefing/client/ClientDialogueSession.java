package com.phantasm.briefing.client;

import com.phantasm.briefing.api.event.DialogueCharacterRevealedEvent;
import com.phantasm.briefing.client.screen.DialogueChoiceScreen;
import com.phantasm.briefing.client.ui.DarkFantasyTheme;
import com.phantasm.briefing.client.vfx.VFXManager;
import com.phantasm.briefing.data.DialogueContentEntry;
import com.phantasm.briefing.data.DialogueContentType;
import com.phantasm.briefing.data.DialogueOption;
import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.network.packet.DialogueNodeFinishedC2SPacket;
import com.phantasm.briefing.network.packet.DialogueOptionSelectedC2SPacket;
import com.phantasm.briefing.network.packet.OpenDialogueNodeS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.common.MinecraftForge;

import java.util.List;

public final class ClientDialogueSession {
    private static final ClientDialogueSession INSTANCE = new ClientDialogueSession();

    private OpenDialogueNodeS2CPacket activeNode;
    private int currentContentIndex;
    private int visibleCharacters;
    private int revealTick;

    private ClientDialogueSession() {
    }

    public static ClientDialogueSession getInstance() {
        return INSTANCE;
    }

    public void openNode(OpenDialogueNodeS2CPacket node) {
        this.activeNode = node;
        this.currentContentIndex = 0;
        this.visibleCharacters = 0;
        this.revealTick = 0;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DialogueChoiceScreen) {
            minecraft.setScreen(null);
        }
        LocalPlayer player = minecraft.player;
        if (player != null && node.triggerVFX()) {
            VFXManager.getInstance().spawnSuspensionParticles(
                    player.getX(),
                    player.getY() + 1.0D,
                    player.getZ(),
                    64,
                    DarkFantasyTheme.AMBER
            );
        }
    }

    public void completeActiveNode() {
        if (this.activeNode == null) {
            return;
        }

        if (this.activeNode.completionRequired()) {
            ModNetwork.CHANNEL.sendToServer(new DialogueNodeFinishedC2SPacket(this.activeNode.nodeId()));
        }
        this.activeNode = null;
        closeChoiceScreen();
    }

    public void selectOption(String optionId) {
        if (this.activeNode == null || optionId == null || optionId.isBlank()) {
            return;
        }

        ModNetwork.CHANNEL.sendToServer(new DialogueOptionSelectedC2SPacket(this.activeNode.nodeId(), optionId));
        this.activeNode = null;
        closeChoiceScreen();
    }

    public void abortActiveNode() {
        if (this.activeNode == null) {
            return;
        }

        if (this.activeNode.completionRequired()) {
            ModNetwork.CHANNEL.sendToServer(new com.phantasm.briefing.network.packet.DialogueSessionAbortedC2SPacket(this.activeNode.nodeId()));
        }
        this.activeNode = null;
        closeChoiceScreen();
    }

    public boolean isActive() {
        return this.activeNode != null;
    }

    public boolean hasSpeakerEntity() {
        return this.activeNode != null && this.activeNode.speakerEntityId() >= 0;
    }

    public boolean clearIfSpeakerMissing(Minecraft minecraft) {
        if (!this.isActive() || !this.hasSpeakerEntity() || minecraft == null || minecraft.level == null) {
            return false;
        }
        var speaker = minecraft.level.getEntity(this.getSpeakerEntityId());
        if (speaker != null && speaker.isAlive()) {
            return false;
        }
        this.abortActiveNode();
        return true;
    }

    public int getSpeakerEntityId() {
        return this.activeNode == null ? -1 : this.activeNode.speakerEntityId();
    }

    public void clear() {
        this.activeNode = null;
        this.currentContentIndex = 0;
        this.visibleCharacters = 0;
        this.revealTick = 0;
        closeChoiceScreen();
    }

    private static void closeChoiceScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DialogueChoiceScreen) {
            minecraft.setScreen(null);
        }
    }

    public String getTitle() {
        return this.activeNode == null ? "" : this.activeNode.title();
    }

    public DialogueContentEntry getCurrentContent() {
        if (this.activeNode == null || this.activeNode.contents().isEmpty()) {
            return DialogueContentEntry.text("");
        }
        return this.activeNode.contents().get(Math.min(this.currentContentIndex, this.activeNode.contents().size() - 1));
    }

    public int getCurrentContentIndex() {
        return this.currentContentIndex;
    }

    public boolean isImageContent() {
        return this.getCurrentContent().type() == DialogueContentType.IMAGE;
    }

    public String getCurrentImage() {
        return this.isImageContent() ? this.getCurrentContent().value() : "";
    }

    public String getVisibleLine() {
        String line = this.getCurrentLine();
        return line.substring(0, Math.min(this.visibleCharacters, line.length()));
    }

    public boolean isAwaitingChoice() {
        return this.activeNode != null
                && this.currentContentIndex >= this.activeNode.contents().size() - 1
                && this.isLineFullyVisible()
                && !this.activeNode.options().isEmpty();
    }

    public List<DialogueOption> getOptions() {
        return this.activeNode == null ? List.of() : this.activeNode.options();
    }

    public void tick() {
        if (this.activeNode == null || this.isImageContent() || this.isLineFullyVisible()) {
            return;
        }
        if (++this.revealTick >= 2) {
            this.revealTick = 0;
            String line = this.getCurrentLine();
            int start = Math.min(this.visibleCharacters, line.length());
            if (start >= line.length()) return;
            int codePoint = line.codePointAt(start);
            String revealed = new String(Character.toChars(codePoint));
            this.visibleCharacters = Math.min(line.length(), start + Character.charCount(codePoint));
            DialogueTypewriterSoundPlayer.play(
                    revealed,
                    start,
                    this.activeNode.typewriterSoundEnabled(),
                    this.activeNode.typewriterSoundEventId(),
                    this.activeNode.typewriterSoundVolume()
            );
            MinecraftForge.EVENT_BUS.post(new DialogueCharacterRevealedEvent(
                    this.activeNode.nodeId(), this.activeNode.title(), this.activeNode.speakerEntityId(),
                    this.currentContentIndex, start, revealed));
        }
    }

    public void advance() {
        if (this.activeNode == null || this.isAwaitingChoice()) {
            return;
        }
        if (!this.isImageContent() && !this.isLineFullyVisible()) {
            this.visibleCharacters = this.getCurrentLine().length();
            return;
        }
        if (this.currentContentIndex + 1 < this.activeNode.contents().size()) {
            this.currentContentIndex++;
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
        DialogueContentEntry content = this.getCurrentContent();
        return content.isText() ? content.value() : "";
    }

    public boolean isLineFullyVisible() {
        return this.isImageContent() || this.visibleCharacters >= this.getCurrentLine().length();
    }
}
