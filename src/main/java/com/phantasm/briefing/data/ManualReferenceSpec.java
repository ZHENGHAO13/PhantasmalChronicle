package com.phantasm.briefing.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** Current address of a manual, optionally a particular lesson and page. */
public record ManualReferenceSpec(String manualId, String lessonId, String pageId) {
    public static List<ManualReferenceSpec> read(JsonObject json) {
        if (!json.has("manualRefs") || !json.get("manualRefs").isJsonArray()) return List.of();
        List<ManualReferenceSpec> result = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("manualRefs")) {
            if (!element.isJsonObject()) continue;
            JsonObject raw = element.getAsJsonObject();
            String id = raw.has("manualId") ? raw.get("manualId").getAsString().trim() : "";
            String lesson = raw.has("lessonId") ? raw.get("lessonId").getAsString().trim() : "";
            String page = raw.has("pageId") ? raw.get("pageId").getAsString().trim() : "";
            ManualReferenceSpec ref = new ManualReferenceSpec(id, lesson, page);
            if (!id.isBlank() && !result.contains(ref)) result.add(ref);
        }
        return List.copyOf(result);
    }
}
