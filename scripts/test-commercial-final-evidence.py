#!/usr/bin/env python3
"""Cross-check actual two-AVD evidence against disposable PostgreSQL; never hosted."""
import hashlib,json,os,re,statistics,subprocess,tarfile
from pathlib import Path

if not (os.environ.get('NOCTUA_COMMERCIAL_DISPOSABLE')=='YES' and
        os.environ.get('NOCTUA_COMMERCIAL_FINAL')=='YES' and
        os.environ.get('PGHOST')=='127.0.0.1' and os.environ.get('PGPORT')=='55431' and
        os.environ.get('PGDATABASE')=='noctua_phase_e_test'):
    raise SystemExit('Disposable commercial final fixture required; no hosted option')
base=Path('build/commercial-tests/android')
final={}
for side in ('primary','secondary'):
    with tarfile.open(base/f'final-{side}.tar')as t:
        members=t.getmembers()
        assert all(not Path(m.name).is_absolute() and '..' not in Path(m.name).parts and not m.issym() and not m.islnk() for m in members)
        m=t.getmember('commercial-final-e-auto-final-final.json')
        assert m.isfile() and m.size<512*1024
        final[side]=json.load(t.extractfile(m))
        assert final[side]['stage']=='ACTIVE_E' and final[side]['message']==''
        assert final[side]['pending']==final[side]['inbox']==0
        assert sum(final[side]['counts'].values())==172 and len(final[side]['counts'])==15
        assert all(s['local']==s['acknowledged'] for s in final[side]['states'])
        assert final[side]['hash']==(base/f'final-{side}.sha256').read_text()
assert final['primary']['hash']==final['secondary']['hash']
assert len(final['primary']['decisions'])==3 and all(d['phase']=='APPLIED' for d in final['primary']['decisions'])
owner='88888888-8888-4888-8888-888888888888'
def sql(text):
    r=subprocess.run([os.environ.get('PSQL_BIN','psql'),'-XAtq','-v','ON_ERROR_STOP=1'],input=text,text=True,capture_output=True)
    assert r.returncode==0,'Disposable SQL evidence query failed'
    return json.loads(r.stdout)
rows=sql(f"SELECT jsonb_agg(jsonb_build_object('kind',kind,'key',key,'revision',revision) ORDER BY kind,key) FROM public.noctua_academic_records WHERE owner_id='{owner}';")
contract=json.loads(Path('supabase/contracts/academic-v2.json').read_text())['entities']
expected=[]
for row in rows:
    keys=[str(row['key'][k])for k in contract[row['kind']]['key']]
    if len(keys)==1:keys.append('')
    expected.append((row['kind'],hashlib.sha256('|'.join(keys).encode()).hexdigest(),row['revision']))
for side,d in final.items():
    assert sorted(expected)==sorted((s['kind'],s['key_hash'],s['revision'])for s in d['states'])
server=sql(f"""SELECT jsonb_build_object(
 'head',(SELECT sequence FROM public.noctua_academic_heads WHERE owner_id='{owner}'),
 'records',(SELECT count(*) FROM public.noctua_academic_records WHERE owner_id='{owner}'),
 'receipts',(SELECT count(*) FROM public.noctua_academic_receipts WHERE owner_id='{owner}'),
 'unique_receipts',(SELECT count(DISTINCT operation_id) FROM public.noctua_academic_receipts WHERE owner_id='{owner}'),
 'decisions',(SELECT count(*) FROM public.noctua_e_decisions WHERE owner_id='{owner}'),
 'grade_revision',(SELECT revision FROM public.noctua_academic_records WHERE owner_id='{owner}' AND kind='grades' AND key='{{"activityId":"d-a1","studentId":"ds2"}}'::jsonb),
 'grade_versions',(SELECT jsonb_agg(payload->'value' ORDER BY revision) FROM public.noctua_academic_versions WHERE owner_id='{owner}' AND kind='grades' AND key='{{"activityId":"d-a1","studentId":"ds2"}}'::jsonb),
 'unentitled_records',(SELECT count(*) FROM public.noctua_academic_records WHERE owner_id='99999999-9999-4999-8999-999999999999'),
 'unentitled_protocols',(SELECT count(*) FROM public.noctua_sync_protocols WHERE owner_id='99999999-9999-4999-8999-999999999999'));
""")
assert server['head']==final['primary']['cursor']==final['secondary']['cursor']
assert server['records']==172 and server['grade_revision']==10 and server['decisions']==3
assert server['grade_versions']==[None,0,1.5,2.5,9,4,8,9.5,7.5,8.5]
assert server['receipts']==server['unique_receipts']
assert server['unentitled_records']==server['unentitled_protocols']==0
logs=list(base.glob('e-auto-final-*.txt'))
assert len(logs)==29, len(logs)
for p in logs:
    text=p.read_text()
    assert 'OK (1 test)' in text and not any(x in text for x in ('FAILURES','INSTRUMENTATION_FAILED','Process crashed','INSTRUMENTATION_STATUS_CODE: -3','INSTRUMENTATION_STATUS_CODE: -4'))
http=[]
for p in base.glob('final-http-*.jsonl'):
    http.extend(json.loads(s)for s in p.read_text().splitlines())
metrics={}
for method in sorted({r['method'] for r in http}):
    values=[r['http_roundtrip_ms']for r in http if r['method']==method and r['completed']]
    if values:metrics[method]=dict(completed=len(values),median_ms=statistics.median(values),max_ms=max(values))
result=dict(android_final_phases_passed=len(logs),android_ignored=0,android_failed=0,
    two_independent_AVDs=True,server=server,primary=final['primary'],secondary=final['secondary'],
    identical_hashes=True,exact_server_ids_and_revisions_match_both_Rooms=True,
    HTTP_roundtrips_including_Android_transport=metrics,Auth='Synthetic local client; real Keystore vault and login UI; NOT hosted GoTrue',
    hosted_requests=0,H13_or_PT409_applied=False)
(base/'final-evidence.json').write_text(json.dumps(result,indent=2,sort_keys=True)+'\n')
print(json.dumps(dict(phases_passed=len(logs),hash=final['primary']['hash'],server=server)))
