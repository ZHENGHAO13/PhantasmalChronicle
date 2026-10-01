from pathlib import Path
root=Path('/mnt/data/final_pc_source')
checks=[]

def check(name, cond):
    checks.append((name, bool(cond)))

quest_dir=root/'src/main/resources/data/phantasmbriefing/phantasm_quests'
embedded={p.name for p in quest_dir.glob('*.json')}
check('no bundled demo quest resources', not ({'arcadia_demo_quest.json','arcadia_event_demo_quest.json','guild_supply.json'} & embedded))

spec=(root/'src/main/java/com/phantasm/briefing/data/QuestSpec.java').read_text(encoding='utf-8')
check('QuestSpec has autoStart flag', 'boolean autoStart' in spec and 'getAsBoolean(json, "autoStart", false)' in spec)

runtime=(root/'src/main/java/com/phantasm/briefing/service/QuestRuntimeService.java').read_text(encoding='utf-8')
check('runtime auto-start method exists', 'ensureAutoStartedQuests' in runtime)
check('auto-start only uses quests marked autoStart', '.autoStart()' in runtime)

player_events=(root/'src/main/java/com/phantasm/briefing/event/PlayerDataEvents.java').read_text(encoding='utf-8')
check('login triggers auto-start evaluation', 'PlayerLoggedInEvent' in player_events and 'ensureAutoStartedQuests' in player_events)

journal_service=(root/'src/main/java/com/phantasm/briefing/service/QuestJournalService.java').read_text(encoding='utf-8')
check('journal excludes available and locked tasks', 'QuestRuntimeStatus.ACTIVE' in journal_service and 'QuestRuntimeStatus.COMPLETED' in journal_service and 'AVAILABLE' not in journal_service.split('buildEntries')[1].split('}')[0])

entry=(root/'src/main/java/com/phantasm/briefing/data/QuestJournalEntry.java').read_text(encoding='utf-8')
check('journal entry contains phase title', 'phaseTitle' in entry)
check('journal entry contains objectives', 'QuestJournalObjectiveEntry' in entry)

screen=(root/'src/main/java/com/phantasm/briefing/client/screen/QuestJournalScreen.java').read_text(encoding='utf-8')
check('journal has in-progress section', 'journal.in_progress' in screen)
check('journal has completed section', 'journal.completed' in screen)
check('journal has detail panel', 'journal.details' in screen)
check('old flat pagination removed', '滚轮翻页' not in screen and 'buildDisplayLines' not in screen)

html=Path('/mnt/data/PhantasmMaker-多人任务对话与NPC生成优化版.html').read_text(encoding='utf-8')
check('web editor imports autoStart', 'autoStart:Boolean(raw.autoStart)' in html)
check('web editor creates autoStart', 'autoStart:false' in html)
check('web editor exports autoStart', 'autoStart:Boolean(q.autoStart)' in html)
check('web editor renders autoStart option', 'field.autoStart' in html)
check('give_item action type reinitializes data', 'setPath(record,actionPath,makeAction(element.value))' in html)

failed=[n for n,ok in checks if not ok]
for n,ok in checks:
    print(('PASS' if ok else 'FAIL'), n)
print(f'\n{len(checks)-len(failed)}/{len(checks)} passed')
if failed:
    raise SystemExit(1)
