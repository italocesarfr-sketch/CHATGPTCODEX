#!/usr/bin/env python3
"""Phase A: real, disposable PostgreSQL/RLS tests. Never targets Supabase or a production DB.
Uses a LOCAL auth.uid() fixture, not real signed JWT authentication. No student data.
PGDATABASE=noctua_academic_test PGHOST=127.0.0.1 python3 scripts/test-academic-postgres.py
"""
import concurrent.futures
import copy
import json
import os
from pathlib import Path
import subprocess
import unittest
import uuid
import time

if os.environ.get('PGDATABASE') not in ('noctua_academic_test','noctua_phase_b_test','noctua_phase_c_test','noctua_phase_d_test','noctua_phase_e_test') or os.environ.get('PGHOST') not in ('127.0.0.1', 'localhost', '/workspace/pg-local'):
    raise SystemExit('Requires an explicitly selected disposable LOCAL noctua_academic_test database.')
PSQL=os.environ.get('PSQL_BIN','psql')
A='11111111-1111-4111-8111-111111111111'
B='22222222-2222-4222-8222-222222222222'
UNCONFIRMED='33333333-3333-4333-8333-333333333333'
ROOT=Path(__file__).resolve().parents[1]
SQL=ROOT/'supabase/migrations/202610300001_class_sync.sql'
GUARD=ROOT/'supabase/migrations/202610310001_sync_session_guard.sql'
SESSION={owner:str(uuid.uuid5(uuid.NAMESPACE_URL,'noctua-fixture-session-'+owner)) for owner in (A,B,UNCONFIRMED)}

