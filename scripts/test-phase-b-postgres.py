#!/usr/bin/env python3
"""Disposable PostgreSQL phase B. Auth claims are fixtures, not hosted Supabase sessions."""
import os,importlib.util,json,uuid,concurrent.futures,unittest
from pathlib import Path
if os.environ.get('PGDATABASE') not in ('noctua_phase_b_test','noctua_phase_c_test'):raise SystemExit('Requires disposable noctua_phase_b_test')
spec=importlib.util.spec_from_file_location('academic_fixture',Path(__file__).with_name('test-academic-postgres.py'))
f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f)
execute=f.execute;literal=f.literal;A=f.A;B=f.B
MIGRATION=f.ROOT/'supabase/migrations/202611020001_phase_b_fence.sql'
execute(MIGRATION.read_text())
TABLES=['noctua_academic_test_accounts','noctua_sync_protocols']+f.TABLES+['noctua_sync_receipts','noctua_class_versions','noctua_class_bundles','noctua_sync_heads']
def reset():
    execute('TRUNCATE '+','.join('public.'+t for t in TABLES)+',auth.users,auth.sessions;')
    execute(f"INSERT INTO auth.users VALUES('{A}',now(),false),('{B}',now(),false),('{f.UNCONFIRMED}',NULL,false);")
    for owner,sid in f.SESSION.items():execute(f"INSERT INTO auth.sessions VALUES('{sid}','{owner}',NULL);")
    execute(f"INSERT INTO public.noctua_academic_test_accounts VALUES('{A}',true),('{B}',true);")
def status(owner=A):return json.loads(execute('SELECT public.noctua_academic_status();',owner))
def activate(owner=A,expected=None):
    s=expected or status(owner)
    return json.loads(execute(f"SELECT public.noctua_activate_academic({s['v1Sequence']},{literal(s['v1'])});",owner))
def push(rows,owner=A,op=None):return json.loads(execute(f.push_sql(rows,op),owner))
def pull(owner=A):return json.loads(execute('SELECT public.noctua_pull_academic(0);',owner))

