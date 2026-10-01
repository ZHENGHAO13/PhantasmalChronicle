from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(rel):
    return (ROOT / rel).read_text(encoding='utf-8')

checks = []

def check(name, condition):
    checks.append((name, bool(condition)))

player = read('src/main/java/com/phantasm/briefing/service/BriefingPlayerData.java')
protection = read('src/main/java/com/phantasm/briefing/event/QuestNpcProtectionEvents.java')
bindings = read('src/main/java/com/phantasm/briefing/data/NpcBindingDataManager.java')
quests = read('src/main/java/com/phantasm/briefing/data/QuestDataManager.java')
hints = read('src/main/java/com/phantasm/briefing/service/QuestEntityHintService.java')
hotreload = read('src/main/java/com/phantasm/briefing/event/EditorHotReloadEvents.java')
player_events = read('src/main/java/com/phantasm/briefing/event/PlayerDataEvents.java')
registry_loader = read('src/main/java/com/phantasm/briefing/data/JsonRegistryLoader.java')
runtime = read('src/main/java/com/phantasm/briefing/service/QuestRuntimeService.java')
dialogue_events = read('src/main/java/com/phantasm/briefing/event/DialogueSessionEvents.java')

check('read-only quest state has non-creating accessor', 'questIfPresent' in player)
check('quest active read uses non-creating accessor', 'isQuestActive' in player and 'questIfPresent(player, questId)' in player)
check('npc binding lookup is entity-type indexed', 'bindingsByEntityType' in bindings)
check('structure spawn bindings are cached', 'structureSpawnBindings' in bindings and 'return this.structureSpawnBindings;' in bindings)
check('living tick has cheap candidate guard', 'mayMatchEntityType' in protection)
check('quest manager has node-target index', 'questsByTargetNode' in quests and 'getQuestsTargetingNode' in quests)
check('hint service uses node-target index', 'getQuestsTargetingNode(nodeId)' in hints)
check('hot reload avoids global reloadResources', 'server.reloadResources' not in hotreload)
check('local ids resolve both namespaced and short forms', 'PhantasmBriefing.MOD_ID + ":" + id' in registry_loader)
check('automatic collection checks build one inventory count cache', 'buildInventoryCounts' in runtime and 'inventoryCounts' in runtime)
check('shop/hint transient state is cleared on logout', 'DialogueShopService.clear' in player_events and 'QuestEntityHintService.clear' in (player_events + dialogue_events))

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(('PASS' if ok else 'FAIL') + ': ' + name)

if failed:
    raise SystemExit(f'{len(failed)} invariant(s) failed')
