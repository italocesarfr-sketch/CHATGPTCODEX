#!/usr/bin/env python3
"""Synthetic PostgreSQL 17 only. No hosted Auth, H13/PT409 or administrative fixtures online."""
import concurrent.futures
import copy
import importlib.util
import json
import os
from pathlib import Path
import time
import unittest
import uuid

if (os.environ.get('NOCTUA_COMMERCIAL_DISPOSABLE') != 'YES' or
        os.environ.get('PGHOST') != '127.0.0.1' or os.environ.get('PGPORT') != '55431' or
        os.environ.get('PGDATABASE') != 'noctua_phase_e_test'):
    raise SystemExit('Requires explicitly authorized disposable local port 55431 / noctua_phase_e_test')
spec = importlib.util.spec_from_file_location('commercial_d_fixture', Path(__file__).with_name('test-phase-d-postgres.py'))
d = importlib.util.module_from_spec(spec)
spec.loader.exec_module(d)
f, b, execute, literal, A, B = d.f, d.b, d.execute, d.literal, d.A, d.B
for name in ('202611030001_phase_e_paging.sql', '202611030002_phase_e_decisions.sql', '202611030003_phase_e_indexed_graph.sql'):
    execute((f.ROOT / 'supabase/migrations' / name).read_text())
old_guard = execute("SELECT pg_get_functiondef('public.noctua_e_guarded_write(uuid,jsonb)'::regprocedure);")
execute((f.ROOT / 'supabase/migrations/202611170001_commercial_bootstrap.sql').read_text())
new_guard = execute("SELECT pg_get_functiondef('public.noctua_e_guarded_write(uuid,jsonb)'::regprocedure);")
assert new_guard.replace('NOT public.noctua_e_authorized()',
    'NOT EXISTS(SELECT FROM public.noctua_academic_test_accounts WHERE owner_id=principal AND allowed)') == old_guard
# Include the new FK in the existing synthetic fixture's atomic reset, never CASCADE.
b.TABLES.insert(0, 'noctua_commercial_accounts')


def ensure(owner=A, **kwargs):
    return json.loads(execute('SELECT public.noctua_ensure_academic_e();', owner, **kwargs))


def grant(owner=A):
    execute(f"INSERT INTO public.noctua_commercial_accounts(owner_id,enabled) VALUES('{owner}',true);")


def push(rows, owner=A, operation=None):
    operation = operation or str(uuid.uuid4())
    parts = [rows[n:n + 128] for n in range(0, len(rows), 128)]
    execute(f"SELECT public.noctua_begin_academic_e('{operation}',{len(rows)},{len(parts)});", owner)
    for n, part in enumerate(parts):
        execute(f"SELECT public.noctua_stage_academic_e('{operation}',{n},{literal(part)});", owner)
    return json.loads(execute(f"SELECT public.noctua_commit_academic_e('{operation}');", owner))


