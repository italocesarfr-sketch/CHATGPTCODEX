#!/usr/bin/env python3
"""Real, disposable PostgreSQL/RLS tests. Never targets Supabase or a production DB.
Uses a LOCAL auth.uid() fixture, not real signed JWT authentication. No student data.
PGDATABASE=noctua_sync_test PGHOST=127.0.0.1 python3 scripts/test-sync-postgres.py
"""
import concurrent.futures
import copy
import json
import os
from pathlib import Path
import subprocess
import unittest
import uuid

if os.environ.get('PGDATABASE') != 'noctua_sync_test' or os.environ.get('PGHOST') not in ('127.0.0.1', 'localhost', '/workspace/pg-local'):
    raise SystemExit('Requires an explicitly selected disposable LOCAL noctua_sync_test database.')
PSQL=os.environ.get('PSQL_BIN','psql')
A='11111111-1111-4111-8111-111111111111'
B='22222222-2222-4222-8222-222222222222'
UNCONFIRMED='33333333-3333-4333-8333-333333333333'
SQL=Path(__file__).resolve().parents[1]/'supabase/migrations/202610300001_class_sync.sql'

def execute(sql, owner=None, role='authenticated', fails=False):
    if owner is not None:
        sql=f"SET ROLE {role}; SET request.jwt.claim.sub='{owner}';\n"+sql
    result=subprocess.run([PSQL,'-XAtq','-v','ON_ERROR_STOP=1'],input=sql,text=True,capture_output=True)
    if fails:
        assert result.returncode != 0, 'Unsafe statement unexpectedly succeeded'
        return result.stderr
    if result.returncode:
        raise AssertionError(result.stderr)
    return result.stdout.strip()

def literal(value):
    return "'"+json.dumps(value,ensure_ascii=False).replace("'","''")+"'::jsonb"

def bundle(id='class-fixture', name='Turma Fictícia Ç'):
    return {'school':dict(id=id,name=name,stage='Médio',subject='História',year=2026,shift='Matutino',schedule='0,0,0,0,0,0,0',duration=50,scale=10.0,low=5.0,high=7.0,countJustified=True,startPresent=False),
            'terms':[dict(classId=id,number=n,start=f'2026-{m:02}-01',end=f'2026-{m+2:02}-{d}') for n,m,d in [(1,1,31),(2,4,30),(3,7,30),(4,10,31)]]}

def statement(value,base=0,op=None,deleted=False):
    return f"SELECT public.noctua_push_class('{op or uuid.uuid4()}', '{value['school']['id']}', {base}, {'true' if deleted else 'false'}, {'NULL' if deleted else literal(value)});"

execute("""
DO $$ BEGIN IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='anon') THEN CREATE ROLE anon NOLOGIN; END IF;
IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='authenticated') THEN CREATE ROLE authenticated NOLOGIN; END IF; END $$;
CREATE SCHEMA IF NOT EXISTS auth;
CREATE TABLE IF NOT EXISTS auth.users(id uuid PRIMARY KEY,email_confirmed_at timestamptz,is_anonymous boolean);
CREATE OR REPLACE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS 'SELECT nullif(current_setting(''request.jwt.claim.sub'',true),'''')::uuid';
GRANT USAGE ON SCHEMA auth TO authenticated,anon;
GRANT EXECUTE ON FUNCTION auth.uid() TO authenticated,anon;
""")
execute(SQL.read_text())

