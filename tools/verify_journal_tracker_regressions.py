from pathlib import Path

root = Path(__file__).resolve().parents[1]
java = root / 'src/main/java/com/phantasm/briefing'

def text(path):
    return (java / path).read_text(encoding='utf-8')

screen = text('client/screen/QuestJournalScreen.java')
runtime = text('service/QuestRuntimeService.java')
tracker = text('service/QuestTrackerService.java')
hot = text('event/EditorHotReloadEvents.java')

checks = [
    ('journal background renders before dynamic content', 'renderPanelBackground(guiGraphics, layout);' in screen),
    ('journal click sound exists', 'SimpleSoundInstance.forUI' in screen and 'SoundEvents.UI_BUTTON_CLICK' in screen),
    ('journal selected border exists', 'renderSelectionFrame' in screen),
    ('tracker uses phase objective lines', 'currentPhaseObjectiveLines' in tracker),
    ('runtime exposes phase objective lines', 'public static List<String> currentPhaseObjectiveLines' in runtime),
    ('runtime can reconcile completed phases', 'reconcileActiveQuestFlow' in runtime),
    ('hot reload reconciles active quest flow', 'reconcileActiveQuestFlow(player)' in hot),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(('PASS' if ok else 'FAIL') + ' - ' + name)
if failed:
    raise SystemExit('Missing: ' + ', '.join(failed))
