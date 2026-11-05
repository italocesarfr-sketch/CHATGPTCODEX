#!/usr/bin/env python3
"""Focused statement-timeout diagnosis, ONLY on a disposable local E fixture.
No hosted credentials/endpoints. Does not change production functions, role defaults,
API configuration or Android. JSON on stdout; never reports fixture JWTs/payloads.
"""
import importlib.util
import json
import os
from pathlib import Path
import select
import subprocess
import time
import uuid

if os.environ.get('PGDATABASE') != 'noctua_phase_e_test' or os.environ.get('PGHOST') not in ('127.0.0.1', 'localhost', '/workspace/pg-local'):
    raise SystemExit('Requires disposable LOCAL noctua_phase_e_test; never hosted')
PSQL = os.environ.get('PSQL_BIN', 'psql')
HERE = Path(__file__).resolve().parent

def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

# Existing fixtures apply A-D definitions at import; empty synthetic scope 5 first.
b = load('timeout_base', HERE / 'test-phase-b-postgres.py')
b.reset()
f = load('timeout_e', HERE / 'test-phase-e-postgres.py')
f.execute((f.f.ROOT / 'supabase/migrations/202611030002_phase_e_decisions.sql').read_text())
results = []

def setup(rows):
    b.reset(); b.activate(); f.d.c.enable(); f.d.enable(); f.enable()
    operation, parts, _ = f.begin(rows)
    for index, part in enumerate(parts):
        f.stage(operation, index, part)
    return operation, len(parts)

def commit(operation, budget='8s'):
    # SET ROLE alone does not apply ALTER ROLE login defaults. PostgREST applies
    # request role settings; emulate its documented budget explicitly per session.
    claims = dict(sub=f.A, role='authenticated', session_id=f.f.SESSION[f.A], exp=int(time.time())+3600)
    sql = ("SET ROLE authenticated; SET request.jwt.claim.sub='"+f.A+"'; SET request.jwt.claims="+
           "'"+json.dumps(claims)+"'; SET statement_timeout='"+budget+"';\n"+
           "SELECT current_setting('statement_timeout');\n"+
           "EXPLAIN (ANALYZE, FORMAT JSON, TIMING OFF) SELECT public.noctua_commit_academic_e('"+operation+"');\n")
    start = time.monotonic()
    run = subprocess.run([PSQL, '-XAtq', '-v', 'ON_ERROR_STOP=1', '-v', 'VERBOSITY=verbose'],
                         input=sql, text=True, capture_output=True, timeout=35)
    elapsed = round(time.monotonic()-start, 3)
    lines = run.stdout.strip().splitlines()
    assert lines and lines[0] == budget, 'Statement budget not applied'
    if run.returncode:
        assert '57014' in run.stderr and 'statement timeout' in run.stderr, 'Unexpected failure; no recovery attempted'
        return None, elapsed, None
    plan = json.loads('\n'.join(lines[1:]))[0]
    reply = json.loads(f.execute("SELECT response FROM public.noctua_academic_receipts WHERE operation_id='"+operation+"';", f.A))
    return reply, elapsed, plan['Execution Time']

def empty_after_cancel(parts):
    for table in ('records', 'versions', 'batches', 'receipts', 'links', 'uniques'):
        assert f.execute('SELECT count(*) FROM public.noctua_academic_'+table+';', f.A) == '0'
    assert f.d.status()['academicSequence'] == 0
    assert int(f.execute('SELECT count(*) FROM public.noctua_e_parts;', f.A)) == parts

def exact_receipt(operation, rows, reply):
    assert reply['accepted'] and reply['sequence'] == 1
    assert int(f.execute('SELECT count(*) FROM public.noctua_academic_records;', f.A)) == len(rows)
    assert int(f.execute('SELECT count(*) FROM public.noctua_academic_versions;', f.A)) == len(rows)
    assert f.execute('SELECT count(*) FROM public.noctua_academic_receipts;', f.A) == '1'
    replay, elapsed, db_ms = commit(operation)
    assert replay == reply and f.d.status()['academicSequence'] == 1
    return dict(psql_wall_seconds=elapsed, postgres_execution_ms=db_ms)

def recover(operation, rows):
    reply, elapsed, db_ms = commit(operation)
    budget = '8s'
    if reply is None:
        # Local diagnosis only: prove the identical manifest can finish, without
        # changing role/global/protocol configuration or claiming hosted 8s passed.
        reply, elapsed, db_ms = commit(operation, '20s')
        budget = '20s-local-diagnostic-only'
    assert reply is not None
    replay = exact_receipt(operation, rows, reply)
    return dict(retry_budget=budget, retry_psql_wall_seconds=elapsed, retry_postgres_execution_ms=db_ms, replay=replay)

