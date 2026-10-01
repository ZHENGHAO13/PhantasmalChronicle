from pathlib import Path
root=Path(__file__).resolve().parents[1]
checks=[]
def need(path, text, name):
    p=root/path
    data=p.read_text(encoding='utf-8') if p.exists() else ''
    checks.append((name, text in data))
need(Path('src/main/java/com/phantasm/briefing/data/QuestJournalPhaseEntry.java'),'record QuestJournalPhaseEntry','phase journal entry exists')
need(Path('src/main/java/com/phantasm/briefing/data/QuestJournalEntry.java'),'List<QuestJournalPhaseEntry> phases','journal entry carries all phases')
need(Path('src/main/java/com/phantasm/briefing/service/BriefingPlayerData.java'),'COMPLETED_PHASES','completed phase persistence exists')
need(Path('src/main/java/com/phantasm/briefing/service/BriefingPlayerData.java'),'objectiveStorageKey','objective storage is phase-scoped')
need(Path('src/main/java/com/phantasm/briefing/service/QuestJournalService.java'),'buildPhases','journal builds all phases')
need(Path('src/main/java/com/phantasm/briefing/client/screen/QuestJournalScreen.java'),'selected.phases()','screen renders all phases')
need(Path('src/main/java/com/phantasm/briefing/network/packet/OpenQuestJournalS2CPacket.java'),'QuestJournalPhaseEntry','packet syncs phase journal entries')
failed=[name for name,ok in checks if not ok]
for name,ok in checks: print(('PASS' if ok else 'FAIL'), name)
if failed: raise SystemExit(1)
