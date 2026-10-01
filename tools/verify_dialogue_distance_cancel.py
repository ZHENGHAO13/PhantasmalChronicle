from pathlib import Path

root = Path(__file__).resolve().parents[1]
flow = (root / 'src/main/java/com/phantasm/briefing/service/DialogueFlowCoordinator.java').read_text(encoding='utf-8')
events = (root / 'src/main/java/com/phantasm/briefing/event/DialogueSessionEvents.java').read_text(encoding='utf-8')
network = (root / 'src/main/java/com/phantasm/briefing/network/ModNetwork.java').read_text(encoding='utf-8')
packet_path = root / 'src/main/java/com/phantasm/briefing/network/packet/CloseDialogueSessionS2CPacket.java'

checks = {
    '8 block constant': 'MAX_DIALOGUE_DISTANCE = 8.0D' in flow,
    'distance squared check': 'distanceToSqr' in flow and 'MAX_DIALOGUE_DISTANCE_SQR' in flow,
    'active-session fast exit': 'if (!hasActiveSession(player))' in flow,
    'server closes client session': 'CloseDialogueSessionS2CPacket' in flow,
    'player tick validation': 'PlayerTickEvent' in events and 'validateActiveSession' in events,
    'close packet file exists': packet_path.exists(),
    'close packet registered': 'CloseDialogueSessionS2CPacket.class' in network,
}
if packet_path.exists():
    packet = packet_path.read_text(encoding='utf-8')
    checks['client clear handler'] = 'ClientDialogueSession.getInstance().clear()' in packet
else:
    checks['client clear handler'] = False

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + name)
if failed:
    raise SystemExit('Missing dialogue distance-cancel behavior: ' + ', '.join(failed))
