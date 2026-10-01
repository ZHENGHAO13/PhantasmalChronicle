from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding='utf-8')

checks = []
def check(name, condition):
    checks.append((name, bool(condition)))

bubble = read('src/main/java/com/phantasm/briefing/client/DialogueBubbleClientEvents.java')
zh = read('src/main/resources/assets/phantasmbriefing/lang/zh_cn.json')
session = read('src/main/java/com/phantasm/briefing/client/ClientDialogueSession.java')
overlay = read('src/main/java/com/phantasm/briefing/client/screen/DialogueOverlayScreen.java')
cinematic = read('src/main/java/com/phantasm/briefing/client/CinematicClientEvents.java')
node = read('src/main/java/com/phantasm/briefing/data/DialogueNode.java')
spec = read('src/main/java/com/phantasm/briefing/data/DialogueSpec.java')
flow = read('src/main/java/com/phantasm/briefing/service/DialogueFlowCoordinator.java')
protect = read('src/main/java/com/phantasm/briefing/event/QuestNpcProtectionEvents.java')
spawn_spec = read('src/main/java/com/phantasm/briefing/data/NpcStructureSpawnSpec.java')
spawn_service = read('src/main/java/com/phantasm/briefing/service/NpcStructureSpawnService.java')
saved = read('src/main/java/com/phantasm/briefing/service/NpcStructureSpawnSavedData.java')
commands = read('src/main/java/com/phantasm/briefing/command/PBriefingCommands.java')
runtime = read('src/main/java/com/phantasm/briefing/service/QuestRuntimeService.java')
html = read('index.html')

check('right-click dialogue hint', 'right_click_continue' in bubble and 'right_click_reveal' in bubble and '[右键]' in zh and '[Enter]' not in bubble)
check('right-click progression handler', 'GLFW_MOUSE_BUTTON_RIGHT' in bubble and 'session.advance()' in bubble)
check('mouse choice screen exists', (ROOT / 'src/main/java/com/phantasm/briefing/client/screen/DialogueChoiceScreen.java').exists())
check('normal numeric choice input removed', 'GLFW_KEY_1' not in bubble and 'selectOptionByIndex' not in bubble)
check('cinematic numeric choice input removed', 'mapKeyToOptionIndex' not in cinematic and 'GLFW_KEY_1' not in overlay)
check('cinematic right click advances', 'GLFW_MOUSE_BUTTON_RIGHT' in overlay and 'requestAdvance()' in overlay)
check('cinematic left click chooses', 'selectOptionByIndex' in overlay and 'GLFW_MOUSE_BUTTON_LEFT' in overlay)
check('dialogue node auto trigger field', 'autoTrigger' in node and 'autoTrigger' in spec)
check('player attack trigger opens dialogue', 'player_attack' in protect and 'openTriggeredNodeForPlayer' in protect)
check('trigger coordinator and cooldown', 'openTriggeredNodeForPlayer' in flow and 'ATTACK_TRIGGER_COOLDOWN' in flow)
check('spawn spread radius schema', 'spreadRadius' in spawn_spec)
check('deterministic spawn spread', 'spreadRadius()' in spawn_service and 'deterministic' in spawn_service.lower())
check('saved data supports clearing instances', 'removeSpawned' in saved and 'removeCompletedStructure' in saved)
check('npc refresh command', 'Commands.literal("refresh")' in commands)
check('npc respawn command', 'Commands.literal("respawn")' in commands)
check('refresh/respawn service methods', 'refreshNearbyGeneratedNpcs' in spawn_service and 'respawnNearbyGeneratedNpcs' in spawn_service)
check('phase completion sound', 'EXPERIENCE_ORB_PICKUP' in runtime)
check('quest completion sound', 'PLAYER_LEVELUP' in runtime)
check('editor exposes spread radius', 'field.spreadRadius' in html and 'spreadRadius' in html)
check('editor exposes attack trigger', 'dialogue.autoTrigger' in html and 'player_attack' in html)
check('editor serializes attack trigger', 'autoTrigger:n.autoTrigger==="player_attack"?"player_attack":""' in html)
check('editor serializes spawn spread', 'spreadRadius:Math.max(0,Math.min(32,Number(s.spreadRadius||0)))' in html)
check('editor interaction wording distinguishes dialogue completion', '由 NPC 对话选择完成' in html)

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(('PASS' if ok else 'FAIL') + ' - ' + name)
print(f'\n{len(checks)-len(failed)}/{len(checks)} checks passed')
if failed:
    raise SystemExit(1)
