#!/usr/bin/env python3
"""Generate a REVIEWABLE A-E transaction. No database/network access or execution.
Only valid on an audited v1 + session-guard baseline with no academic v2 objects.
"""
import argparse
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FILES = (
    '202611010001_academic_protocol.sql',
    '202611020001_phase_b_fence.sql',
    '202611020002_phase_c_scope.sql',
    '202611020003_phase_d_scope.sql',
    '202611030001_phase_e_paging.sql',
    '202611030002_phase_e_decisions.sql',
    '202611030003_phase_e_indexed_graph.sql',
)

def bundle():
    parts = []
    for name in FILES:
        raw = (ROOT / 'supabase/migrations' / name).read_bytes()
        lines = raw.decode().splitlines(keepends=True)
        # Match ONLY standalone transaction markers, never PL/pgSQL BEGIN/END.
        starts = [i for i,l in enumerate(lines) if l.strip() == 'BEGIN;']
        ends = [i for i,l in enumerate(lines) if l.strip() == 'COMMIT;']
        if len(starts) != 1 or len(ends) != 1 or starts[0] >= ends[0]:
            raise ValueError('Unexpected transaction envelope: '+name)
        if any(l.strip() and not l.lstrip().startswith('--') for l in lines[:starts[0]]+lines[ends[0]+1:]):
            raise ValueError('Executable SQL outside transaction: '+name)
        body = ''.join(lines[:starts[0]]+lines[starts[0]+1:ends[0]]+lines[ends[0]+1:])
        parts.append('-- SOURCE '+name+' SHA256 '+hashlib.sha256(raw).hexdigest()+'\n'+body)
    return """-- GENERATED FOR REVIEW ONLY. Administrative authorization still required.
-- Never run on a partially migrated or unknown remote baseline.
-- One outer transaction: intermediate unfenced RPC grants never become visible.
BEGIN;
SET LOCAL lock_timeout='5s';
DO $preflight$
DECLARE definition text;
BEGIN
 IF to_regprocedure('public.noctua_sync_principal()') IS NULL OR
    to_regprocedure('public.noctua_push_class(uuid,text,bigint,boolean,jsonb)') IS NULL OR
    to_regclass('auth.sessions') IS NULL THEN
  RAISE EXCEPTION 'Audited v1 and session guard baseline required';
 END IF;
 SELECT pg_get_functiondef('public.noctua_sync_principal()'::regprocedure) INTO definition;
 IF position('auth.sessions' in definition)=0 OR position('email_confirmed_at' in definition)=0 THEN
  RAISE EXCEPTION 'Active confirmed session guard required';
 END IF;
 IF EXISTS(SELECT FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
           WHERE n.nspname='public' AND (c.relname LIKE 'noctua_academic_%' OR
                 c.relname LIKE 'noctua_e_%' OR c.relname='noctua_sync_protocols')) OR
    to_regprocedure('public.noctua_activate_academic(bigint,jsonb)') IS NOT NULL OR
    to_regprocedure('public.noctua_v1_push_class(uuid,text,bigint,boolean,jsonb)') IS NOT NULL THEN
  RAISE EXCEPTION 'Academic objects already present; inventory and tailored review required';
 END IF;
END $preflight$;
"""+'\n'.join(parts)+"\nNOTIFY pgrst, 'reload schema';\nCOMMIT;\n"

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    sql = bundle()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    # No overwrite of a previously reviewed artifact.
    with args.output.open('x', encoding='utf-8') as out:
        out.write(sql)
    print('Prepared only:', args.output)
    print('SHA256:', hashlib.sha256(sql.encode()).hexdigest())
