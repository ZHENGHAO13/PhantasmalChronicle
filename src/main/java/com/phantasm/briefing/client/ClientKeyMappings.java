package com.phantasm.briefing.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class ClientKeyMappings {
    public static final String CATEGORY = "key.categories.phantasmbriefing";

    public static final KeyMapping START_NPC_DIALOGUE = new KeyMapping(
            "key.phantasmbriefing.start_npc_dialogue",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    );

    private ClientKeyMappings() {
    }
}
