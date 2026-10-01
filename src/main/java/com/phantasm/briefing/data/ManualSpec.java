package com.phantasm.briefing.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.phantasm.briefing.data.JsonDataUtil.readText;

/** A named handbook with hierarchical groups and independently unlockable lessons. */
public record ManualSpec(
        String manualId,
        String title,
        String description,
        String category,
        String cover,
        int sortOrder,
        List<ManualNodeSpec> sections
) {
    public ManualSpec {
        sections = List.copyOf(sections);
    }

    public List<ManualNodeSpec> lessons() {
        List<ManualNodeSpec> result = new ArrayList<>();
        for (ManualNodeSpec section : sections) result.addAll(section.lessons());
        return List.copyOf(result);
    }

    public ManualNodeSpec findLesson(String lessonId, String pageId) {
        if (lessonId != null && !lessonId.isBlank()) {
            return lessons().stream().filter(node -> node.nodeId().equals(lessonId)).findFirst().orElse(null);
        }
        if (pageId != null && !pageId.isBlank()) {
            return lessons().stream().filter(node -> node.pages().stream().anyMatch(page -> page.pageId().equals(pageId)))
                    .findFirst().orElse(null);
        }
        return lessons().stream().findFirst().orElse(null);
    }

    public static ManualSpec fromJson(@Nonnull String source, @Nonnull JsonObject json) {
        String id = GsonHelper.getAsString(json, "manualId", "").trim();
        String title = readText(json, "title");
        if (id.isBlank() || title.isBlank()) throw new IllegalArgumentException("Manual ID/title is blank: " + source);
        String cover = GsonHelper.getAsString(json, "coverImage", "").trim();
        if (!cover.isBlank() && ContentImagePath.normalize(cover).isBlank())
            throw new IllegalArgumentException("Invalid manual cover: " + source);

        List<ManualNodeSpec> sections = new ArrayList<>();
        Set<String> nodeIds = new HashSet<>();
        Set<String> pageIds = new HashSet<>();
        int[] budget = {0};
        if (!json.has("sections") || !json.get("sections").isJsonArray()) {
            throw new IllegalArgumentException("Manual sections are missing: " + source);
        }
        for (JsonElement element : json.getAsJsonArray("sections")) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Invalid manual outline: " + source);
            sections.add(readNode(source, element.getAsJsonObject(), 0, nodeIds, pageIds, budget));
        }
        ManualSpec result = new ManualSpec(id, title,
                readText(json, "description"),
                GsonHelper.getAsString(json, "category", ""),
                ContentImagePath.normalize(cover),
                GsonHelper.getAsInt(json, "sortOrder", 0), sections);
        if (result.lessons().isEmpty()) throw new IllegalArgumentException("Manual has no lesson: " + source);
        return result;
    }

    private static ManualNodeSpec readNode(String source, JsonObject json, int depth,
                                           Set<String> nodeIds, Set<String> pageIds, int[] budget) {
        if (++budget[0] > 256 || depth > 3) throw new IllegalArgumentException("Manual outline exceeds limits: " + source);
        String type = GsonHelper.getAsString(json, "type", "lesson");
        String nodeId = GsonHelper.getAsString(json, "nodeId", "").trim();
        String title = readText(json, "title");
        if (!nodeId.matches("[A-Za-z0-9_-]+") || !nodeIds.add(nodeId) || title.isBlank())
            throw new IllegalArgumentException("Duplicate/invalid manual node: " + source + " " + nodeId);
        if ("group".equals(type)) {
            if (depth >= 3) throw new IllegalArgumentException("Manual group depth exceeds three: " + source);
            List<ManualNodeSpec> children = new ArrayList<>();
            if (!json.has("children") || !json.get("children").isJsonArray())
                throw new IllegalArgumentException("Manual group missing children: " + source);
            for (JsonElement element : json.getAsJsonArray("children")) {
                if (!element.isJsonObject()) throw new IllegalArgumentException("Invalid manual child: " + source);
                children.add(readNode(source, element.getAsJsonObject(), depth + 1, nodeIds, pageIds, budget));
            }
            return new ManualNodeSpec("group", nodeId, title, children, List.of(), ManualUnlockType.ALWAYS, "", "", "");
        }
        if (!"lesson".equals(type)) throw new IllegalArgumentException("Unknown manual node type: " + source);
        ManualUnlockType unlockType = ManualUnlockType.ALWAYS;
        String questId = "", phaseId = "", objectiveId = "";
        if (json.has("unlock") && json.get("unlock").isJsonObject()) {
            JsonObject unlock = json.getAsJsonObject("unlock");
            unlockType = ManualUnlockType.fromSerializedName(GsonHelper.getAsString(unlock, "type", "always"));
            questId = GsonHelper.getAsString(unlock, "questId", "").trim();
            phaseId = GsonHelper.getAsString(unlock, "phaseId", "").trim();
            objectiveId = GsonHelper.getAsString(unlock, "objectiveId", "").trim();
        }
        if (unlockType != ManualUnlockType.ALWAYS && (questId.isBlank() || phaseId.isBlank()))
            throw new IllegalArgumentException("Incomplete lesson unlock condition: " + source);
        if ((unlockType == ManualUnlockType.OBJECTIVE_COMPLETED || unlockType == ManualUnlockType.OBJECTIVE_ACTIVE)
                && objectiveId.isBlank())
            throw new IllegalArgumentException("Lesson objective unlock condition is incomplete: " + source);
        if (!json.has("pages") || !json.get("pages").isJsonArray())
            throw new IllegalArgumentException("Lesson missing pages: " + source);
        return new ManualNodeSpec("lesson", nodeId, title, List.of(),
                readPages(source, json.getAsJsonArray("pages"), pageIds), unlockType, questId, phaseId, objectiveId);
    }

    private static List<ManualPageSpec> readPages(String source, JsonArray raw, Set<String> pageIds) {
        if (raw.isEmpty() || raw.size() > 50) throw new IllegalArgumentException("Lesson page count invalid: " + source);
        List<ManualPageSpec> pages = new ArrayList<>();
        for (JsonElement element : raw) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Invalid lesson page: " + source);
            JsonObject json = element.getAsJsonObject();
            String pageId = GsonHelper.getAsString(json, "pageId", "page_" + (pages.size() + 1)).trim();
            String image = GsonHelper.getAsString(json, "image", "").trim();
            if (!pageId.matches("[A-Za-z0-9_-]+") || !pageIds.add(pageId))
                throw new IllegalArgumentException("Duplicate/invalid manual page ID: " + source + " " + pageId);
            if (!image.isBlank() && ContentImagePath.normalize(image).isBlank())
                throw new IllegalArgumentException("Invalid manual image: " + source + " " + image);
            ManualPageSpec page = new ManualPageSpec(pageId,
                    readText(json, "title"),
                    readText(json, "text"), image);
            if (!page.hasContent()) throw new IllegalArgumentException("Empty manual page: " + source + " " + pageId);
            pages.add(page);
        }
        return List.copyOf(pages);
    }
}
