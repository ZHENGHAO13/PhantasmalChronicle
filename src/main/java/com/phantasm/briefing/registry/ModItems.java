package com.phantasm.briefing.registry;

import com.phantasm.briefing.PhantasmBriefing;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, PhantasmBriefing.MOD_ID);

    public static final RegistryObject<Item> STATIC_QUEST_NPC_SPAWN_EGG =
            ITEMS.register("static_quest_npc_spawn_egg", () -> new ForgeSpawnEggItem(
                    ModEntityTypes.STATIC_QUEST_NPC,
                    0x5B6673,
                    0xD9C7A1,
                    new Item.Properties()
            ));

    private ModItems() {
    }
}