LOCK = 220008
locker = None
try:
    small = f.d.rows()
    op, parts = setup(small)
    reply, elapsed, db_ms = commit(op)
    assert reply is not None, 'Small transaction failed 8s admission'
    replay = exact_receipt(op, small, reply)
    results.append(dict(case='small-budget', records=len(small), parts=parts, statement_timeout='8s',
                        outcome='accepted', psql_wall_seconds=elapsed, postgres_execution_ms=db_ms, replay=replay))

    rows = f.large()
    assert len(rows) == 5171
    op, parts = setup(rows)
    reply, elapsed, db_ms = commit(op)
    evidence = dict(case='large-natural-budget', records=len(rows), parts=parts, statement_timeout='8s',
                    outcome='accepted' if reply else '57014-atomic-rollback', psql_wall_seconds=elapsed, postgres_execution_ms=db_ms)
    if reply:
        evidence['replay'] = exact_receipt(op, rows, reply)
    else:
        empty_after_cancel(parts)
        evidence.update(recover(op, rows))
    results.append(evidence)

    op, parts = setup(rows)
    # Test-only hook: a held advisory lock stops the real RPC AFTER academic writes.
    # A sequence witnesses entering that point even though the transaction rolls back.
    # No sleep is used and no production validator is replaced.
    f.execute("""
CREATE SEQUENCE public.noctua_timeout_probe;
CREATE FUNCTION public.noctua_timeout_hook() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.owner_id='11111111-1111-4111-8111-111111111111'::uuid AND
   (SELECT count(*) FROM public.noctua_academic_records WHERE owner_id=NEW.owner_id)=8 THEN
  PERFORM nextval('public.noctua_timeout_probe');
  PERFORM pg_advisory_xact_lock(220008);
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER noctua_timeout_hook AFTER INSERT ON public.noctua_academic_records
 FOR EACH ROW EXECUTE FUNCTION public.noctua_timeout_hook();
""")
    locker = subprocess.Popen([PSQL, '-XAtq', '-v', 'ON_ERROR_STOP=1'], stdin=subprocess.PIPE,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, bufsize=1)
    locker.stdin.write("BEGIN; SELECT 'locked' FROM (SELECT pg_advisory_xact_lock("+str(LOCK)+")) AS held;\n")
    locker.stdin.flush()
    # Read bounded readiness on this disposable control connection.
    deadline = time.monotonic()+10
    while True:
        assert time.monotonic()<deadline, 'Local advisory lock not ready'
        ready, _, _ = select.select([locker.stdout], [], [], 1)
        if ready and locker.stdout.readline().strip() == 'locked':
            break
    reply, elapsed, db_ms = commit(op)
    assert reply is None, 'Controlled blocking did not exercise statement_timeout'
    assert f.execute('SELECT is_called FROM public.noctua_timeout_probe;') == 't', 'RPC never reached academic writes'
    empty_after_cancel(parts)
    locker.stdin.write('ROLLBACK;\n\\q\n'); locker.stdin.flush(); locker.communicate(timeout=5); locker = None
    f.execute('DROP TRIGGER noctua_timeout_hook ON public.noctua_academic_records; DROP FUNCTION public.noctua_timeout_hook(); DROP SEQUENCE public.noctua_timeout_probe;')
    evidence = dict(case='mid-write-timeout-and-retry', records=len(rows), parts=parts,
                    statement_timeout='8s', outcome='57014-atomic-rollback', reached_academic_writes=True,
                    psql_wall_seconds=elapsed, postgres_execution_ms=db_ms, persisted_parts=parts, academic_rows_after_cancel=0,
                    versions_after_cancel=0, receipts_after_cancel=0, sequence_after_cancel=0)
    evidence.update(recover(op, rows))
    results.append(evidence)
    print(json.dumps(dict(environment='local-disposable-postgresql', real_supabase_auth=False,
                          postgrest_timeout_tested=False, android_timeout_tested=False,
                          timings='psql wall time includes connection; successful statements also report PostgreSQL EXPLAIN ANALYZE Execution Time (ms); explicit 8s session budget',
                          results=results, passed=len(results)), indent=2))
finally:
    if locker is not None:
        locker.kill(); locker.communicate(timeout=5)
    f.execute('DROP TRIGGER IF EXISTS noctua_timeout_hook ON public.noctua_academic_records; DROP FUNCTION IF EXISTS public.noctua_timeout_hook(); DROP SEQUENCE IF EXISTS public.noctua_timeout_probe;')
    b.reset()
