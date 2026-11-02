#!/usr/bin/env python3
"""Local disposable phase C/RLS; no hosted project or real Auth accounts."""
import os,importlib.util,json,uuid,unittest,concurrent.futures
from pathlib import Path
if os.environ.get('PGDATABASE')not in ('noctua_phase_c_test','noctua_phase_d_test'):raise SystemExit('Disposable noctua_phase_c_test required')
spec=importlib.util.spec_from_file_location('phaseb',Path(__file__).with_name('test-phase-b-postgres.py'))
b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
f=b.f;execute=b.execute;A=b.A;B=b.B;literal=b.literal
MIGRATION=f.ROOT/'supabase/migrations/202611020002_phase_c_scope.sql'
execute(MIGRATION.read_text())
def status(owner=A):return json.loads(execute('SELECT public.noctua_academic_status_c();',owner))
def enable(owner=A,sequence=None):
 s=status(owner);return json.loads(execute(f"SELECT public.noctua_enable_academic_c({s['academicSequence'] if sequence is None else sequence});",owner))
def statement(rows,op=None):return f"SELECT public.noctua_push_academic_c('{op or uuid.uuid4()}',{literal(rows)});"
def push(rows,owner=A,op=None):return json.loads(execute(statement(rows,op),owner))
def pull(owner=A):
 pages=[];after=0
 while True:
  page=json.loads(execute(f'SELECT public.noctua_pull_academic_c({after});',owner))
  if not page:return pages
  assert len(page)==1 and page[0]['sequence']==after+1
  pages+=page;after=page[0]['sequence']
