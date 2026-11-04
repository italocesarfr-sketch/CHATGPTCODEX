#!/usr/bin/env python3
"""Phase D, disposable LOCAL PostgreSQL. Fictitious auth claims; never hosted Supabase."""
import os,importlib.util,json,uuid,unittest,copy
from pathlib import Path
if os.environ.get('PGDATABASE') not in ('noctua_phase_d_test','noctua_phase_e_test'):raise SystemExit('Disposable noctua_phase_d_test required')
spec=importlib.util.spec_from_file_location('phasec',Path(__file__).with_name('test-phase-c-postgres.py'))
c=importlib.util.module_from_spec(spec);spec.loader.exec_module(c)
b=c.b;f=c.f;execute=c.execute;A=c.A;B=c.B;literal=c.literal
MIGRATION=f.ROOT/'supabase/migrations/202611020003_phase_d_scope.sql';execute(MIGRATION.read_text())
SNAP=json.loads((f.ROOT/'app/src/androidTest/assets/phase-d.json').read_text())
NAMES=dict(classes='classes',terms='terms',students='students',lessons='lessons',marks='marks',activities='activities',grades='grades',recompositions='recompositions',relocations='relocations',drafts='import_drafts',exportRows='export_rows',classArchives='class_archives',lessonRecords='lesson_records',histories='preserved_histories',timetables='weekly_timetables')
def rows(snap=SNAP):return [f.change(kind,p) for name,kind in NAMES.items() for p in snap[name]]
def status(owner=A):return c.status(owner)
def enable(owner=A,sequence=None):
 s=status(owner);return json.loads(execute(f"SELECT public.noctua_enable_academic_d({s['academicSequence'] if sequence is None else sequence});",owner))
def statement(changes,op=None):return f"SELECT public.noctua_push_academic_d('{op or uuid.uuid4()}',{literal(changes)});"
def push(changes,owner=A,op=None):return json.loads(execute(statement(changes,op),owner))
def pull(owner=A):
 pages=[];after=0
 while True:
  page=json.loads(execute(f'SELECT public.noctua_pull_academic_d({after});',owner))
  if not page:return pages
  assert len(page)==1 and page[0]['sequence']==after+1
  pages+=page;after=page[0]['sequence']
