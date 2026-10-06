package com.phantasm.briefing.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class PhantasmBriefingClientConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue QUEST_TRACKING_ENABLED;
    private static final ForgeConfigSpec.BooleanValue QUEST_TRACKER_SHOW_OBJECTIVE_TRACKING_TEXT;
    private static final ForgeConfigSpec.BooleanValue QUEST_TRACKER_ANCHOR_RIGHT;
    private static final ForgeConfigSpec.BooleanValue WORLD_MARKER_ENABLED;
    private static final ForgeConfigSpec.BooleanValue NPC_INDICATOR_ENABLED;
    private static final ForgeConfigSpec.BooleanValue MARKER_DISTANCE_ENABLED;
    private static final ForgeConfigSpec.EnumValue<DialogueHudStyle> DIALOGUE_HUD_STYLE;
    private static final ForgeConfigSpec.BooleanValue DIALOGUE_GAZE_BUBBLE_STYLE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("quest_tracker");
        QUEST_TRACKING_ENABLED = builder
                .comment("任务追踪总开关；关闭后隐藏任务面板、世界目标指引与 NPC 任务标记。")
                .define("enabled", true);
        QUEST_TRACKER_SHOW_OBJECTIVE_TRACKING_TEXT = builder
                .comment("是否显示阶段目标追踪文字；目标名称始终显示。")
                .define("show_objective_tracking_text", false);
        QUEST_TRACKER_ANCHOR_RIGHT = builder
                .comment("任务追踪面板位置：false = 左上角；true = 右上角。")
                .define("anchor_right", false);
        WORLD_MARKER_ENABLED = builder
                .comment("是否显示当前任务的世界坐标或结构方向指引。")
                .define("world_marker_enabled", true);
        NPC_INDICATOR_ENABLED = builder
                .comment("是否显示可接取、可推进、可交付 NPC 的方向标记。")
                .define("npc_indicator_enabled", true);
        MARKER_DISTANCE_ENABLED = builder
                .comment("是否在世界目标与 NPC 标记旁显示距离。")
                .define("show_marker_distance", true);
        builder.pop();

        builder.push("dialogue");
        DIALOGUE_HUD_STYLE = builder
                .comment("对话显示风格：DIALOGUE_BAR、PORTRAIT_BUBBLE 或 MAID_WORLD_BUBBLE。")
                .defineEnum("display_style", DialogueHudStyle.DIALOGUE_BAR);
        DIALOGUE_GAZE_BUBBLE_STYLE = builder
                .comment("旧版头像气泡开关，仅用于自动迁移旧客户端配置。")
                .define("gaze_bubble_style", false);
        builder.pop();

        SPEC = builder.build();
    }

    private PhantasmBriefingClientConfig() {
    }

    public static boolean questTrackingEnabled() {
        return QUEST_TRACKING_ENABLED.get();
    }

    public static boolean questTrackerShowObjectiveTrackingText() {
        return QUEST_TRACKER_SHOW_OBJECTIVE_TRACKING_TEXT.get();
    }

    public static boolean questTrackerAnchorRight() {
        return QUEST_TRACKER_ANCHOR_RIGHT.get();
    }

    public static boolean worldMarkerEnabled() {
        return WORLD_MARKER_ENABLED.get();
    }

    public static boolean npcIndicatorEnabled() {
        return NPC_INDICATOR_ENABLED.get();
    }

    public static boolean markerDistanceEnabled() {
        return MARKER_DISTANCE_ENABLED.get();
    }

    public static DialogueHudStyle dialogueHudStyle() {
        DialogueHudStyle configuredStyle = DIALOGUE_HUD_STYLE.get();
        if (configuredStyle == DialogueHudStyle.DIALOGUE_BAR && DIALOGUE_GAZE_BUBBLE_STYLE.get()) {
            return DialogueHudStyle.PORTRAIT_BUBBLE;
        }
        return configuredStyle;
    }

    public static void save(
            boolean questTrackingEnabled,
            boolean questTrackerShowObjectiveTrackingText,
            boolean questTrackerAnchorRight,
            boolean worldMarkerEnabled,
            boolean npcIndicatorEnabled,
            boolean markerDistanceEnabled,
            DialogueHudStyle dialogueHudStyle
    ) {
        QUEST_TRACKING_ENABLED.set(questTrackingEnabled);
        QUEST_TRACKER_SHOW_OBJECTIVE_TRACKING_TEXT.set(questTrackerShowObjectiveTrackingText);
        QUEST_TRACKER_ANCHOR_RIGHT.set(questTrackerAnchorRight);
        WORLD_MARKER_ENABLED.set(worldMarkerEnabled);
        NPC_INDICATOR_ENABLED.set(npcIndicatorEnabled);
        MARKER_DISTANCE_ENABLED.set(markerDistanceEnabled);
        DIALOGUE_HUD_STYLE.set(dialogueHudStyle);
        DIALOGUE_GAZE_BUBBLE_STYLE.set(false);
        SPEC.save();
    }
}