def mark(value='FJ',reason='Outro',student='student-fixture',lesson='lesson-fixture'):return f.change('marks',dict(lessonId=lesson,studentId=student,mark=value,reason=reason))
def grade(value=0,student='student-fixture',activity='activity-fixture',base=0):return f.change('grades',dict(activityId=activity,studentId=student,value=value),base)
def recomp(value=7):return f.change('recompositions',dict(studentId='student-fixture',term=4,value=value,date='2026-10-20'))
class PhaseCPostgresTest(unittest.TestCase):
 def setUp(self):b.reset();b.activate()
 def seed(self):
  enable();return push(f.classroom()+[f.change('students',f.student()),f.change('lessons',f.lesson()),f.change('activities',f.activity())])
 def test_01_scope_is_explicit_and_old_clients_are_fenced(self):
  self.assertEqual(2,status()['academicScope']);execute(statement(f.classroom()),A,fails=True)
  enable();self.assertEqual(3,status()['academicScope'])
  execute(f.push_sql(f.classroom()),A,fails=True);execute('SELECT public.noctua_pull_academic(0);',A,fails=True)
  execute(f.statement(f.bundle()),A,fails=True)
 def test_02_expansion_requires_current_cursor_and_keeps_revisions(self):
  b.push(f.classroom());execute('SELECT public.noctua_enable_academic_c(0);',A,fails=True)
  self.assertEqual(2,status()['academicScope']);enable();self.assertEqual(5,len(pull()[0]['records']))
 def test_03_all_36_stable_enrollments_and_dependencies(self):
  enable();rows=f.classroom()+[f.change('students',dict(f.student(f's{n}',number=n),personId=f'p{n}')) for n in range(1,37)]
  rows += [f.change('activities',f.activity()),f.change('lessons',f.lesson())]
  rows += [grade(0,'s1'),grade(None,'s2'),mark('P',None,'s1'),mark('F',None,'s2'),mark(student='s3'),mark('NR',None,'s4')]
  self.assertTrue(push(rows)['accepted']);self.assertEqual(len(rows),len(pull()[0]['records']))
 def test_04_zero_null_and_missing_are_distinct(self):
  self.seed();push([grade(0)]);self.assertEqual(0,pull()[-1]['records'][0]['payload']['value'])
  push([grade(None,base=1)]);self.assertIsNone(pull()[-1]['records'][0]['payload']['value'])
  push([f.change('grades',{},2,key={'activityId':'activity-fixture','studentId':'student-fixture'})|{'payload':None}])
  self.assertIsNone(pull()[-1]['records'][0]['payload'])
 def test_05_all_attendance_states_and_six_optional_reasons(self):
  self.seed();rev=0
  for value,reason in [('P',None),('F',None),('NR',None),('FJ',None)]+[('FJ',r) for r in ['Atestado médico','Acompanhamento médico','Falecimento familiar','Convocação oficial','Atividade escolar ou esportiva','Outro']]:
   row=mark(value,reason);row['base']=rev;self.assertTrue(push([row])['accepted']);rev+=1
 def test_06_reason_on_non_fj_is_rejected_without_receipt(self):
  self.seed();before=len(pull());execute(statement([mark('F','Outro')]),A,fails=True);self.assertEqual(before,len(pull()))
 def test_07_grade_above_activity_maximum_rolls_back(self):
  self.seed();execute(statement([grade(11)]),A,fails=True);self.assertEqual(1,len(pull()))
 def test_08_grade_and_mark_cannot_cross_classes(self):
  self.seed();push(f.classroom('other')+[f.change('students',f.student('other-student',classId='other'))])
  for rows in [[grade(1,'other-student')],[mark(student='other-student')]]:execute(statement(rows),A,fails=True)
 def test_09_recomposition_zero_null_and_limits(self):
  self.seed();push([recomp(0)]);r=recomp(None);r['base']=1;push([r])
  r=recomp(11);r['base']=2;execute(statement([r]),A,fails=True)
 def test_10_parent_delete_requires_explicit_children(self):
  self.seed();push([grade(),mark()]);before=len(pull())
  execute(statement([f.change('lessons',{},1,key={'id':'lesson-fixture'})|{'payload':None}]),A,fails=True)
  self.assertEqual(before,len(pull()))
 def test_11_explicit_deletions_keep_versions(self):
  self.seed();push([grade(),mark()]);push([f.change('marks',{},1,key={'lessonId':'lesson-fixture','studentId':'student-fixture'})|{'payload':None},f.change('lessons',{},1,key={'id':'lesson-fixture'})|{'payload':None}])
  self.assertEqual(3,len(pull()));self.assertGreater(int(execute('SELECT count(*) FROM public.noctua_academic_versions;',A)),0)
 def test_12_concurrent_grade_conflict_has_stable_negative_receipt(self):
  self.seed();push([grade(0)]);first=push([grade(5,base=1)]);op=str(uuid.uuid4());rows=[grade(6,base=1)]
  conflict=push(rows,op=op);self.assertFalse(conflict['accepted']);self.assertEqual(conflict,push(rows,op=op));self.assertEqual(3,len(pull()))
 def test_13_independent_student_grades_do_not_conflict(self):
  self.seed();push([f.change('students',f.student('s2',number=2))]);self.assertTrue(push([grade(3)])['accepted']);self.assertTrue(push([grade(4,'s2')])['accepted'])
 def test_14_lost_response_replay_is_idempotent(self):
  self.seed();op=str(uuid.uuid4());rows=[grade(),mark()];r=push(rows,op=op);self.assertEqual(r,push(rows,op=op));self.assertEqual(2,len(pull()))
 def test_15_other_owner_cannot_read_or_change_known_ids(self):
  self.seed();push([grade(),mark()]);b.activate(B);enable(B);self.assertEqual([],pull(B))
  self.assertFalse(push([grade(9,base=1)],B)['accepted'])
  for table in b.TABLES:self.assertEqual('0',execute(f"SELECT count(*) FROM public.{table} WHERE owner_id='{A}';",B))
 def test_16_revoked_or_unconfirmed_session_cannot_use_c(self):
  self.seed();execute(statement([grade()]),f.UNCONFIRMED,fails=True)
  execute(f"UPDATE auth.sessions SET not_after=now()-interval '1 second' WHERE user_id='{A}';")
  execute('SELECT public.noctua_pull_academic_c(0);',A,fails=True)
 def test_17_lowering_maximum_cannot_invalidate_existing_grade(self):
  self.seed();push([grade(8)]);a=dict(f.activity(),maximum=5);execute(statement([f.change('activities',a,1)]),A,fails=True)
 def test_18_reapply_c_does_not_remove_scope_or_audit(self):
  self.seed();push([grade()]);before=pull();execute(MIGRATION.read_text());self.assertEqual(3,status()['academicScope']);self.assertEqual(before,pull())
 def test_19_non_c_entity_is_not_transmitted(self):
  self.seed();execute(statement([f.change('lesson_records',dict(id='journal',classId='class-fixture',date='2026-10-20',title='Local',description='Preservado'))]),A,fails=True)
 def test_20_lessons_and_activities_cannot_change_class_identity(self):
  self.seed();push(f.classroom('other'))
  for kind,payload in [('lessons',dict(f.lesson(),classId='other')),('activities',dict(f.activity(),classId='other'))]:execute(statement([f.change(kind,payload,1)]),A,fails=True)
if __name__=='__main__':unittest.main(verbosity=2)