class PhaseDPostgresTest(unittest.TestCase):
 def setUp(self):b.reset();b.activate();c.enable()
 def seed(self):enable();return push(rows())
 def reject(self,snap):execute(statement(rows(snap)),A,fails=True);self.assertEqual([],pull())
 def test_01_scope_requires_explicit_consent_and_blocks_old_writers(self):
  execute(statement(rows()),A,fails=True);enable();self.assertEqual(4,status()['academicScope'])
  execute('SELECT public.noctua_enable_academic_c(0);',A,fails=True);self.assertEqual(4,status()['academicScope'])
  execute(c.statement(f.classroom()),A,fails=True);execute('SELECT public.noctua_pull_academic_c(0);',A,fails=True)
  execute(f.push_sql(f.classroom()),A,fails=True);execute(f.statement(f.bundle()),A,fails=True)
 def test_02_all_fifteen_types_recover_with_same_keys(self):
  self.assertTrue(self.seed()['accepted']);records=pull()[0]['records'];self.assertEqual(15,len({r['kind'] for r in records}));self.assertEqual(len(rows()),len(records))
  self.assertEqual({(r['kind'],json.dumps(r['key'],sort_keys=True)) for r in rows()},{(r['kind'],json.dumps(r['key'],sort_keys=True))for r in records})
 def test_03_other_account_cannot_read_or_change_known_records(self):
  self.seed();b.activate(B);c.enable(B);enable(B);self.assertEqual([],pull(B))
  x=f.change('lesson_records',SNAP['lessonRecords'][0],1);self.assertFalse(push([x],B)['accepted'])
  for table in b.TABLES:self.assertEqual('0',execute(f"SELECT count(*) FROM public.{table} WHERE owner_id='{A}';",B))
 def test_04_half_movement_is_rejected_atomically(self):
  enable();s=copy.deepcopy(SNAP);s['relocations']=[];self.reject(s)
 def test_05_internal_identity_mismatch_rolls_back_everything(self):
  enable();s=copy.deepcopy(SNAP);s['students'][-1]['personId']='other';self.reject(s)
 def test_06_missing_preserved_origin_is_not_partial_recovery(self):
  enable();s=copy.deepcopy(SNAP);s['histories'][0]['content']['relocations'][0]['sourceStudentId']='missing';self.reject(s)
 def test_07_audit_cannot_be_removed_or_rewritten(self):
  self.seed();r=copy.deepcopy(SNAP['relocations'][0]);r['corrections'][0]['previousTarget']='Forged'
  execute(statement([f.change('relocations',r,1)]),A,fails=True);self.assertEqual(1,len(pull()))
 def test_08_audit_removed_occurrence_cannot_reference_another_student(self):
  enable();s=copy.deepcopy(SNAP);s['relocations'][0]['corrections'][0]['removed'][0]['attendance']['studentId']='ds2';self.reject(s)
 def test_09_timetable_revision_cannot_be_changed_or_deleted(self):
  self.seed();t=copy.deepcopy(SNAP['timetables'][0]);t['effective']='2026-02-01';execute(statement([f.change('weekly_timetables',t,1)]),A,fails=True)
  execute(statement([f.change('weekly_timetables',{},1,key={'id':t['id']})|{'payload':None}]),A,fails=True)
 def test_10_legacy_weekend_and_deleted_class_slot_are_preserved(self):
  self.seed();t=[r['payload']for r in pull()[0]['records']if r['kind']=='weekly_timetables'];self.assertEqual(12,t[0]['slots'][1]['ordinal']);self.assertEqual(6,t[0]['slots'][1]['weekday'])
 def test_11_slot_conflicts_and_identity_changes_are_rejected(self):
  enable();s=copy.deepcopy(SNAP);s['timetables'][1]['slots'][0]['id']='d-slot-1';self.reject(s)
  s=copy.deepcopy(SNAP);s['timetables'][0]['slots'].append(s['timetables'][0]['slots'][0]|{'id':'conflict'});self.reject(s)
 def test_12_export_reorder_is_atomic_without_transient_unique_failure(self):
  self.seed();rs=copy.deepcopy(SNAP['exportRows']);rs[0]['position'],rs[2]['position']=2,0
  self.assertTrue(push([f.change('export_rows',r,1)for r in rs if r['studentId']is not None])['accepted']);self.assertEqual(2,len(pull()))
 def test_13_layout_cannot_omit_positions_or_hide_blank(self):
  enable();s=copy.deepcopy(SNAP);s['exportRows'][1]['position']=5;self.reject(s)
  s=copy.deepcopy(SNAP);s['exportRows'][1]['manualHidden']=True;self.reject(s)
 def test_14_replayed_receipt_is_idempotent(self):
  enable();op=str(uuid.uuid4());first=push(rows(),op=op);self.assertEqual(first,push(rows(),op=op));self.assertEqual(1,len(pull()))
 def test_15_journal_concurrency_preserves_versions(self):
  self.seed();r=copy.deepcopy(SNAP['lessonRecords'][0]);r['description']='First';self.assertTrue(push([f.change('lesson_records',r,1)])['accepted']);r['description']='Second';self.assertFalse(push([f.change('lesson_records',r,1)])['accepted']);self.assertEqual(2,len(pull()))
 def test_16_live_session_and_email_confirmation_are_required(self):
  self.seed();execute('SELECT public.noctua_pull_academic_d(0);',f.UNCONFIRMED,fails=True)
  execute(f"DELETE FROM auth.sessions WHERE user_id='{A}';");execute('SELECT public.noctua_pull_academic_d(0);',A,fails=True)
 def test_17_private_helpers_and_direct_writes_are_denied(self):
  self.seed()
  for call in [f"noctua_d_projection('{A}')",f"noctua_d_graph('{A}')","noctua_d_get('[]','students','{}')","noctua_d_subject('História')"]:
   self.assertIn('permission denied',execute('SELECT public.'+call+';',A,fails=True))
  for table in b.TABLES:execute(f'DELETE FROM public.{table};',A,fails=True)
 def test_18_cursor_transition_rejects_stale_expected_head(self):
  c.push(f.classroom());execute('SELECT public.noctua_enable_academic_d(0);',A,fails=True);self.assertEqual(3,status()['academicScope'])
 def test_19_draft_journal_archive_changes_do_not_modify_attendance(self):
  self.seed();r=copy.deepcopy(SNAP['lessonRecords'][0]);r['description']='Edição offline\nAcentuação & texto exato'
  self.assertTrue(push([f.change('lesson_records',r,1)])['accepted']);self.assertEqual(1,len(pull()[-1]['records']))
 def test_20_orphan_history_cannot_be_introduced(self):
  enable();s=copy.deepcopy(SNAP);s['histories'][0]['content']['relocations']=[];self.reject(s)
 def test_21_preserved_grade_cannot_be_rewritten_or_dropped(self):
  self.seed();h=copy.deepcopy(SNAP['histories'][0]);h['content']['grades'][0]['value']=9
  execute(statement([f.change('preserved_histories',h,1)]),A,fails=True)
  h=copy.deepcopy(SNAP['histories'][0]);h['content']['grades']=[]
  execute(statement([f.change('preserved_histories',h,1)]),A,fails=True);self.assertEqual(1,len(pull()))
 def test_22_historical_audit_survives_record_storage_transition(self):
  self.seed();h=copy.deepcopy(SNAP['histories'][0]);h['content']['students'][0]['personId']='forged'
  execute(statement([f.change('preserved_histories',h,1)]),A,fails=True);self.assertEqual(1,len(pull()))
 def test_23_source_deletion_moves_links_and_audits_into_history_atomically(self):
  self.seed();file=f.ROOT/'app/build/academic-phase-d-validation/source-deletion.json'
  self.assertTrue(file.is_file(),'Run JVM tests to generate the domain-validated transition')
  delta=json.loads(file.read_text());self.assertTrue(push(delta)['accepted'])
  histories=json.loads(execute("SELECT jsonb_agg(payload) FROM public.noctua_academic_records WHERE kind='preserved_histories' AND NOT deleted;",A))
  self.assertEqual({'d-past','d-source'},{h['classId']for h in histories})
  source=next(h for h in histories if h['classId']=='d-source');self.assertEqual(2,len(source['content']['relocations'][0]['corrections']))
  self.assertEqual('1',execute("SELECT count(*) FROM public.noctua_academic_records WHERE kind='students' AND NOT deleted;",A))
 def test_24_rejected_oversized_atomic_batch_leaves_no_partial_history(self):
  enable();self.assertTrue(len(rows())<256)
  execute(statement(rows()+[f.change('lesson_records',dict(SNAP['lessonRecords'][0],id=f'extra-{i}'))for i in range(256)]),A,fails=True)
  self.assertEqual([],pull())
 def test_25_unmarked_historical_lesson_is_still_preserved_for_enrollment(self):
  enable();s=copy.deepcopy(SNAP);s['histories'][0]['content']['lessons'].append(dict(s['histories'][0]['content']['lessons'][0],id='empty-historical',date='2026-04-11'))
  self.assertTrue(push(rows(s))['accepted'])
  execute(statement([f.change('preserved_histories',SNAP['histories'][0],1)]),A,fails=True);self.assertEqual(1,len(pull()))
 def test_26_confirmed_history_cannot_gain_fabricated_academic_records(self):
  self.seed();h=copy.deepcopy(SNAP['histories'][0]);h['content']['lessons'].append(dict(h['content']['lessons'][0],id='invented',date='2026-04-11'))
  execute(statement([f.change('preserved_histories',h,1)]),A,fails=True);self.assertEqual(1,len(pull()))
 def test_27_historical_lesson_period_cannot_be_duplicated_under_another_id(self):
  enable();s=copy.deepcopy(SNAP);h=s['histories'][0];h['content']['lessons'].append(dict(h['content']['lessons'][0],id='duplicate-period'))
  self.reject(s)
if __name__=='__main__':unittest.main(verbosity=2)
