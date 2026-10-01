package com.phantasm.briefing.api.event;

import net.minecraftforge.eventbus.api.Event;

/**
 * Fired on the client event bus whenever the timed dialogue reveal exposes one Unicode code point.
 * Fast-forwarding a line does not emit a burst of events; this hook follows the natural reveal cadence.
 */
public final class DialogueCharacterRevealedEvent extends Event {
    private final String nodeId;
    private final String speakerTitle;
    private final int speakerEntityId;
    private final int lineIndex;
    private final int characterIndex;
    private final String character;

    public DialogueCharacterRevealedEvent(String nodeId, String speakerTitle, int speakerEntityId,
                                          int lineIndex, int characterIndex, String character) {
        this.nodeId = nodeId == null ? "" : nodeId;
        this.speakerTitle = speakerTitle == null ? "" : speakerTitle;
        this.speakerEntityId = speakerEntityId;
        this.lineIndex = Math.max(0, lineIndex);
        this.characterIndex = Math.max(0, characterIndex);
        this.character = character == null ? "" : character;
    }

    public String nodeId() {
        return nodeId;
    }

    public String speakerTitle() {
        return speakerTitle;
    }

    public int speakerEntityId() {
        return speakerEntityId;
    }

    public int lineIndex() {
        return lineIndex;
    }

    public int characterIndex() {
        return characterIndex;
    }

    public String character() {
        return character;
    }
}
