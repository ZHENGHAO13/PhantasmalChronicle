from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'src/main/java/com/phantasm/briefing'

def read(rel):
    return (JAVA / rel).read_text(encoding='utf-8')

checks = []

def check(name, ok):
    checks.append((name, bool(ok)))

choice = read('client/screen/DialogueChoiceScreen.java')
session = read('client/ClientDialogueSession.java')
bubble = read('client/DialogueBubbleClientEvents.java')
interaction = read('client/DialogueInteractionClientEvents.java')
runtime = read('service/QuestRuntimeService.java')

check('choice screen no vanilla Button widgets', 'gui.components.Button' not in choice and 'Button.builder' not in choice)
check('choice screen uses custom original-style drawing', 'DIALOGUE_BACKGROUND' in choice and 'DIALOGUE_ACCENT' in choice and 'drawChoiceBubble' in choice)
check('choice screen supports hover mouse selection', 'mouseClicked' in choice and 'hovered' in choice)
check('choice selection keeps UI click sound', 'UI_BUTTON_CLICK' in choice and 'SimpleSoundInstance.forUI' in choice)
check('client session can clear stale missing speaker', 'clearIfSpeakerMissing' in session and 'minecraft.level.getEntity' in session)
check('dialogue tick invokes stale speaker recovery', 'clearIfSpeakerMissing' in bubble)
check('journal hotkey invokes stale speaker recovery', 'clearIfSpeakerMissing' in interaction)

# Reset must re-run auto-start evaluation after state removal.
reset_start = runtime.index('public static boolean reset(')
reset_end = runtime.index('public static int resetAll(', reset_start)
reset_body = runtime[reset_start:reset_end]
check('single quest reset re-evaluates auto-start', 'ensureAutoStartedQuests(player)' in reset_body)
reset_all_start = reset_end
reset_all_end = runtime.index('private static void activateQuest', reset_all_start)
reset_all_body = runtime[reset_all_start:reset_all_end]
check('reset all re-evaluates auto-start', 'ensureAutoStartedQuests(player)' in reset_all_body)

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(('PASS' if ok else 'FAIL') + ': ' + name)
if failed:
    raise SystemExit(f'{len(failed)} checks failed')
print(f'All {len(checks)} checks passed')
