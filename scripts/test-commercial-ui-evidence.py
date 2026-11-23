#!/usr/bin/env python3
"""Read-only proof of repeated native A/B/A transitions against this job's synthetic DB."""
import json,os,subprocess
from pathlib import Path
assert os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('CI')=='true'
assert os.environ.get('NOCTUA_COMMERCIAL_DISPOSABLE')=='YES'
assert (os.environ.get('PGHOST'),os.environ.get('PGPORT'),os.environ.get('PGDATABASE'))==('127.0.0.1','55431','noctua_phase_e_test')
base=Path('build/commercial-tests/android')
points=[json.loads((base/f'commercial-final-transition-{r}-{p}.json').read_text())for r in range(3)for p in ('B','A')]
for d in points:
    assert d['auth_owner']==d['workspace_owner']==d['control_owner']==d['point']
    assert d['authenticated'] and not d['initializing'] and not d['loading']
    assert not d['other_owner_states'] and d['inbox']==0
    if d['point']=='B':
        assert d['counts']==dict(classes=1,terms=4) and not d['previous_empty_predicate']
        assert d['stage']=='BOOTSTRAP_E' and d['ineligible'] and not d['active'] and d['cursor']==0
        assert d['decisions']==0
    else:
        assert d['stage']=='ACTIVE_E' and d['active'] and not d['ineligible']
        assert d['pending']==0 and len(d['counts'])==15
for point in ('A','B'):
    values=[d for d in points if d['point']==point]
    for field in ('hash','cursor','counts','decisions','pending','inbox'):
        assert all(d[field]==values[0][field]for d in values),f'{point}: changed {field}'
# These principals are fixed synthetic fixtures, never hosted account identities.
a='88888888-8888-4888-8888-888888888888';b='99999999-9999-4999-8999-999999999999'
query=f"""SELECT jsonb_build_object(
 'A_records',(SELECT count(*) FROM public.noctua_academic_records WHERE owner_id='{a}'),
 'A_head',(SELECT sequence FROM public.noctua_academic_heads WHERE owner_id='{a}'),
 'A_receipts',(SELECT count(*) FROM public.noctua_academic_receipts WHERE owner_id='{a}'),
 'A_unique_receipts',(SELECT count(DISTINCT operation_id) FROM public.noctua_academic_receipts WHERE owner_id='{a}'),
 'A_decisions',(SELECT count(*) FROM public.noctua_e_decisions WHERE owner_id='{a}'),
 'B_records',(SELECT count(*) FROM public.noctua_academic_records WHERE owner_id='{b}'),
 'B_protocols',(SELECT count(*) FROM public.noctua_sync_protocols WHERE owner_id='{b}'),
 'B_claims',(SELECT count(*) FROM public.noctua_commercial_claims WHERE owner_id='{b}'),
 'B_entitlements',(SELECT count(*) FROM public.noctua_commercial_accounts WHERE owner_id='{b}'));
"""
r=subprocess.run([os.environ.get('PSQL_BIN','psql'),'-XAtq','-v','ON_ERROR_STOP=1'],input=query,text=True,capture_output=True)
assert r.returncode==0,'Disposable server evidence query failed'
server=json.loads(r.stdout);current=next(d for d in points if d['point']=='A')
assert all(server[k]==0 for k in ('B_records','B_protocols','B_claims','B_entitlements'))
assert server['A_records']==sum(current['counts'].values())
assert server['A_head']==current['cursor'] and server['A_decisions']==current['decisions']
assert server['A_receipts']==server['A_unique_receipts']
result=dict(rounds=3,points=points,server=server,passed=True,hosted_requests=0)
(base/'ui-transition-evidence.json').write_text(json.dumps(result,indent=2,sort_keys=True)+'\n')
print(json.dumps(dict(passed=True,rounds=3,A_hash=current['hash'],A_cursor=current['cursor'],B_local_records=5,B_remote_records=0,B_protocols=0)))
