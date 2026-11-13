#!/usr/bin/env python3
"""Reuse the approved H13.1 HTTP fault/concurrency scenario, then compare all rows.
No hosted capability, new accounts or fixture duplication.
"""
import contextlib,copy,io,json
import common as c
output=io.StringIO()
with contextlib.redirect_stdout(output):probe=c.load('h132_network_probe','scripts/h131/network.py')
result=json.loads(output.getvalue())
expected=[dict(kind=r['kind'],key=r['key'],revision=1,payload=copy.deepcopy(r['payload']))for r in probe.rows]
winner=next(i for i,r in enumerate(probe.answers)if r['accepted'])
for r in expected:
 if r['kind']=='grades'and r['payload']['activityId']==probe.grade['payload']['activityId']and r['payload']['studentId']in('ds2','ds3','ds4'):
  sid=r['payload']['studentId'];r['revision']=3 if sid=='ds2'else 2
  r['payload']['value']=(2+winner)if sid=='ds2'else None if sid=='ds3'else 0
 if r['kind']=='activities'and r['payload']['id']=='d-a1':r['revision']=2;r['payload']['maximum']=7
actual=json.loads(probe.f.execute("SELECT jsonb_agg(jsonb_build_object('kind',kind,'key',key,'revision',revision,'payload',payload)) FROM public.noctua_academic_records;",probe.A))
bench=c.load('h132_network_digest','scripts/benchmark-h13.py')
assert bench.canonical(expected)==bench.canonical(actual),'HTTP convergence modified unrelated records/identities/revisions'
result.update(case='H13.2-network',source_test='H13.1-network',all_171_records_payloads_ids_revisions_equal=True,academic_sha256=bench.digest(actual))
print(json.dumps(result,indent=2))
