#!/usr/bin/env python3
"""PostgREST security after E AVD proof. LOCAL synthetic identities, no hosted request."""
import importlib.util,uuid
from pathlib import Path
spec=importlib.util.spec_from_file_location('fixture',Path(__file__).with_name('test-phase-b-http.py'))
f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f)
A='88888888-8888-4888-8888-888888888888';B='99999999-9999-4999-8999-999999999999'
a=f.token(A,'81818181-8181-4181-8181-818181818181');b=f.token(B,'93939393-9393-4393-8393-939393939393');checks=0
def check(path,body=None,jwt=None,method='POST',expected=200):
 global checks
 result=f.request(path,body,jwt,method,expected);checks+=1;return result
for jwt in [None,'invalid-token']:check('/rpc/noctua_pull_academic_e',{'p_after':0,'p_offset':0},jwt,expected=401)
check('/rpc/noctua_pull_academic_e',{'p_after':0,'p_offset':0},f.token(A,str(uuid.uuid4())),expected=403)
now=f.time.time;f.time.time=lambda:1
expired=f.token(A,'81818181-8181-4181-8181-818181818181');f.time.time=now
check('/rpc/noctua_pull_academic_e',{'p_after':0,'p_offset':0},expired,expected=401)
for jwt in [a,b]:assert check('/rpc/noctua_academic_status_c',jwt=jwt)['academicScope']==5
for table in ['noctua_e_transfers','noctua_e_parts','noctua_academic_records','noctua_academic_versions','noctua_academic_receipts','noctua_academic_batches','noctua_academic_heads','noctua_academic_links','noctua_academic_uniques','noctua_sync_protocols','noctua_academic_test_accounts']:
 assert check('/'+table+'?owner_id=eq.'+A,jwt=b,method='GET')==[]
 check('/'+table,jwt=b,method='DELETE',expected=403)
for name,body in [('noctua_e_principal',{}),('noctua_e_write',{'p_operation':str(uuid.uuid4()),'p_changes':[]}),('noctua_e_guarded_write',{'p_operation':str(uuid.uuid4()),'p_changes':[]})]:check('/rpc/'+name,body,b,expected=403)
check('/rpc/noctua_pull_academic_d',{'p_after':0},a,expected=403)
check('/rpc/noctua_enable_academic_d',{'p_expected_sequence':0},a,expected=403)
op=str(uuid.uuid4());check('/rpc/noctua_begin_academic_e',{'p_operation':op,'p_total':1,'p_parts':1},b)
change=dict(kind='grades',key=dict(activityId='d-a1',studentId='e-student-1'),base=1,payload=dict(activityId='d-a1',studentId='e-student-1',value=9))
check('/rpc/noctua_stage_academic_e',{'p_operation':op,'p_part':0,'p_changes':[change]},b)
reply=check('/rpc/noctua_commit_academic_e',{'p_operation':op},b);assert not reply['accepted'] and reply['sequence'] is None
assert check('/rpc/noctua_pull_academic_e',{'p_after':0,'p_offset':0},b)==[]
print(f'{checks} disposable phase E HTTP/security checks passed; signed fixture identities, not hosted Auth.')
