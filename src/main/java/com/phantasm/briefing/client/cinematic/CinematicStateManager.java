package com.phantasm.briefing.client.cinematic;

import com.phantasm.briefing.client.ClientDialogueSession;
import com.phantasm.briefing.client.vfx.VFXManager;
import com.phantasm.briefing.client.screen.DialogueOverlayScreen;
import com.phantasm.briefing.config.PhantasmBriefingClientConfig;
import com.phantasm.briefing.data.DialogueOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class CinematicStateManager {
    private static final CinematicStateManager INSTANCE = new CinematicStateManager();

    private final List<String> dialogueNodes = new ArrayList<>();
    private final List<DialogueOption> activeOptions = new ArrayList<>();

    private String activeNodeId = "";
    private boolean cinematicModeActive;
    private boolean awaitingOptionSelection;
    private int currentNodeIndex;
    private float lockedYaw;
    private float lockedPitch;
    private float targetYaw;
    private float targetPitch;
    private boolean smoothLookActive;

    private CinematicStateManager() {
    }

    public static CinematicStateManager getInstance() {
        return INSTANCE;
    }

    public boolean isCinematicModeActive() {
        return this.cinematicModeActive;
    }

    public void startCinematic(String nodeId, List<String> dialogueSequence, List<DialogueOption> options) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || dialogueSequence == null || dialogueSequence.isEmpty()) {
            return;
        }

        this.activeNodeId = nodeId == null ? "" : nodeId;
        this.dialogueNodes.clear();
        this.dialogueNodes.addAll(dialogueSequence);
        this.activeOptions.clear();
        if (options != null) {
            this.activeOptions.addAll(options);
        }
        this.currentNodeIndex = 0;
        this.cinematicModeActive = true;
        this.awaitingOptionSelection = false;
        this.lockedYaw = player.getYRot();
        this.lockedPitch = player.getXRot();
        this.targetYaw = this.lockedYaw;
        this.targetPitch = this.lockedPitch;
        this.smoothLookActive = false;
        this.ensureOverlayOpen();
    }

    public void endCinematicMode() {
        this.activeNodeId = "";
        this.dialogueNodes.clear();
        this.activeOptions.clear();
        this.currentNodeIndex = 0;
        this.cinematicModeActive = false;
        this.awaitingOptionSelection = false;
        this.smoothLookActive = false;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DialogueOverlayScreen) {
            minecraft.setScreen(null);
        }
    }

    public void handleEscapeExit() {
        if (!this.cinematicModeActive) {
            return;
        }

        if (PhantasmBriefingClientConfig.cinematicEscSkipsNode()) {
            ClientDialogueSession.getInstance().completeActiveNode();
        } else {
            ClientDialogueSession.getInstance().abortActiveNode();
        }

        this.endCinematicMode();
        VFXManager.getInstance().clear();
    }

    public void requestAdvance() {
        if (!this.cinematicModeActive) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof DialogueOverlayScreen overlayScreen)) {
            this.ensureOverlayOpen();
            return;
        }

        if (!overlayScreen.isCurrentNodeComplete()) {
            overlayScreen.revealCurrentNode();
            return;
        }

        if (this.awaitingOptionSelection) {
            return;
        }

        this.nextNode();
    }

    public void nextNode() {
        if (!this.cinematicModeActive) {
            return;
        }

        this.currentNodeIndex++;
        if (this.currentNodeIndex >= this.dialogueNodes.size()) {
            if (!this.activeOptions.isEmpty()) {
                this.currentNodeIndex = this.dialogueNodes.size() - 1;
                this.awaitingOptionSelection = true;
                this.ensureOverlayOpen();
                if (Minecraft.getInstance().screen instanceof DialogueOverlayScreen overlayScreen) {
                    overlayScreen.syncFromManager();
                }
                return;
            }
            if (!this.activeNodeId.isBlank()) {
                ClientDialogueSession.getInstance().completeActiveNode();
            }
            this.endCinematicMode();
            return;
        }

        this.ensureOverlayOpen();
        if (Minecraft.getInstance().screen instanceof DialogueOverlayScreen overlayScreen) {
            overlayScreen.syncFromManager();
        }
    }

    public void setSmoothLookTarget(float yaw, float pitch) {
        this.targetYaw = yaw;
        this.targetPitch = pitch;
        this.smoothLookActive = true;
    }

    public void freezeCurrentLookTarget() {
        this.targetYaw = this.lockedYaw;
        this.targetPitch = this.lockedPitch;
        this.smoothLookActive = false;
    }

    public void tick() {
        if (!this.cinematicModeActive) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            this.endCinematicMode();
            return;
        }

        if (!(minecraft.screen instanceof DialogueOverlayScreen)) {
            this.ensureOverlayOpen();
        }

        if (this.smoothLookActive) {
            this.lockedYaw = Mth.rotLerp(0.2F, this.lockedYaw, this.targetYaw);
            this.lockedPitch = Mth.lerp(0.2F, this.lockedPitch, this.targetPitch);
        }

        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.zza = 0.0F;
        player.xxa = 0.0F;
        player.setSprinting(false);

        if (player.input != null) {
            player.input.leftImpulse = 0.0F;
            player.input.forwardImpulse = 0.0F;
            player.input.jumping = false;
            player.input.shiftKeyDown = false;
        }

        player.setYRot(this.lockedYaw);
        player.setYHeadRot(this.lockedYaw);
        player.setYBodyRot(this.lockedYaw);
        player.setXRot(this.lockedPitch);
    }

    public float getLockedYaw() {
        return this.lockedYaw;
    }

    public float getLockedPitch() {
        return this.lockedPitch;
    }

    public int getCurrentNodeIndex() {
        return this.currentNodeIndex;
    }

    public String getCurrentNodeText() {
        if (this.currentNodeIndex < 0 || this.currentNodeIndex >= this.dialogueNodes.size()) {
            return "";
        }
        return this.dialogueNodes.get(this.currentNodeIndex);
    }

    public boolean hasNextNode() {
        return this.currentNodeIndex + 1 < this.dialogueNodes.size();
    }

    public boolean isAwaitingOptionSelection() {
        return this.awaitingOptionSelection;
    }

    public List<DialogueOption> getActiveOptions() {
        return List.copyOf(this.activeOptions);
    }

    public void selectOptionByIndex(int optionIndex) {
        if (!this.cinematicModeActive || !this.awaitingOptionSelection) {
            return;
        }
        if (optionIndex < 0 || optionIndex >= this.activeOptions.size()) {
            return;
        }

        ClientDialogueSession.getInstance().selectOption(this.activeOptions.get(optionIndex).optionId());
        this.endCinematicMode();
        VFXManager.getInstance().clear();
    }

    @Nullable
    public DialogueOverlayScreen getDialogueOverlayScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.screen instanceof DialogueOverlayScreen overlayScreen ? overlayScreen : null;
    }

    private void ensureOverlayOpen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof DialogueOverlayScreen overlayScreen)) {
            minecraft.setScreen(new DialogueOverlayScreen());
        } else {
            overlayScreen.syncFromManager();
        }
    }
}
