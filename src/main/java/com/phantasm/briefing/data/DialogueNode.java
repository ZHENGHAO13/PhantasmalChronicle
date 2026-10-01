package com.phantasm.briefing.data;

import com.google.gson.JsonObject;

import java.util.List;

/** Runtime dialogue node produced only from the current DialogueSpec model. */
public record DialogueNode(
        String nodeId,
        String title,
        List<DialogueContentEntry> contents,
        boolean triggerVFX,
        String nextNodeId,
        String autoTrigger,
        DialoguePresentationMode presentationMode,
        List<DialogueOption> options,
        List<JsonObject> conditions,
        List<DialogueAction> completionActions
) {
    public List<String> lines() {
        return contents.stream().filter(DialogueContentEntry::isText).map(DialogueContentEntry::value).toList();
    }
}
