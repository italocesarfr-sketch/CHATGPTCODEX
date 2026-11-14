#!/usr/bin/env python3
"""E2/E3, only disposable LOCAL PostgreSQL with fixture principals."""
import os,importlib.util,json,uuid,unittest,copy
from pathlib import Path
if os.environ.get('PGDATABASE')!='noctua_phase_e_test':raise SystemExit('Disposable noctua_phase_e_test required')
spec=importlib.util.spec_from_file_location('paging',Path(__file__).with_name('test-phase-e-postgres.py'))
e=importlib.util.module_from_spec(spec);spec.loader.exec_module(e)
e.execute((e.f.ROOT/'supabase/migrations/202611030002_phase_e_decisions.sql').read_text())
A=e.A;B=e.B;execute=e.execute;literal=e.literal
class PhaseEDecisionPostgresTest(unittest.TestCase):
 def setUp(self):e.b.reset();e.b.activate();e.d.c.enable();e.d.enable();e.enable();e.push(e.d.rows())
 def journal(self,text):return dict(e.d.SNAP['lessonRecords'][0],description=text)
 def current(self):
  r=self.journal('Remota');e.push([e.f.change('lesson_records',r,1)]);return r
 def request(self,choice='KEEP_LOCAL',source='CONFLICT',head=2,local=None,kind='lesson_records',key=None,base=1,revision=2):
  local=local if local is not None else self.journal('Local')
  return dict(expectedSequence=head,choice=choice,source=source,localDigest='a'*64,hasChanges=choice=='KEEP_LOCAL',evidence=[dict(kind=kind,key=key or {'id':local['id']},baseRevision=base,revision=revision,local=local)])
 def resolve(self,request,rows=None,owner=A,op=None,cancel=False):
  op=op or str(uuid.uuid4())
  if rows:
   _,parts,_=e.begin(rows,owner,op)
   for n,part in enumerate(parts):e.stage(op,n,part,owner)
  return op,json.loads(execute(f"SELECT public.noctua_resolve_academic_e('{op}',{literal(request)},{str(cancel).lower()});",owner))
 def test_01_explicit_local_choice_creates_revision_and_preserves_both_variants(self):
  self.current();local=self.journal('Local');op,r=self.resolve(self.request(),[e.f.change('lesson_records',local,2)])
  self.assertTrue(r['accepted']);self.assertEqual(3,r['sequence'])
  audit=json.loads(execute('SELECT evidence FROM public.noctua_e_decisions;',A))[0]
  self.assertEqual('Local',audit['local']['description']);self.assertEqual('Remota',audit['remote']['description'])
  self.assertEqual('3',execute("SELECT count(*) FROM public.noctua_academic_versions WHERE kind='lesson_records' AND key="+literal({'id':local['id']})+';',A))
 def test_02_remote_choice_records_audit_without_an_unnecessary_write(self):
  self.current();_,r=self.resolve(self.request('KEEP_REMOTE'));self.assertTrue(r['accepted']);self.assertEqual(2,r['sequence'])
  self.assertEqual('1',execute('SELECT count(*) FROM public.noctua_e_decisions;',A))
 def test_03_remote_head_change_preserves_negative_decision_and_never_overwrites(self):
  self.current();req=self.request();e.push([e.f.change('lesson_records',self.journal('Mais recente'),2)])
  op,r=self.resolve(req,[e.f.change('lesson_records',self.journal('Local'),2)]);self.assertFalse(r['accepted'])
  self.assertEqual(r,json.loads(execute(f"SELECT public.noctua_decision_academic_e('{op}');",A)))
  self.assertEqual(3,e.d.status()['academicSequence'])
 def test_04_decision_identity_is_idempotent_but_cannot_change_choice(self):
  self.current();req=self.request('KEEP_REMOTE');op,r=self.resolve(req);self.assertEqual(r,self.resolve(req,op=op)[1])
  bad=dict(req,choice='KEEP_LOCAL');execute(f"SELECT public.noctua_resolve_academic_e('{op}',{literal(bad)},false);",A,fails=True)
 def test_05_false_local_choice_without_staged_write_is_rejected(self):
  self.current();req=self.request();req['hasChanges']=False
  execute(f"SELECT public.noctua_resolve_academic_e('{uuid.uuid4()}',{literal(req)},false);",A,fails=True)
  self.assertEqual('0',execute('SELECT count(*) FROM public.noctua_e_decisions;',A))
 def test_06_unknown_base_or_remote_revision_is_not_accepted_as_evidence(self):
  self.current();req=self.request('KEEP_REMOTE');req['evidence'][0]['revision']=99
  execute(f"SELECT public.noctua_resolve_academic_e('{uuid.uuid4()}',{literal(req)},false);",A,fails=True)
 def test_07_cancellation_does_not_commit_and_closes_abandoned_transfer_quota(self):
  self.current()
  for i in range(5):
   _,r=self.resolve(self.request(),[e.f.change('lesson_records',self.journal('Local'),2)],cancel=True);self.assertFalse(r['accepted'])
  self.assertEqual(2,e.d.status()['academicSequence']);self.assertEqual('5',execute('SELECT count(*) FROM public.noctua_e_decisions;',A))
 def test_08_confirmed_audit_cannot_be_deleted_or_rewritten(self):
  self.current();self.resolve(self.request('KEEP_REMOTE'))
  for sql in ['DELETE FROM public.noctua_e_decisions;','UPDATE public.noctua_e_decisions SET evidence=\'[]\';']:execute(sql,A,fails=True)
 def test_09_other_account_cannot_read_known_decision_or_touch_academic_data(self):
  self.current();op,_=self.resolve(self.request('KEEP_REMOTE'));e.b.activate(B);e.d.c.enable(B);e.d.enable(B);e.enable(B)
  self.assertEqual('0',execute(f"SELECT count(*) FROM public.noctua_e_decisions WHERE owner_id='{A}';",B))
  self.assertEqual('',execute(f"SELECT public.noctua_decision_academic_e('{op}');",B))
 def test_10_unconfirmed_and_revoked_sessions_cannot_resolve(self):
  self.current();req=self.request('KEEP_REMOTE')
  execute(f"SELECT public.noctua_resolve_academic_e('{uuid.uuid4()}',{literal(req)},false);",e.f.UNCONFIRMED,fails=True)
  execute(f"DELETE FROM auth.sessions WHERE user_id='{A}';")
  execute(f"SELECT public.noctua_resolve_academic_e('{uuid.uuid4()}',{literal(req)},false);",A,fails=True)
 def test_11_restore_absence_does_not_delete_a_remote_enrollment(self):
  student=e.d.SNAP['students'][2]
  req=dict(expectedSequence=1,choice='KEEP_LOCAL',source='RESTORE',localDigest='b'*64,hasChanges=False,evidence=[dict(kind='students',key={'id':student['id']},baseRevision=1,revision=1,local=None)])
  _,r=self.resolve(req);self.assertTrue(r['accepted']);self.assertEqual('37',execute("SELECT count(*) FROM public.noctua_academic_records WHERE kind='students' AND NOT deleted;",A))
 def test_12_restore_remote_choice_retains_local_only_identity_explicitly(self):
  student=dict(e.d.SNAP['students'][2],id='restored-only',personId='restored-person',number=40)
  req=self.request('KEEP_REMOTE','RESTORE',1,student,'students',{'id':student['id']},0,0);req['hasChanges']=True
  _,r=self.resolve(req,[e.f.change('students',student)]);self.assertTrue(r['accepted']);self.assertEqual('38',execute("SELECT count(*) FROM public.noctua_academic_records WHERE kind='students' AND NOT deleted;",A))
 def test_13_movement_audits_cannot_be_bypassed_by_a_resolution(self):
  relocation=copy.deepcopy(e.d.SNAP['relocations'][0]);relocation['corrections']=[]
  req=self.request(local=relocation,kind='relocations',key={'sourceStudentId':relocation['sourceStudentId']},revision=1,head=1)
  op,parts,_=e.begin([e.f.change('relocations',relocation,1)])
  for n,p in enumerate(parts):e.stage(op,n,p)
  execute(f"SELECT public.noctua_resolve_academic_e('{op}',{literal(req)},false);",A,fails=True)
  self.assertEqual('0',execute('SELECT count(*) FROM public.noctua_e_decisions;',A));self.assertEqual(1,e.d.status()['academicSequence'])
 def test_14_zero_and_null_are_distinct_audited_values(self):
  grade=dict(e.d.SNAP['grades'][0],value=None);key={'activityId':grade['activityId'],'studentId':grade['studentId']}
  e.push([e.f.change('grades',grade,1)]);local=dict(grade,value=0)
  req=self.request(local=local,kind='grades',key=key)
  _,r=self.resolve(req,[e.f.change('grades',local,2)]);self.assertTrue(r['accepted'])
  audit=json.loads(execute('SELECT evidence FROM public.noctua_e_decisions;',A))[0]
  self.assertIsNone(audit['remote']['value']);self.assertEqual(0,audit['local']['value'])
 def test_15_cancel_after_lost_confirmation_returns_original_confirmed_receipt(self):
  self.current();req=self.request();op,r=self.resolve(req,[e.f.change('lesson_records',self.journal('Local'),2)])
  self.assertEqual(r,self.resolve(req,op=op,cancel=True)[1]);self.assertEqual(3,e.d.status()['academicSequence'])
 def numeric_conflict(self):
  row=copy.deepcopy(e.d.SNAP['grades'][1]);self.assertEqual('ds2',row['studentId'])
  row['value']=4;e.push([e.f.change('grades',row,1)])
  row['value']=8;e.push([e.f.change('grades',row,2)])
  local=dict(row,value=6);key={'activityId':row['activityId'],'studentId':row['studentId']}
  return local,key
 def test_16_higher_numeric_remote_choice_retains_values_and_does_not_rewrite_grade(self):
  local,key=self.numeric_conflict();req=self.request(choice='KEEP_REMOTE',kind='grades',key=key,base=2,revision=3,head=3,local=local)
  _,r=self.resolve(req);self.assertTrue(r['accepted']);self.assertEqual(3,r['sequence'])
  ev=json.loads(execute('SELECT evidence FROM public.noctua_e_decisions;',A))[0]
  self.assertEqual(6,ev['local']['value']);self.assertEqual(8,ev['remote']['value'])
  self.assertEqual('3',execute("SELECT revision FROM public.noctua_academic_records WHERE kind='grades' AND key="+literal(key)+';',A))
 def test_17_lower_numeric_local_choice_has_one_revision_and_idempotent_receipt(self):
  local,key=self.numeric_conflict();req=self.request(kind='grades',key=key,base=2,revision=3,head=3,local=local)
  op,r=self.resolve(req,[e.f.change('grades',local,3)]);self.assertTrue(r['accepted']);self.assertEqual(4,r['sequence'])
  self.assertEqual(r,self.resolve(req,op=op)[1]);self.assertEqual('4',execute("SELECT revision FROM public.noctua_academic_records WHERE kind='grades' AND key="+literal(key)+';',A))
  ev=json.loads(execute('SELECT evidence FROM public.noctua_e_decisions;',A))[0]
  self.assertEqual((6,8),(ev['local']['value'],ev['remote']['value']))
 def test_18_third_numeric_revision_rejects_frozen_decision_without_overwrite(self):
  local,key=self.numeric_conflict();req=self.request(kind='grades',key=key,base=2,revision=3,head=3,local=local)
  e.push([e.f.change('grades',dict(local,value=9),3)])
  _,r=self.resolve(req,[e.f.change('grades',local,3)]);self.assertFalse(r['accepted'])
  self.assertEqual('9',execute("SELECT payload->>'value' FROM public.noctua_academic_records WHERE kind='grades' AND key="+literal(key)+';',A))
  self.assertEqual(4,e.d.status()['academicSequence'])
if __name__=='__main__':unittest.main(verbosity=2)