class CommercialBootstrapTest(unittest.TestCase):
    def setUp(self):
        b.reset()
        execute('DELETE FROM public.noctua_academic_test_accounts; UPDATE public.noctua_commercial_rollout SET enabled=true;')

    def test_01_rollout_and_individual_entitlement_both_required(self):
        self.assertEqual('INELIGIBLE', ensure()['reason'])
        grant(); execute('UPDATE public.noctua_commercial_rollout SET enabled=false;')
        self.assertEqual('INELIGIBLE', ensure()['reason'])
        self.assertEqual('0', execute('SELECT count(*) FROM public.noctua_sync_protocols;'))

    def test_02_confirmed_new_account_receives_full_E_without_test_allowlist(self):
        grant(); result = ensure()
        self.assertEqual(dict(eligible=True, initialized=True, reason='READY', academicSequence=0), result)
        self.assertEqual('2|5', execute('SELECT protocol,academic_scope FROM public.noctua_sync_protocols;', A))
        self.assertEqual('0', execute('SELECT count(*) FROM public.noctua_academic_test_accounts;'))
        self.assertFalse(json.loads(execute('SELECT public.noctua_academic_status_c();', A))['testAllowed'])

    def test_03_duplicate_bootstrap_preserves_initialization_time_and_revisions(self):
        grant(); first = ensure()
        before = execute('SELECT initialized_at FROM public.noctua_commercial_accounts;')
        self.assertEqual(first, ensure())
        self.assertEqual(before, execute('SELECT initialized_at FROM public.noctua_commercial_accounts;'))
        self.assertEqual('1', execute('SELECT count(*) FROM public.noctua_sync_protocols;'))

    def test_04_v1_account_is_not_migrated_or_modified(self):
        grant(); execute(f.statement(f.bundle()), A)
        before = execute('SELECT md5(jsonb_agg(to_jsonb(v) ORDER BY sequence)::text) FROM public.noctua_class_versions v;')
        self.assertEqual('REVIEW', ensure()['reason'])
        self.assertEqual(before, execute('SELECT md5(jsonb_agg(to_jsonb(v) ORDER BY sequence)::text) FROM public.noctua_class_versions v;'))
        self.assertEqual('0', execute('SELECT count(*) FROM public.noctua_sync_protocols;'))

    def test_05_v1_tombstones_and_receipts_also_block(self):
        grant(); execute(f.statement(f.bundle()), A); execute(f.statement(f.bundle(), 1, deleted=True), A)
        self.assertEqual('REVIEW', ensure()['reason'])
        self.assertEqual('2', execute('SELECT count(*) FROM public.noctua_sync_receipts;'))

    def test_06_existing_test_account_analogue_of_Aron_stays_on_old_path(self):
        execute(f"INSERT INTO public.noctua_academic_test_accounts VALUES('{A}',true);")
        execute(f.statement(f.bundle()), A); grant()
        before = json.loads(execute('SELECT public.noctua_academic_status_c();', A))
        self.assertEqual('REVIEW', ensure()['reason'])
        self.assertEqual(before, json.loads(execute('SELECT public.noctua_academic_status_c();', A)))
        b.activate(); d.c.enable(); d.enable()
        sequence=d.status()['academicSequence']
        execute(f'SELECT public.noctua_enable_academic_e({sequence});', A)
        self.assertTrue(push(d.rows())['accepted'])

    def test_07_two_sessions_initialize_once(self):
        grant(); sid = str(uuid.uuid4()); execute(f"INSERT INTO auth.sessions VALUES('{sid}','{A}',NULL);")
        claims = dict(sub=A, role='authenticated', session_id=sid, exp=int(time.time()) + 3600)
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            left = pool.submit(ensure); right = pool.submit(ensure, claims=claims)
            self.assertEqual(left.result(), right.result())
        self.assertEqual('1', execute('SELECT count(*) FROM public.noctua_sync_protocols;'))

    def test_08_competing_v1_write_and_bootstrap_cannot_both_win(self):
        grant()
        def write():
            try: execute(f.statement(f.bundle()), A); return True
            except AssertionError: return False
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            a = pool.submit(write); c = pool.submit(ensure)
            self.assertNotEqual(a.result(), c.result()['initialized'])

    def test_09_revoked_and_expired_sessions_are_rejected(self):
        grant(); execute(f"DELETE FROM auth.sessions WHERE user_id='{A}';")
        execute('SELECT public.noctua_ensure_academic_e();', A, fails=True)
        expired = dict(sub=B, role='authenticated', session_id=f.SESSION[B], exp=1)
        execute('SELECT public.noctua_commercial_status();', B, claims=expired, fails=True)
        self.assertEqual('0', execute('SELECT count(*) FROM public.noctua_sync_protocols;'))

    def test_10_unconfirmed_and_anonymous_accounts_cannot_initialize(self):
        execute('SELECT public.noctua_ensure_academic_e();', f.UNCONFIRMED, fails=True)
        execute(f"UPDATE auth.users SET is_anonymous=true WHERE id='{A}';"); grant()
        execute('SELECT public.noctua_ensure_academic_e();', A, fails=True)

    def test_11_no_client_can_grant_itself_or_call_private_helper(self):
        for table in ('noctua_commercial_accounts', 'noctua_commercial_rollout'):
            for action in (f'SELECT * FROM public.{table};', f'DELETE FROM public.{table};'):
                self.assertIn('permission denied', execute(action, A, fails=True))
        execute('SELECT public.noctua_e_authorized();', A, fails=True)
        execute('SET ROLE anon; SELECT public.noctua_ensure_academic_e();', fails=True)
        execute(f"INSERT INTO public.noctua_commercial_accounts(owner_id,enabled) VALUES('{A}',true);", A, fails=True)

    def test_12_two_owners_remain_isolated_even_with_known_keys(self):
        grant(A); grant(B); ensure(A); ensure(B); push(f.classroom(), A)
        self.assertEqual([], json.loads(execute('SELECT public.noctua_pull_academic_e(0,0);', B)))
        self.assertEqual('0', execute(f"SELECT count(*) FROM public.noctua_academic_records WHERE owner_id='{A}';", B))
        reply = push([f.change('classes', f.bundle(name='Other owner')['school'], 1)], B)
        self.assertFalse(reply['accepted'])
        self.assertEqual('1', execute('SELECT max(revision) FROM public.noctua_academic_records;', A))

    def test_13_all_fifteen_types_use_original_E_validators_and_pull(self):
        grant(); ensure(); self.assertTrue(push(d.rows())['accepted'])
        records=[];offset=0
        while True:
            page=json.loads(execute(f'SELECT public.noctua_pull_academic_e(0,{offset});', A))[0]
            records+=page['records'];offset+=len(page['records'])
            if offset==page['total']:break
        self.assertEqual(15, len({r['kind'] for r in records}))
        self.assertEqual(len(d.rows()), len(records))

    def test_14_lost_response_replay_keeps_single_receipt_and_batch(self):
        grant(); ensure(); op = str(uuid.uuid4()); first = push(d.rows(), operation=op)
        self.assertEqual(first, push(d.rows(), operation=op))
        self.assertEqual('1|1', execute('SELECT (SELECT count(*) FROM public.noctua_academic_batches),(SELECT count(*) FROM public.noctua_academic_receipts);'))

    def test_15_graph_failure_rolls_back_data_revision_and_receipt(self):
        grant(); ensure(); bad = [r for r in d.rows() if r['kind'] != 'relocations']
        with self.assertRaises(AssertionError): push(bad)
        self.assertEqual('0|0|0', execute('SELECT (SELECT count(*) FROM public.noctua_academic_records),(SELECT count(*) FROM public.noctua_academic_receipts),(SELECT sequence FROM public.noctua_academic_heads);'))

    def test_16_concurrent_grade_variants_preserve_versions_and_negative_receipt(self):
        grant(); ensure(); push(d.rows())
        grade = copy.deepcopy(d.SNAP['grades'][0]); other = dict(grade, value=0)
        grade['value'] = 8
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            a = pool.submit(push, [f.change('grades', grade, 1)])
            c = pool.submit(push, [f.change('grades', other, 1)])
            self.assertEqual(1, sum(x['accepted'] for x in (a.result(), c.result())))
        self.assertEqual('3', execute('SELECT count(*) FROM public.noctua_academic_receipts;'))

    def test_17_revoking_commercial_policy_preserves_data_and_blocks_access(self):
        grant(); ensure(); push(d.rows())
        before = execute('SELECT md5(jsonb_agg(to_jsonb(r) ORDER BY kind,key)::text) FROM public.noctua_academic_records r;')
        execute('UPDATE public.noctua_commercial_rollout SET enabled=false;')
        execute('SELECT public.noctua_pull_academic_e(0,0);', A, fails=True)
        self.assertEqual('INELIGIBLE', ensure()['reason'])
        self.assertEqual(before, execute('SELECT md5(jsonb_agg(to_jsonb(r) ORDER BY kind,key)::text) FROM public.noctua_academic_records r;'))
        execute('UPDATE public.noctua_commercial_rollout SET enabled=true; UPDATE public.noctua_commercial_accounts SET enabled=false;')
        execute('SELECT public.noctua_pull_academic_e(0,0);', A, fails=True)

    def test_18_legacy_owner_is_denied_even_if_misgranted(self):
        owner = 'f624fdc9-2225-4211-8efe-46f9af2c53e5'
        execute(f"INSERT INTO auth.users VALUES('{owner}',now(),false); INSERT INTO auth.sessions VALUES('{f.SESSION[A]}', '{owner}', NULL) ON CONFLICT DO NOTHING;")
        sid = str(uuid.uuid4()); execute(f"INSERT INTO auth.sessions VALUES('{sid}','{owner}',NULL);"); grant(owner)
        result = ensure(owner, claims=dict(sub=owner, role='authenticated', session_id=sid, exp=int(time.time()) + 3600))
        self.assertEqual('REVIEW', result['reason'])
        self.assertEqual('0', execute('SELECT count(*) FROM public.noctua_sync_protocols;'))

    def test_19_unmanaged_partial_v2_is_not_adopted(self):
        grant(); execute(f"INSERT INTO public.noctua_academic_heads VALUES('{A}',0);")
        self.assertEqual('REVIEW', ensure()['reason'])
        self.assertEqual('0', execute('SELECT count(*) FROM public.noctua_sync_protocols;'))

    def test_20_transaction_rollback_does_not_leave_half_initialization(self):
        grant()
        execute('BEGIN; SELECT public.noctua_ensure_academic_e(); ROLLBACK;', A)
        self.assertEqual('0', execute('SELECT count(*) FROM public.noctua_sync_protocols;'))
        self.assertEqual('t', execute('SELECT initialized_at IS NULL FROM public.noctua_commercial_accounts;'))
        self.assertTrue(ensure()['initialized'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
