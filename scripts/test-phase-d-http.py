#!/usr/bin/env python3
"""Negative HTTP proof AFTER the fifteen-type AVD scenario. Only local synthetic Auth."""
import importlib.util,uuid,json
from pathlib import Path
spec=importlib.util.spec_from_file_location('http_fixture',Path(__file__).with_name('test-phase-b-http.py'))
f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f)
A='66666666-6666-4666-8666-666666666666';B='77777777-7777-4777-8777-777777777777'
a=f.token(A,'14141414-1414-4414-8414-141414141414');b=f.token(B,'12121212-1212-4212-8212-121212121212')
request=f.request;checks=0
def check(path,body=None,jwt=None,method='POST',expected=200):
 global checks
 result=request(path,body,jwt,method,expected);checks+=1;return result
for jwt in (None,'invalid-not-a-jwt'):
 check('/rpc/noctua_pull_academic_d',{'p_after':0},jwt,expected=401)
check('/rpc/noctua_pull_academic_d',{'p_after':0},f.token(A,str(uuid.uuid4())),expected=403)
assert check('/rpc/noctua_academic_status_c',jwt=a)['academicScope']==4
assert check('/rpc/noctua_academic_status_c',jwt=b)['academicScope']==4
for table in ('noctua_academic_records','noctua_academic_versions','noctua_academic_receipts','noctua_academic_batches','noctua_academic_heads','noctua_academic_links','noctua_academic_uniques','noctua_sync_protocols','noctua_academic_test_accounts'):
 assert check('/'+table+'?owner_id=eq.'+A,jwt=b,method='GET')==[]
 check('/'+table,jwt=b,method='DELETE',expected=403)
for name,body in [('noctua_d_projection',{'p_owner':A}),('noctua_d_graph',{'p_owner':A}),('noctua_d_history_guard',{'p_before':[],'p_after':[]}),('noctua_d_get',{'p_rows':[],'p_kind':'students','p_key':{}})]:
 check('/rpc/'+name,body,b,expected=403)
check('/rpc/noctua_enable_academic_c',{'p_expected_sequence':0},a,expected=403)
check('/rpc/noctua_push_academic',{'p_operation':str(uuid.uuid4()),'p_changes':[]},a,expected=403)
check('/rpc/noctua_push_academic_c',{'p_operation':str(uuid.uuid4()),'p_changes':[]},a,expected=403)
record=dict(id='d-journal-target',classId='d-target',date='2026-11-02',title='Fictício',description='Must not overwrite another owner')
reply=check('/rpc/noctua_push_academic_d',dict(p_operation=str(uuid.uuid4()),p_changes=[dict(kind='lesson_records',key={'id':record['id']},base=1,payload=record)]),b)
assert reply['accepted'] is False and reply['sequence'] is None
assert check('/rpc/noctua_pull_academic_d',{'p_after':0},b)==[]
print(f'{checks} disposable phase D HTTP/security checks passed; fixture JWTs, never hosted Auth.')
