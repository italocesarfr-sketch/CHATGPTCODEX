#!/usr/bin/env python3
"""Reuse the approved H13.1 HTTP fault/concurrency scenario, then compare all rows.
No hosted capability, new accounts or fixture duplication.
"""
import contextlib,copy,io,json,os,subprocess
import common as c
assert os.environ.get('PGDATABASE')=='noctua_phase_b_test'and os.environ.get('PGHOST')in('127.0.0.1','localhost','/workspace/pg-local')
def sql(value):
 r=subprocess.run([os.environ.get('PSQL_BIN','psql'),'-XAtq','-v','ON_ERROR_STOP=1'],input=value,text=True,capture_output=True)
 assert r.returncode==0,'Disposable network fixture SQL failure (response omitted)'
 return r.stdout.strip()
definition_query="SELECT string_agg(pg_get_functiondef(oid),E';\\n' ORDER BY proname)||';' FROM pg_proc WHERE pronamespace='public'::regnamespace AND proname IN('noctua_d_get','noctua_d_history_guard','noctua_d_graph');"
original=sql(definition_query)
output=io.StringIO()
try:
 sql(c.CANDIDATE.read_text())
 with contextlib.redirect_stdout(output):probe=c.load('h132_network_probe','scripts/h131/network.py')
finally:
 sql(original);assert sql(definition_query)==original,'Previous helper definitions not restored'
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
result.update(case='H13.2-network',source_test='H13.1-network',lab_validator='H132_REJECTED_CANDIDATE',candidate_applied_explicitly=True,previous_helpers_restored=True,all_171_records_payloads_ids_revisions_equal=True,academic_sha256=bench.digest(actual))
print(json.dumps(result,indent=2))