def execute(sql, owner=None, role='authenticated', fails=False, claims=None):
    if owner is not None:
        jwt=claims if claims is not None else {'sub':owner,'role':role,'session_id':SESSION.get(owner,str(uuid.uuid4())),'exp':int(time.time())+3600}
        encoded=json.dumps(jwt).replace("'","''")
        sql=f"SET ROLE {role}; SET request.jwt.claim.sub='{owner}'; SET request.jwt.claims='{encoded}';\n"+sql
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
CREATE TABLE IF NOT EXISTS auth.sessions(id uuid PRIMARY KEY,user_id uuid NOT NULL,not_after timestamptz);
CREATE OR REPLACE FUNCTION auth.jwt() RETURNS jsonb LANGUAGE sql STABLE AS 'SELECT coalesce(nullif(current_setting(''request.jwt.claims'',true),''''),''{}'')::jsonb';
CREATE OR REPLACE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS 'SELECT nullif(current_setting(''request.jwt.claim.sub'',true),'''')::uuid';
GRANT USAGE ON SCHEMA auth TO authenticated,anon;
GRANT EXECUTE ON FUNCTION auth.uid() TO authenticated,anon;
""")
ACADEMIC=ROOT/'supabase/migrations/202611010001_academic_protocol.sql'
execute(SQL.read_text()+GUARD.read_text()+ACADEMIC.read_text())

TABLES=['noctua_academic_'+name for name in ('links','uniques','versions','records','receipts','batches','heads')]

def change(kind,payload,base=0,key=None):
    definitions=json.loads((ROOT/'supabase/contracts/academic-v2.json').read_text())['entities']
    return dict(kind=kind,key=key or {k:payload[k] for k in definitions[kind]['key']},base=base,payload=payload)

def classroom(id='class-fixture',name='Turma Fictícia Ç'):
    value=bundle(id,name)
    return [change('classes',value['school'])]+[change('terms',t) for t in value['terms']]

def student(id='student-fixture',classId='class-fixture',number=1):
    return dict(id=id,personId='person-fixture',classId=classId,name='Estudante Fictício',number=number,
                joined='2026-01-01',left=None,status='Ativo',notes='')

def lesson(id='lesson-fixture',ordinal=1):
    return dict(id=id,classId='class-fixture',date='2026-10-20',ordinal=ordinal,state='REALIZADA',note='')

def activity(id='activity-fixture'):
    return dict(id=id,classId='class-fixture',date='2026-10-20',name='Produção fictícia',type='Produção textual',term=4,maximum=10,description='',notes='')

def push_sql(changes,op=None):
    return f"SELECT public.noctua_push_academic('{op or uuid.uuid4()}',{literal(changes)});"

class AcademicProtocolPostgresTest(unittest.TestCase):
    def setUp(self):
        old=['noctua_sync_receipts','noctua_class_versions','noctua_class_bundles','noctua_sync_heads']
        execute('TRUNCATE '+','.join('public.'+t for t in TABLES+old)+',auth.users,auth.sessions;')
        execute(f"INSERT INTO auth.users VALUES('{A}',now(),false),('{B}',now(),false),('{UNCONFIRMED}',NULL,false);")
        for owner,sid in SESSION.items():execute(f"INSERT INTO auth.sessions VALUES('{sid}','{owner}',NULL);")
    def push(self,changes,owner=A,op=None):return json.loads(execute(push_sql(changes,op),owner))
    def pull(self,owner=A,after=0):return json.loads(execute(f'SELECT public.noctua_pull_academic({after});',owner))
    def rows(self,kind,owner=A):return json.loads(execute(f"SELECT coalesce(jsonb_agg(payload ORDER BY key),'[]') FROM public.noctua_academic_records WHERE kind='{kind}' AND NOT deleted;",owner))
    def populated(self):
        return classroom()+[change('students',student()),change('lessons',lesson()),change('activities',activity())]

    def test_01_parent_children_are_atomic_and_recoverable_as_one_batch(self):
        result=self.push(self.populated());self.assertTrue(result['accepted'])
        remote=self.pull();self.assertEqual(1,len(remote));self.assertEqual(8,len(remote[0]['records']))
        self.assertEqual(4,len(self.rows('terms')));self.assertEqual('person-fixture',self.rows('students')[0]['personId'])
    def test_02_only_name_changes_with_local_academic_dependents(self):
        self.push(self.populated());before={k:self.rows(k) for k in ('students','terms','activities','lessons')}
        school=bundle(name='Nome corrigido')['school'];self.assertTrue(self.push([change('classes',school,1)])['accepted'])
        self.assertEqual('Nome corrigido',self.rows('classes')[0]['name'])
        self.assertEqual(before,{k:self.rows(k) for k in before})
    def test_03_zero_null_and_missing_grade_remain_distinct(self):
        self.push(self.populated()+[change('students',student('s2',number=2)),change('students',student('s3',number=3)),
            change('grades',dict(activityId='activity-fixture',studentId='student-fixture',value=0)),
            change('grades',dict(activityId='activity-fixture',studentId='s2',value=None))])
        grades=self.rows('grades');self.assertEqual(2,len(grades));self.assertEqual([None,0],[r['value'] for r in grades])
    def test_04_p_f_fj_nr_and_reason_roundtrip(self):
        changes=self.populated()
        for n,mark in enumerate(('P','F','FJ','NR'),1):
            if n>1:changes.append(change('lessons',lesson(f'lesson-{n}',n)))
            changes.append(change('marks',dict(lessonId='lesson-fixture' if n==1 else f'lesson-{n}',studentId='student-fixture',mark=mark,reason='Atestado médico' if mark=='FJ' else None)))
        self.push(changes);self.assertEqual({'P','F','FJ','NR'},{r['mark'] for r in self.rows('marks')})
        self.assertEqual('Atestado médico',next(r['reason'] for r in self.rows('marks') if r['mark']=='FJ'))
    def test_05_only_fj_can_have_reason_and_only_existing_categories(self):
        self.push(self.populated())
        for mark,reason in [('F','Atestado médico'),('P','Outro'),('FJ','inventado')]:
            execute(push_sql([change('marks',dict(lessonId='lesson-fixture',studentId='student-fixture',mark=mark,reason=reason))]),A,fails=True)
        self.assertEqual([],self.rows('marks'))
    def test_06_all_physical_room_entities_have_contract_and_fk_mapping(self):
        room=json.loads((ROOT/'app/schemas/br.com.diariodeclasse.data.DiaryDatabase/11.json').read_text())['database']['entities']
        definitions=json.loads((ROOT/'supabase/contracts/academic-v2.json').read_text())['entities']
        academic=[e for e in room if not e['tableName'].startswith('sync_')]
        self.assertEqual({e['tableName'] for e in academic},set(definitions));self.assertEqual(15,len(definitions))
        for e in academic:
            d=definitions[e['tableName']];self.assertEqual(set(f['fieldPath'] for f in e['fields']),set(d['fields']))
            self.assertEqual(e['primaryKey']['columnNames'],d['key'])
            self.assertEqual(e.get('foreignKeys',[]),[dict(table=f['kind'],onDelete=e['foreignKeys'][i]['onDelete'],onUpdate='NO ACTION',columns=f['fields'],referencedColumns=f['key']) for i,f in enumerate(d['references'])])
            self.assertEqual(d,json.loads(execute(f"SELECT public.noctua_academic_definition('{e['tableName']}');")))
    def test_07_repeat_after_lost_response_is_idempotent(self):
        op=str(uuid.uuid4());first=self.push(self.populated(),op=op)
        self.assertEqual(first,self.push(self.populated(),op=op));self.assertEqual(1,len(self.pull()))
        self.assertEqual('8',execute('SELECT count(*) FROM public.noctua_academic_versions;'))
    def test_08_operation_reuse_with_modified_body_is_rejected(self):
        op=str(uuid.uuid4());self.push(classroom(),op=op)
        execute(push_sql(classroom(name='Outra proposta'),op),A,fails=True)
        self.assertEqual('Turma Fictícia Ç',self.rows('classes')[0]['name'])
    def test_09_two_devices_same_row_only_one_accepted(self):
        self.push(classroom())
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            results=list(pool.map(lambda n:self.push([change('classes',bundle(name=f'Dispositivo {n}')['school'],1)]),range(2)))
        self.assertEqual([False,True],sorted(r['accepted'] for r in results))
        self.assertEqual(2,len(json.loads(execute('SELECT jsonb_agg(sequence) FROM public.noctua_academic_batches;',A))))
    def test_10_different_enrollments_do_not_conflict(self):
        self.push(classroom()+[change('students',student()),change('students',student('s2',number=2))])
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            changes=[student(),student('s2',number=2)]
            results=list(pool.map(lambda s:self.push([change('students',dict(s,notes='Atualizada'),1)]),changes))
        self.assertTrue(all(r['accepted'] for r in results));self.assertEqual(2,len(self.rows('students')))
    def test_11_one_stale_row_rejects_entire_batch_and_preserves_both_proposals(self):
        self.push(self.populated());self.push([change('classes',bundle(name='Remota')['school'],1)])
        batch=[change('classes',bundle(name='Offline antiga')['school'],1),change('students',dict(student(),notes='Não aplicar'),1)]
        result=self.push(batch);self.assertFalse(result['accepted']);self.assertEqual('',self.rows('students')[0]['notes'])
        self.assertEqual('Remota',self.rows('classes')[0]['name']);self.assertEqual(1,len(result['conflicts']))
        self.assertEqual(batch,json.loads(execute('SELECT request FROM public.noctua_academic_receipts WHERE NOT (response->>\'accepted\')::boolean;',A)))
    def test_12_missing_parent_rolls_back_all_changes_receipts_and_cursor(self):
        batch=classroom()+[change('students',student(classId='missing'))]
        execute(push_sql(batch),A,fails=True);self.assertEqual([],self.pull());self.assertEqual([],self.rows('classes'))
        self.assertEqual('0',execute('SELECT count(*) FROM public.noctua_academic_receipts;'))
    def test_13_existing_parent_in_other_account_cannot_satisfy_fk(self):
        self.push(classroom(),owner=B)
        execute(push_sql([change('students',student())]),A,fails=True)
        self.assertEqual([],self.rows('students',B));self.assertEqual([],self.pull())
    def test_14_direct_read_and_writes_are_isolated_for_all_seven_tables(self):
        self.push(self.populated())
        for table in TABLES:
            self.assertEqual('0',execute(f'SELECT count(*) FROM public.{table};',B))
            for command in [f'DELETE FROM public.{table};',f'UPDATE public.{table} SET owner_id=\'{B}\';',f'TRUNCATE public.{table};']:
                self.assertIn('permission denied',execute(command,A,fails=True))
        self.assertEqual([],self.pull(B));self.assertEqual(1,len(self.pull(A)))
    def test_15_owner_field_is_never_accepted_from_client(self):
        batch=classroom();batch[0]['owner_id']=B;execute(push_sql(batch),A,fails=True)
        batch=classroom();batch[0]['payload']['owner_id']=B;execute(push_sql(batch),A,fails=True)
    def test_16_same_legacy_id_can_exist_without_cross_account_access(self):
        self.push(classroom());self.push(classroom(name='Outra conta'),B)
        self.assertEqual('Outra conta',self.rows('classes',B)[0]['name'])
        self.assertEqual('Turma Fictícia Ç',self.rows('classes',A)[0]['name'])
    def test_17_anonymous_unconfirmed_expired_and_revoked_are_rejected(self):
        execute('SET ROLE anon;SELECT public.noctua_pull_academic(0);',fails=True)
        execute(push_sql(classroom()),UNCONFIRMED,fails=True)
        claims=dict(sub=A,role='authenticated',session_id=SESSION[A],exp=int(time.time())-1)
        execute(push_sql(classroom()),A,claims=claims,fails=True)
        self.push(classroom());execute(f"DELETE FROM auth.sessions WHERE id='{SESSION[A]}';")
        execute(push_sql(classroom('other')),A,fails=True);execute('SELECT public.noctua_pull_academic(0);',A,fails=True)
        for table in TABLES:self.assertEqual('0',execute(f'SELECT count(*) FROM public.{table};',A))
    def test_18_auxiliary_functions_are_not_callable_by_clients(self):
        for call in ["noctua_academic_definition('classes')","noctua_academic_key('classes','{}')","noctua_academic_graph('"+B+"')","noctua_academic_shape('{}','{}')","noctua_academic_validate('classes','{}','{}')"]:
            self.assertIn('permission denied',execute('SELECT public.'+call+';',A,fails=True))
    def test_19_parent_tombstone_with_live_child_is_blocked(self):
        self.push(self.populated());execute(push_sql([change('classes',None,1,dict(id='class-fixture'))]),A,fails=True)
        self.assertEqual(1,len(self.rows('classes')));self.assertEqual(1,len(self.pull()))
    def test_20_explicit_child_deletion_then_parent_preserves_prior_versions(self):
        self.push(classroom()+[change('students',student())]);rows=classroom()+[change('students',student())]
        delete=[dict(r,base=1,payload=None) for r in rows]
        self.push(list(reversed(delete)));self.assertEqual([],self.rows('classes'));self.assertEqual([],self.rows('students'))
        self.assertEqual('12',execute('SELECT count(*) FROM public.noctua_academic_versions;'))
        self.assertEqual(6,len(self.pull(after=1)[0]['records']))
    def test_21_composite_keys_preserve_separators_and_types(self):
        self.push(classroom('a:b')+[change('students',student('x:y','a:b'))])
        self.assertEqual('x:y',self.rows('students')[0]['id'])
        bad=classroom();bad[1]['key']['number']='1';execute(push_sql(bad),A,fails=True)
    def test_22_duplicate_keys_unexpected_fields_or_key_change_rejected(self):
        execute(push_sql(classroom()+[classroom()[0]]),A,fails=True)
        bad=classroom();bad[0]['key']['id']='different';execute(push_sql(bad),A,fails=True)
        bad=classroom();bad[0]['payload']['unexpected']='secret';execute(push_sql(bad),A,fails=True)
    def test_23_all_four_terms_required_and_calendar_validated(self):
        execute(push_sql(classroom()[:-1]),A,fails=True)
        bad=classroom();bad[2]['payload']['start']='2026-02-01';execute(push_sql(bad),A,fails=True)
        bad=classroom();bad[3]['payload']['start']='2026-02-30';execute(push_sql(bad),A,fails=True)
    def test_24_mismatched_classes_in_marks_and_grades_blocked(self):
        self.push(self.populated()+classroom('other')+[change('students',student('s2','other'))])
        for kind,row in [('marks',dict(lessonId='lesson-fixture',studentId='s2',mark='P',reason=None)),('grades',dict(activityId='activity-fixture',studentId='s2',value=0))]:
            execute(push_sql([change(kind,row)]),A,fails=True)
    def test_25_limits_and_grade_range_enforced(self):
        self.push(self.populated())
        for value in [-1,11]:execute(push_sql([change('grades',dict(activityId='activity-fixture',studentId='student-fixture',value=value))]),A,fails=True)
        execute(push_sql([]),A,fails=True)
        execute('SELECT public.noctua_pull_academic(-1);',A,fails=True)
    def test_26_unique_lesson_and_export_constraints_are_atomic(self):
        self.push(self.populated());execute(push_sql([change('lessons',lesson('duplicate'))]),A,fails=True)
        row=dict(id='row1',classId='class-fixture',studentId='student-fixture',position=0,manualHidden=False)
        self.push([change('export_rows',row)])
        execute(push_sql([change('export_rows',dict(row,id='row2',position=1))]),A,fails=True)
        execute(push_sql([change('export_rows',dict(row,id='row2',studentId=None))]),A,fails=True)
    def test_27_unique_position_swap_is_allowed_in_one_batch(self):
        self.push(classroom());rows=[dict(id=f'row{n}',classId='class-fixture',studentId=None,position=n,manualHidden=False) for n in range(2)]
        self.push([change('export_rows',r) for r in rows])
        self.push([change('export_rows',dict(r,position=1-r['position']),1) for r in rows])
        self.assertEqual([1,0],[r['position'] for r in self.rows('export_rows')])
    def test_28_timetable_historical_weekends_and_deleted_class_reference_are_preserved(self):
        timetable=dict(id='revision1',year=2026,effective='2026-01-01',revision=1,createdAt='2026-11-01T00:00:00Z',slots=[dict(id='slot',classId='deleted-origin',weekday=7,ordinal=12,className='Histórica',subject='História',shift='Matutino')])
        self.push([change('weekly_timetables',timetable)])
        self.assertEqual(timetable,self.rows('weekly_timetables')[0])
        execute(push_sql([change('weekly_timetables',dict(timetable,effective='2026-02-01'),1)]),A,fails=True)
    def test_29_journal_text_is_not_rewritten_and_not_tied_to_lessons(self):
        value=dict(id='journal',classId='class-fixture',date='2026-10-20',title='Ação & discussão',description='Linha 1\nLinha 2 <texto> '+('Longo ' * 1000))
        self.push(classroom()+[change('lesson_records',value)])
        self.assertEqual(value,self.rows('lesson_records')[0]);self.assertEqual([],self.rows('lessons'))
    def test_30_recomposition_and_external_relocation_audit_are_preserved(self):
        removed=dict(lesson=lesson(),attendance=dict(lessonId='lesson-fixture',studentId='student-fixture',mark='FJ',reason='Outro'))
        audit=dict(id='audit',confirmedAt='2026-11-01T00:00:00Z',previousDate='2026-10-21',effectiveDate='2026-10-20',previousTarget='Noturno',externalTarget='Outra escola',removed=[removed],operation='CORRECAO_EXTERNA',sourceStudentId=None,targetStudentId=None,targetClassId=None,targetLabel=None)
        link=dict(sourceStudentId='student-fixture',targetStudentId=None,effectiveDate='2026-10-20',externalTarget='Outra escola',corrections=[audit])
        changes=self.populated();changes[5]=change('students',dict(student(),status='Remanejado',left='2026-10-19'))
        self.push(changes+[change('relocations',link),change('recompositions',dict(studentId='student-fixture',term=4,value=0,date='2026-10-19'))])
        self.assertEqual(link,self.rows('relocations')[0]);self.assertEqual(0,self.rows('recompositions')[0]['value'])
    def test_31_all_tables_rls_and_no_direct_write_grants(self):
        self.assertEqual('7',execute("SELECT count(*) FROM pg_class WHERE relname LIKE 'noctua_academic_%' AND relkind='r' AND relrowsecurity;"))
        self.assertEqual('0',execute("SELECT count(*) FROM information_schema.table_privileges WHERE table_name LIKE 'noctua_academic_%' AND grantee IN ('PUBLIC','anon','authenticated') AND privilege_type<>'SELECT';"))
        self.assertEqual('0',execute("SELECT count(*) FROM pg_policies WHERE tablename LIKE 'noctua_academic_%' AND (cmd<>'SELECT' OR roles<>'{authenticated}'::name[]);"))
    def test_32_reapplying_migration_does_not_change_old_or_new_data(self):
        self.push(self.populated());before=self.pull()
        execute(ACADEMIC.read_text());self.assertEqual(before,self.pull())
        old=json.loads(execute(statement(bundle('v1-kept')),A));self.assertTrue(old['accepted'])
        execute(ACADEMIC.read_text());self.assertEqual('v1-kept',json.loads(execute('SELECT public.noctua_pull_classes(0);',A))[0]['id'])
    def test_33_cursor_never_splits_dependencies_and_is_commit_ordered(self):
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            a=pool.submit(execute,'BEGIN;'+push_sql(classroom('slow'))+'SELECT pg_sleep(0.15);COMMIT;',A)
            b=pool.submit(self.push,classroom('fast'));a.result();b.result()
        first=self.pull()[0];second=self.pull(after=first['sequence'])[0]
        self.assertEqual([1,2],[first['sequence'],second['sequence']]);self.assertEqual([5,5],[len(first['records']),len(second['records'])])
    def test_34_transaction_interruption_leaves_no_positive_receipt(self):
        op=str(uuid.uuid4());execute('BEGIN;'+push_sql(self.populated(),op)+'ROLLBACK;',A)
        self.assertEqual([],self.pull());self.assertTrue(self.push(self.populated(),op=op)['accepted'])
    def test_35_bad_session_cannot_read_any_table_even_with_known_owner_id(self):
        self.push(self.populated());claims=dict(sub=A,role='authenticated',session_id=SESSION[B],exp=int(time.time())+3600)
        for table in TABLES:self.assertEqual('0',execute(f"SELECT count(*) FROM public.{table} WHERE owner_id='{A}';",A,claims=claims))
        execute('SELECT public.noctua_pull_academic(0);',A,claims=claims,fails=True)

    def test_36_independent_kotlin_room_serializer_payloads_match_postgres_recovery(self):
        path=os.environ.get('ROOM_WIRE_PATH')
        if path is None:self.skipTest('Set ROOM_WIRE_PATH to JVM-generated academic-protocol-validation directory')
        kinds=set()
        for name in ('room-rows.json','history-rows.json','audit-rows.json'):
            self.setUp() # Separate fictitious graph per sample, never production data.
            rows=json.loads((Path(path)/name).read_text())
            self.assertTrue(self.push(rows)['accepted'])
            recovered=self.pull()[0]['records'];kinds.update(r['kind'] for r in recovered)
            identity=lambda r:(r['kind'],json.dumps(r['key'],sort_keys=True))
            self.assertEqual({identity(r):r['payload'] for r in rows},{identity(r):r['payload'] for r in recovered})
            self.assertTrue(all(r['revision']==1 for r in recovered))
        self.assertEqual(15,len(kinds))

    def test_37_prior_correction_audit_cannot_be_rewritten_or_dropped(self):
        audit=dict(id='audit',confirmedAt='2026-11-01T00:00:00Z',previousDate='2026-10-21',effectiveDate='2026-10-20',previousTarget='Externo',externalTarget='Corrigido',removed=[],operation='CORRECAO_EXTERNA',sourceStudentId=None,targetStudentId=None,targetClassId=None,targetLabel=None)
        link=dict(sourceStudentId='student-fixture',targetStudentId=None,effectiveDate='2026-10-20',externalTarget='Corrigido',corrections=[audit])
        self.push(self.populated()+[change('relocations',link)])
        for corrections in ([],[dict(audit,previousTarget='Apagado')]):
            execute(push_sql([change('relocations',dict(link,corrections=corrections),1)]),A,fails=True)
        self.assertEqual(link,self.rows('relocations')[0])
    def test_38_no_anonymous_select_on_tables_or_forged_owner_rpc_overload(self):
        self.push(self.populated())
        for table in TABLES:execute('SET ROLE anon;SELECT count(*) FROM public.'+table+';',fails=True)
        self.assertEqual('2',execute("SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname IN ('noctua_push_academic','noctua_pull_academic') AND p.prosecdef AND p.proconfig @> ARRAY['search_path=pg_catalog'];"))
        self.assertEqual('0',execute("SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname IN ('noctua_push_academic','noctua_pull_academic') AND pg_get_function_arguments(p.oid) LIKE '%owner%';"))

if __name__=='__main__':unittest.main(verbosity=2)
