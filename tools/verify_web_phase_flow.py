from pathlib import Path
html=(Path(__file__).resolve().parents[1]/'index.html').read_text(encoding='utf-8')
checks=[
 ('warns stalled non-last phase','本阶段目标完成后不会自动进入下一阶段' in html),
 ('next mode prefers following phase','record.phases[index+1]?.phaseId' in html),
 ('give item action initializes through makeAction','setPath(record,actionPath,makeAction(element.value))' in html),
]
for n,ok in checks: print(('PASS' if ok else 'FAIL')+' - '+n)
if not all(ok for _,ok in checks): raise SystemExit(1)
