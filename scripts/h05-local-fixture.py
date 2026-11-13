#!/usr/bin/env python3
"""Seed only the existing disposable PostgREST fixture; NEVER a hosted project."""
import os,json,importlib.util,uuid
from pathlib import Path
assert os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('CI')=='true'
assert os.environ.get('PGHOST')=='127.0.0.1' and os.environ.get('PGDATABASE')=='noctua_phase_b_test'
root=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('f',root/'scripts/test-phase-b-http.py');f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f)
jwt=f.token('88888888-8888-4888-8888-888888888888','81818181-8181-4181-8181-818181818181')
def rpc(name,body=None):return f.request('/rpc/'+name,body,jwt)
status=rpc('noctua_academic_status_c');assert status['protocol']==1
rpc('noctua_activate_academic',dict(p_expected_sequence=status['v1Sequence'],p_expected_v1=status['v1']))
for stage in ('c','d','e'):rpc('noctua_enable_academic_'+stage,dict(p_expected_sequence=0))
s=json.loads((root/'app/src/androidTest/assets/phase-d.json').read_text())
NAMES=dict(classes='classes',terms='terms',students='students',lessons='lessons',marks='marks',activities='activities',grades='grades',recompositions='recompositions',relocations='relocations',drafts='import_drafts',exportRows='export_rows',classArchives='class_archives',lessonRecords='lesson_records',histories='preserved_histories',timetables='weekly_timetables')
KEYS=dict(terms=['classId','number'],grades=['activityId','studentId'],marks=['lessonId','studentId'],recompositions=['studentId','term'],relocations=['sourceStudentId'],class_archives=['classId'],preserved_histories=['classId'])
def change(kind,row,base=0):return dict(kind=kind,key={k:row[k]for k in KEYS.get(kind,['id'])},base=base,payload=row)
def push(rows):
 op=str(uuid.uuid4());parts=[rows[n:n+128]for n in range(0,len(rows),128)]
 rpc('noctua_begin_academic_e',dict(p_operation=op,p_total=len(rows),p_parts=len(parts)))
 for n,part in enumerate(parts):rpc('noctua_stage_academic_e',dict(p_operation=op,p_part=n,p_changes=part))
 assert rpc('noctua_commit_academic_e',dict(p_operation=op))['accepted']
rows=[change(kind,v)for name,kind in NAMES.items()for v in s[name]];assert len(rows)==171
push(rows)
row=dict(s['classes'][0],name='H05 turma fictícia renomeada com históricos')
push([change('classes',row,1)])
assert rpc('noctua_academic_status_c')['academicSequence']==2
print('H05 disposable fixture ready: 171 live records, 15 types, 2 atomic batches; synthetic Auth only.')