class PhaseBPostgresTest(unittest.TestCase):
    def setUp(self):reset()
    def test_01_admin_opt_in_authorizes_nobody_by_default_and_does_not_disable_v1(self):
        execute('DELETE FROM public.noctua_academic_test_accounts;')
        self.assertFalse(status()['testAllowed'])
        execute("SELECT public.noctua_activate_academic(0,'[]');",A,fails=True)
        self.assertTrue(json.loads(execute(f.statement(f.bundle()),A))['accepted'])
        self.assertEqual(1,status()['protocol'])
    def test_02_authorized_transition_blocks_old_writers_and_private_helpers(self):
        self.assertEqual(2,activate()['protocol'])
        execute(f.statement(f.bundle()),A,fails=True)
        execute('SELECT public.noctua_pull_classes(0);',A,fails=True)
        for call in [f"noctua_v1_push_class('{uuid.uuid4()}','x',0,false,'{{}}')",'noctua_v1_pull_classes(0)',f"noctua_v2_push_unfenced('{uuid.uuid4()}','[]')",'noctua_v2_pull_unfenced(0)']:
            self.assertIn('permission denied',execute('SELECT public.'+call+';',A,fails=True))
    def test_03_v1_class_and_four_terms_seed_atomically_without_reassigning_ids(self):
        execute(f.statement(f.bundle()),A);old=execute('SELECT jsonb_agg(to_jsonb(v)) FROM public.noctua_class_versions v;',A)
        first=activate();self.assertEqual(2,first['protocol']);self.assertEqual(5,len(pull()[0]['records']))
        self.assertEqual(old,execute('SELECT jsonb_agg(to_jsonb(v)) FROM public.noctua_class_versions v;',A))
        activate();self.assertEqual(1,len(pull()))
        rows=pull()[0]['records'];self.assertEqual(f.bundle()['school'],next(r['payload'] for r in rows if r['kind']=='classes'))
    def test_04_changed_v1_inventory_blocks_transition_and_preserves_old_writer(self):
        before=status();execute(f.statement(f.bundle()),A)
        execute(f"SELECT public.noctua_activate_academic({before['v1Sequence']},{literal(before['v1'])});",A,fails=True)
        self.assertEqual(1,status()['protocol']);self.assertTrue(json.loads(execute(f.statement(f.bundle(name='Nova'),1),A))['accepted'])
    def test_05_v1_write_and_transition_share_the_same_owner_lock(self):
        before=status()
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            def change():
                try:return ('write',json.loads(execute(f.statement(f.bundle()),A))['accepted'])
                except AssertionError:return ('write',False)
            def transition():
                try:return ('transition',activate(expected=before)['protocol']==2)
                except AssertionError:return ('transition',False)
            a=pool.submit(change);b=pool.submit(transition)
            results=dict([a.result(),b.result()])
        self.assertEqual(1,sum(results.values()))
    def test_06_thirty_six_enrollments_keep_person_and_enrollment_ids_distinct(self):
        activate();rows=f.classroom()+[f.change('students',dict(f.student(f'student-{n}',number=n),personId=f'person-{n}',name='Homônimo Fictício')) for n in range(1,37)]
        self.assertTrue(push(rows)['accepted']);records=pull()[0]['records']
        self.assertEqual(41,len(records));self.assertEqual(36,len([r for r in records if r['kind']=='students']))
        self.assertEqual(set(range(1,37)),{r['payload']['number'] for r in records if r['kind']=='students'})
    def test_07_other_account_cannot_read_or_change_known_identity(self):
        activate();push(f.classroom());activate(B)
        self.assertEqual([],pull(B));r=push([f.change('classes',f.bundle(name='B overwrite')['school'],1)],B)
        self.assertFalse(r['accepted']);self.assertEqual('Turma Fictícia Ç',pull()[0]['records'][0]['payload']['name'])
        for table in TABLES:self.assertEqual('0',execute(f"SELECT count(*) FROM public.{table} WHERE owner_id='{A}';",B))
    def test_08_client_cannot_authorize_itself_or_change_the_fence(self):
        for table in ('noctua_academic_test_accounts','noctua_sync_protocols'):
            execute(f'DELETE FROM public.{table};',A,fails=True)
            execute(f"UPDATE public.{table} SET owner_id='{B}';",A,fails=True)
    def test_09_year_person_and_class_identity_are_immutable(self):
        activate();push(f.classroom()+[f.change('students',f.student())])
        for rows in [[f.change('classes',dict(f.bundle()['school'],year=2027),1)],[f.change('students',dict(f.student(),personId='different'),1)],
                     [f.change('students',dict(f.student(),classId='different'),1)]]:
            execute(f.push_sql(rows),A,fails=True)
        self.assertEqual(1,len(pull()))
    def test_10_duplicate_call_number_rolls_back_whole_batch(self):
        activate();push(f.classroom()+[f.change('students',f.student())])
        execute(f.push_sql([f.change('students',f.student('duplicate')),f.change('classes',f.bundle(name='Do not change')['school'],1)]),A,fails=True)
        self.assertEqual(1,len(pull()));self.assertEqual('1',execute("SELECT count(*) FROM public.noctua_academic_records WHERE kind='students';",A))
    def test_11_call_number_swap_in_one_transaction_is_allowed(self):
        activate();push(f.classroom()+[f.change('students',f.student()),f.change('students',f.student('s2',number=2))])
        self.assertTrue(push([f.change('students',f.student(number=2),1),f.change('students',f.student('s2',number=1),1)])['accepted'])
    def test_12_unsupported_entities_and_missing_movement_chain_are_rejected(self):
        activate();push(f.classroom())
        for rows in [[f.change('activities',f.activity())],[f.change('students',dict(f.student(),status='Remanejado',left='2026-10-19'))],
                     [f.change('students',dict(f.student(),status='Transferido'))]]:execute(f.push_sql(rows),A,fails=True)
        self.assertEqual(1,len(pull()))
    def test_13_revoke_test_permission_preserves_fence_and_versions(self):
        activate();push(f.classroom());execute(f"UPDATE public.noctua_academic_test_accounts SET allowed=false WHERE owner_id='{A}';")
        execute(f.push_sql([f.change('classes',f.bundle(name='denied')['school'],1)]),A,fails=True)
        execute('SELECT public.noctua_pull_academic(0);',A,fails=True);execute(f.statement(f.bundle()),A,fails=True)
        self.assertEqual('5',execute('SELECT count(*) FROM public.noctua_academic_versions;'))
    def test_14_reapplying_only_phase_b_preserves_fence_and_receipts(self):
        activate();op=str(uuid.uuid4());first=push(f.classroom(),op=op);execute(MIGRATION.read_text())
        self.assertEqual(first,push(f.classroom(),op=op));execute(f.statement(f.bundle()),A,fails=True)
    def test_15_revoked_unconfirmed_anonymous_and_foreign_session_are_denied(self):
        activate();push(f.classroom());execute(f"DELETE FROM auth.sessions WHERE id='{f.SESSION[A]}';")
        for call in ['noctua_academic_status()','noctua_pull_academic(0)',"noctua_activate_academic(0,'[]')"]:execute('SELECT public.'+call+';',A,fails=True)
        execute('SELECT public.noctua_academic_status();',f.UNCONFIRMED,fails=True)
        execute('SET ROLE anon;SELECT public.noctua_academic_status();',fails=True)
    def test_16_tombstoned_identity_cannot_be_reused_for_another_person(self):
        activate();push(f.classroom()+[f.change('students',f.student())]);push([f.change('students',None,1,{'id':'student-fixture'})])
        execute(f.push_sql([f.change('students',dict(f.student(),personId='new-person'),2)]),A,fails=True)
    def test_17_two_active_sessions_of_same_owner_recover_identical_records(self):
        activate();push(f.classroom());sid=str(uuid.uuid4());execute(f"INSERT INTO auth.sessions VALUES('{sid}','{A}',NULL);")
        claims=dict(sub=A,role='authenticated',session_id=sid,exp=int(f.time.time())+3600)
        self.assertEqual(pull(),json.loads(execute('SELECT public.noctua_pull_academic(0);',A,claims=claims)))
    def test_18_unmanaged_phase_a_data_cannot_be_assumed_by_transition(self):
        # Administrative fixture represents an earlier manual experiment, not a client bypass.
        changes=f.classroom();execute(f"SET request.jwt.claim.sub='{A}'; SET request.jwt.claims='{json.dumps(dict(sub=A,role='authenticated',session_id=f.SESSION[A],exp=int(f.time.time())+3600))}'; SELECT public.noctua_v2_push_unfenced('{uuid.uuid4()}',{literal(changes)});")
        execute("SELECT public.noctua_activate_academic(0,'[]');",A,fails=True);self.assertEqual(1,status()['protocol'])

if __name__=='__main__':unittest.main(verbosity=2)