class ClassSyncPostgresTest(unittest.TestCase):
    def setUp(self):
        execute('TRUNCATE public.noctua_sync_receipts,public.noctua_class_versions,public.noctua_class_bundles,public.noctua_sync_heads,auth.users;')
        execute(f"INSERT INTO auth.users VALUES('{A}',now(),false),('{B}',now(),false),('{UNCONFIRMED}',NULL,false);")
    def push(self,value=None,base=0,op=None,owner=A,deleted=False):
        return json.loads(execute(statement(value or bundle(),base,op,deleted),owner))
    def pull(self,owner=A,after=0):return json.loads(execute(f'SELECT public.noctua_pull_classes({after});',owner))
    def test_01_create_and_retrieve_by_same_identity(self):
        result=self.push();self.assertTrue(result['accepted']);self.assertEqual(bundle(),json.loads(self.pull()[0]['payload']))
    def test_02_other_owner_cannot_pull_or_select_rows(self):
        self.push();self.assertEqual([],self.pull(B))
        for table in ['noctua_sync_heads','noctua_class_bundles','noctua_class_versions','noctua_sync_receipts']:
            self.assertEqual('0',execute(f'SELECT count(*) FROM public.{table};',B))
    def test_03_direct_ownership_forgery_is_rejected(self):
        execute(f"INSERT INTO public.noctua_class_bundles VALUES('{A}','forged',1,1,true,NULL);",B,fails=True)
    def test_04_direct_update_and_delete_are_rejected(self):
        self.push();execute("UPDATE public.noctua_class_bundles SET revision=99;",A,fails=True)
        execute("DELETE FROM public.noctua_class_bundles;",A,fails=True);self.assertEqual(1,self.pull()[0]['revision'])
    def test_05_duplicate_operation_returns_same_receipt(self):
        op=str(uuid.uuid4());first=self.push(op=op);self.assertEqual(first,self.push(op=op))
        self.assertEqual('1',execute('SELECT count(*) FROM public.noctua_class_versions;'))
    def test_06_reused_operation_with_different_payload_is_rejected(self):
        op=str(uuid.uuid4());self.push(op=op);execute(statement(bundle(name='Diferente'),op=op),A,fails=True)
        self.assertEqual('Turma Fictícia Ç',json.loads(self.pull()[0]['payload'])['school']['name'])
    def test_07_stale_revision_preserves_remote_and_reports_conflict(self):
        self.push();r=self.push(bundle(name='Outra edição'),base=0);self.assertFalse(r['accepted']);self.assertEqual(1,r['record']['revision'])
    def test_08_two_devices_concurrently_compare_and_swap(self):
        self.push()
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            results=list(pool.map(lambda n:self.push(bundle(name=f'Dispositivo {n}'),base=1),range(2)))
        self.assertEqual([False,True],sorted(r['accepted'] for r in results))
        self.assertEqual('2',execute('SELECT count(*) FROM public.noctua_class_versions;'))
    def test_09_older_offline_operation_cannot_overwrite_newer_revision(self):
        self.push();self.push(bundle(name='Recente'),1);old=self.push(bundle(name='Antiga'),0)
        self.assertFalse(old['accepted']);self.assertEqual('Recente',json.loads(old['record']['payload'])['school']['name'])
    def test_10_tombstone_preserves_previous_version(self):
        self.push();self.push(base=1,deleted=True);self.assertTrue(self.pull()[0]['deleted'])
        self.assertEqual('2',execute('SELECT count(*) FROM public.noctua_class_versions;'))
        self.assertFalse(self.push(base=0)['accepted'])
    def test_11_rollback_leaves_no_partial_receipt_or_revision(self):
        execute('BEGIN;'+statement(bundle())+'ROLLBACK;',A)
        self.assertEqual([],self.pull());self.assertEqual('0',execute('SELECT count(*) FROM public.noctua_sync_receipts;'))
    def test_12_cursor_returns_chronological_account_changes(self):
        self.push(bundle('first'));self.push(bundle('second'));self.push(bundle('first','Atualizada'),1)
        self.assertEqual([2,3],[r['sequence'] for r in self.pull(after=1)])
    def test_13_same_id_in_different_accounts_remains_separate(self):
        self.push();self.push(bundle(name='Outra conta'),owner=B)
        self.assertEqual('Turma Fictícia Ç',json.loads(self.pull()[0]['payload'])['school']['name'])
        self.assertEqual('Outra conta',json.loads(self.pull(B)[0]['payload'])['school']['name'])
    def test_14_unconfirmed_and_unknown_identity_are_rejected(self):
        execute(statement(bundle()),UNCONFIRMED,fails=True)
        execute(statement(bundle()),str(uuid.uuid4()),fails=True)
    def test_15_anonymous_and_no_identity_are_rejected(self):
        execute("SET ROLE anon;SELECT public.noctua_pull_classes(0);",fails=True)
        execute("SET ROLE authenticated;SELECT public.noctua_pull_classes(0);",fails=True)
    def test_16_malformed_class_is_rejected_atomically(self):
        for field,value in [('scale',0),('year',None),('countJustified',None),('duration','50')]:
            bad=bundle();bad['school'][field]=value;execute(statement(bad),A,fails=True)
        self.assertEqual([],self.pull())
    def test_17_wrong_term_identity_overlap_or_count_is_rejected(self):
        for change in ('identity','overlap','count'):
            bad=bundle()
            if change=='identity':bad['terms'][0]['classId']='other'
            if change=='overlap':bad['terms'][1]['start']='2026-02-01'
            if change=='count':bad['terms'].pop()
            execute(statement(bad),A,fails=True)
    def test_18_unexpected_student_data_is_rejected(self):
        bad=bundle();bad['students']=[];execute(statement(bad),A,fails=True)
    def test_19_migration_is_reproducible_and_preserves_records(self):
        self.push();before=self.pull();execute(SQL.read_text());self.assertEqual(before,self.pull())
    def test_20_owner_commits_are_ordered_before_cursor_can_advance(self):
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            a=pool.submit(execute,'BEGIN;'+statement(bundle('slow'))+'SELECT pg_sleep(0.2);COMMIT;',A)
            b=pool.submit(execute,statement(bundle('fast')),A);a.result();b.result()
        rows=self.pull();self.assertEqual([1,2],[r['sequence'] for r in rows]);self.assertEqual(1,len(self.pull(after=1)))
    def test_21_negative_cursor_is_rejected(self):execute('SELECT public.noctua_pull_classes(-1);',A,fails=True)
    def test_22_all_tables_have_rls_and_no_public_write_grants(self):
        self.assertEqual('4',execute("SELECT count(*) FROM pg_class WHERE relname IN ('noctua_sync_heads','noctua_class_bundles','noctua_class_versions','noctua_sync_receipts') AND relrowsecurity;"))
        self.assertEqual('0',execute("SELECT count(*) FROM information_schema.table_privileges WHERE table_name LIKE 'noctua_%' AND grantee IN ('authenticated','anon','PUBLIC') AND privilege_type IN ('INSERT','UPDATE','DELETE','TRUNCATE');"))
    def test_23_confirmation_is_checked_for_direct_reads_as_well_as_rpc(self):
        self.push();execute(f"UPDATE auth.users SET email_confirmed_at=NULL WHERE id='{A}';")
        for table in ['noctua_sync_heads','noctua_class_bundles','noctua_class_versions','noctua_sync_receipts']:
            self.assertEqual('0',execute(f'SELECT count(*) FROM public.{table};',A))
        execute('SELECT public.noctua_pull_classes(0);',A,fails=True)
        self.assertEqual('1',execute('SELECT count(*) FROM public.noctua_class_bundles;'))

if __name__=='__main__':unittest.main(verbosity=2)
