#!/usr/bin/env python3
"""Two synthetic tenants in the guarded, disposable H05 backend. Never hosted."""
import runpy, uuid
from pathlib import Path
f=runpy.run_path(str(Path(__file__).with_name('h05-local-fixture.py')))
jwt=f['f'].token('99999999-9999-4999-8999-999999999999','93939393-9393-4393-8393-939393939393')
def rpc(name,body=None):return f['f'].request('/rpc/'+name,body,jwt)
s=rpc('noctua_academic_status_c');assert s['protocol']==1
rpc('noctua_activate_academic',dict(p_expected_sequence=s['v1Sequence'],p_expected_v1=s['v1']))
for stage in ('c','d','e'):rpc('noctua_enable_academic_'+stage,dict(p_expected_sequence=0))
source=f['s'];cls=dict(source['classes'][0],id='p1-b-class',name='P1 synthetic B')
terms=[dict(t,classId=cls['id'])for t in source['terms']if t['classId']==source['classes'][0]['id']]
assert len(terms)==4
student=dict(source['students'][0],id='p1-b-student',classId=cls['id'],personId='p1-b-person',name='Synthetic B',status='Ativo',joined='2026-01-01',left=None,notes='')
rows=[f['change']('classes',cls)]+[f['change']('terms',t)for t in terms]+[f['change']('students',student)]
op=str(uuid.uuid4());rpc('noctua_begin_academic_e',dict(p_operation=op,p_total=6,p_parts=1))
rpc('noctua_stage_academic_e',dict(p_operation=op,p_part=0,p_changes=rows))
assert rpc('noctua_commit_academic_e',dict(p_operation=op))['accepted']
assert rpc('noctua_academic_status_c')['academicSequence']==1
print('P1 disposable fixture ready: A=171/15 types, B=6/3 types. No real Auth accounts.')
