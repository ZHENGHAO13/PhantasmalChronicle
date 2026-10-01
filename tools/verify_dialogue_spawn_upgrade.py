from pathlib import Path

ROOT = Path('/mnt/data/pc_work')

def read(rel):
    return (ROOT / rel).read_text(encoding='utf-8')

checks = []
def check(name, cond):
    checks.append((name, bool(cond)))

cond = read('src/main/java/com/phantasm/briefing/service/BriefingConditionService.java')
spawn_spec = read('src/main/java/com/phantasm/briefing/data/NpcStructureSpawnSpec.java')
spawn_service = read('src/main/java/com/phantasm/briefing/service/NpcStructureSpawnService.java')
spawn_events = read('src/main/java/com/phantasm/briefing/event/NpcStructureSpawnEvents.java')
flow = read('src/main/java/com/phantasm/briefing/service/DialogueFlowCoordinator.java')
dialogue_spec = read('src/main/java/com/phantasm/briefing/data/DialogueSpec.java')
packet = read('src/main/java/com/phantasm/briefing/network/packet/StartNpcDialogueC2SPacket.java')
progress_events = read('src/main/java/com/phantasm/briefing/event/QuestObjectiveProgressEvents.java')
keys = read('src/main/java/com/phantasm/briefing/client/ClientKeyMappings.java')
client_events = read('src/main/java/com/phantasm/briefing/client/DialogueInteractionClientEvents.java')
network = read('src/main/java/com/phantasm/briefing/network/ModNetwork.java')
quest_runtime = read('src/main/java/com/phantasm/briefing/service/QuestRuntimeService.java')
flags = read('src/main/java/com/phantasm/briefing/service/PlayerFlagService.java')
html = read('index.html')

check('PB conditions are explicit', 'pb_quest_completed' in cond and 'pb_quest_phase' in cond and 'pb_quest_not_started' in cond)
check('FTB conditions are explicit', 'ftb_quest_completed' in cond and 'ftb_quest_not_completed' in cond)
check('spawn rule parses conditions', 'List<JsonObject>' in spawn_spec and 'conditions' in spawn_spec)
check('spawn checks player condition', 'BriefingConditionService.all(player' in spawn_service and '.conditions()' in spawn_service)
check('spawn scan can be invalidated', 'invalidatePlayer' in spawn_events)
check('quest changes invalidate spawn scan', 'NpcStructureSpawnEvents.invalidatePlayer' in quest_runtime)
check('flag changes invalidate spawn scan', 'NpcStructureSpawnEvents.invalidatePlayer' in flags)
check('dialogue has ordered entry nodes', 'entryNodeIds' in dialogue_spec)
check('dialogue resolves best entry', 'openBestNodeForPlayer' in flow)
check('V dialogue uses best entry', 'openBestNodeForPlayer' in packet)
check('right click can open dialogue', 'openBestNodeForPlayer' in progress_events and 'setCanceled(true)' in progress_events)
check('right-click dialogue resolves before interaction objective mutates phase', progress_events.index('openBestNodeForPlayer') < progress_events.index('onEntityInteracted'))
check('J key exists', 'OPEN_QUEST_JOURNAL' in keys and 'GLFW_KEY_J' in keys)
check('J key sends journal request', 'OPEN_QUEST_JOURNAL.consumeClick' in client_events or ((ROOT/'src/main/java/com/phantasm/briefing/client/QuestJournalClientEvents.java').exists() and 'OPEN_QUEST_JOURNAL.consumeClick' in read('src/main/java/com/phantasm/briefing/client/QuestJournalClientEvents.java')))
check('journal request packet registered', 'OpenQuestJournalC2SPacket' in network)
check('web splits PB and FTB condition sources', 'condition.source.pb' in html and 'condition.source.ftb' in html)
check('web supports PB phase selector', 'pb_quest_phase' in html and 'phaseSelectField' in html)
check('web FTB condition options only completion states', 'ftb_quest_completed' in html and 'ftb_quest_not_completed' in html)
check('web spawn conditions are serialized', 'spawnConditions' in html or 'conditions:(s.conditions' in html)
check('web writes dialogue entry ordering', 'entryNodeIds' in html)
check('web removes old NPC linked quest UI', 'recordSelect("field.npcQuest"' not in html and 'textarea("field.lockedText"' not in html)
check('web spawn editor exposes conditions', 'field.spawnCondition' in html and 'renderConditionList(`${base}.conditions`' in html)
check('web dialogue scenes can be reordered', 'dialogue-move-node' in html and 'dialogue-entry-toggle' in html)

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(('PASS' if ok else 'FAIL') + ' - ' + name)
if failed:
    raise SystemExit(f'{len(failed)} checks failed')
print(f'All {len(checks)} checks passed')
