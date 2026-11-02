-- Phase B, apply AFTER the three earlier migrations. No academic row is deleted.
-- Never reapply the original v1/guard migrations after these fenced entry points.
BEGIN;
-- Admin opt-in for authorized fictitious accounts only. Migration itself authorizes nobody.
CREATE TABLE IF NOT EXISTS public.noctua_academic_test_accounts (
 owner_id uuid PRIMARY KEY REFERENCES auth.users(id), allowed boolean NOT NULL DEFAULT false
);
ALTER TABLE public.noctua_academic_test_accounts ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.noctua_academic_test_accounts FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.noctua_academic_test_accounts TO authenticated;
DROP POLICY IF EXISTS own_rows ON public.noctua_academic_test_accounts;
CREATE POLICY own_rows ON public.noctua_academic_test_accounts FOR SELECT TO authenticated
 USING(owner_id=public.noctua_sync_principal());
CREATE TABLE IF NOT EXISTS public.noctua_sync_protocols (
 owner_id uuid PRIMARY KEY REFERENCES auth.users(id), protocol integer NOT NULL CHECK(protocol=2),
 activated_at timestamptz NOT NULL DEFAULT statement_timestamp()
);
ALTER TABLE public.noctua_sync_protocols ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.noctua_sync_protocols FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.noctua_sync_protocols TO authenticated;
DROP POLICY IF EXISTS own_rows ON public.noctua_sync_protocols;
CREATE POLICY own_rows ON public.noctua_sync_protocols FOR SELECT TO authenticated
 USING(owner_id=public.noctua_sync_principal());

DO $$ BEGIN
 IF to_regprocedure('public.noctua_v1_push_class(uuid,text,bigint,boolean,jsonb)') IS NULL THEN
  ALTER FUNCTION public.noctua_push_class(uuid,text,bigint,boolean,jsonb) RENAME TO noctua_v1_push_class;
  ALTER FUNCTION public.noctua_pull_classes(bigint) RENAME TO noctua_v1_pull_classes;
 END IF;
 IF to_regprocedure('public.noctua_v2_push_unfenced(uuid,jsonb)') IS NULL THEN
  ALTER FUNCTION public.noctua_push_academic(uuid,jsonb) RENAME TO noctua_v2_push_unfenced;
  ALTER FUNCTION public.noctua_pull_academic(bigint) RENAME TO noctua_v2_pull_unfenced;
 END IF;
END $$;
REVOKE ALL ON FUNCTION public.noctua_v1_push_class(uuid,text,bigint,boolean,jsonb),
 public.noctua_v1_pull_classes(bigint),public.noctua_v2_push_unfenced(uuid,jsonb),public.noctua_v2_pull_unfenced(bigint) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.noctua_academic_status()
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal(); inventory jsonb; seq bigint;
BEGIN
 IF principal IS NULL THEN RAISE EXCEPTION 'active confirmed session required' USING ERRCODE='42501'; END IF;
 SELECT coalesce(sequence,0) INTO seq FROM public.noctua_sync_heads WHERE owner_id=principal;
 SELECT coalesce(jsonb_agg(jsonb_build_object('id',id,'revision',revision,'sequence',sequence,'deleted',deleted,
    'payload',coalesce(payload::text,'')) ORDER BY sequence),'[]'::jsonb)
 INTO inventory FROM public.noctua_class_bundles WHERE owner_id=principal;
 RETURN jsonb_build_object('protocol',CASE WHEN EXISTS(SELECT FROM public.noctua_sync_protocols WHERE owner_id=principal) THEN 2 ELSE 1 END,
    'v1Sequence',coalesce(seq,0),'v1',inventory,'testAllowed',EXISTS(SELECT FROM public.noctua_academic_test_accounts WHERE owner_id=principal AND allowed));
END $$;

CREATE OR REPLACE FUNCTION public.noctua_push_class(p_operation uuid,p_id text,p_base bigint,p_deleted boolean,p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal();
BEGIN
 IF principal IS NULL THEN RAISE EXCEPTION 'active confirmed session required' USING ERRCODE='42501'; END IF;
 INSERT INTO public.noctua_sync_heads(owner_id) VALUES(principal) ON CONFLICT DO NOTHING;
 PERFORM 1 FROM public.noctua_sync_heads WHERE owner_id=principal FOR UPDATE;
 IF EXISTS(SELECT FROM public.noctua_sync_protocols WHERE owner_id=principal) THEN
  RAISE EXCEPTION 'account uses academic protocol; old writer blocked' USING ERRCODE='42501'; END IF;
 RETURN public.noctua_v1_push_class(p_operation,p_id,p_base,p_deleted,p_payload);
END $$;
CREATE OR REPLACE FUNCTION public.noctua_pull_classes(p_after bigint)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal();
BEGIN
 IF principal IS NULL THEN RAISE EXCEPTION 'active confirmed session required' USING ERRCODE='42501'; END IF;
 IF EXISTS(SELECT FROM public.noctua_sync_protocols WHERE owner_id=principal) THEN
  RAISE EXCEPTION 'account uses academic protocol; old reader blocked' USING ERRCODE='42501'; END IF;
 RETURN public.noctua_v1_pull_classes(p_after);
END $$;

CREATE OR REPLACE FUNCTION public.noctua_activate_academic(p_expected_sequence bigint,p_expected_v1 jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal(); status jsonb; row record; term jsonb;
 changes jsonb:='[]'; operation uuid;
BEGIN
 IF principal IS NULL THEN RAISE EXCEPTION 'active confirmed session required' USING ERRCODE='42501'; END IF;
 IF NOT EXISTS(SELECT FROM public.noctua_academic_test_accounts WHERE owner_id=principal AND allowed) THEN
  RAISE EXCEPTION 'authorized test account required' USING ERRCODE='42501'; END IF;
 INSERT INTO public.noctua_sync_heads(owner_id) VALUES(principal) ON CONFLICT DO NOTHING;
 PERFORM 1 FROM public.noctua_sync_heads WHERE owner_id=principal FOR UPDATE;
 status:=public.noctua_academic_status();
 IF p_expected_sequence IS DISTINCT FROM (status->>'v1Sequence')::bigint OR p_expected_v1 IS DISTINCT FROM status->'v1' THEN
  RAISE EXCEPTION 'v1 changed during transition; review required' USING ERRCODE='40001'; END IF;
 IF status->>'protocol'='2' THEN RETURN status; END IF;
 IF EXISTS(SELECT FROM public.noctua_academic_records WHERE owner_id=principal) THEN
  RAISE EXCEPTION 'unmigrated academic records require review' USING ERRCODE='40001'; END IF;
 FOR row IN SELECT * FROM public.noctua_class_bundles WHERE owner_id=principal AND NOT deleted ORDER BY sequence LOOP
  PERFORM public.noctua_validate_class(row.id,row.payload);
  changes:=changes||jsonb_build_array(jsonb_build_object('kind','classes','key',jsonb_build_object('id',row.id),'base',0,'payload',row.payload->'school'));
  FOR term IN SELECT * FROM jsonb_array_elements(row.payload->'terms') LOOP
   changes:=changes||jsonb_build_array(jsonb_build_object('kind','terms','key',jsonb_build_object('classId',row.id,'number',term->'number'),'base',0,'payload',term));
  END LOOP;
 END LOOP;
 IF jsonb_array_length(changes)>256 OR octet_length(changes::text)>1048576 THEN
  RAISE EXCEPTION 'initial inventory exceeds validated batch limit' USING ERRCODE='54000'; END IF;
 -- Fence and seeding commit together; prior receipts/conflicts/versions remain untouched.
 INSERT INTO public.noctua_sync_protocols(owner_id,protocol) VALUES(principal,2);
 IF jsonb_array_length(changes)>0 THEN
  operation:=gen_random_uuid();
  PERFORM public.noctua_v2_push_unfenced(operation,changes);
 END IF;
 RETURN public.noctua_academic_status();
END $$;

CREATE OR REPLACE FUNCTION public.noctua_push_academic(p_operation uuid,p_changes jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal(); change jsonb; previous jsonb; result jsonb;
BEGIN
 IF principal IS NULL THEN RAISE EXCEPTION 'active confirmed session required' USING ERRCODE='42501'; END IF;
 IF NOT EXISTS(SELECT FROM public.noctua_academic_test_accounts WHERE owner_id=principal AND allowed) THEN
  RAISE EXCEPTION 'authorized test account required' USING ERRCODE='42501'; END IF;
 INSERT INTO public.noctua_sync_heads(owner_id) VALUES(principal) ON CONFLICT DO NOTHING;
 PERFORM 1 FROM public.noctua_sync_heads WHERE owner_id=principal FOR UPDATE;
 IF NOT EXISTS(SELECT FROM public.noctua_sync_protocols WHERE owner_id=principal) THEN
  RAISE EXCEPTION 'explicit protocol transition required' USING ERRCODE='42501'; END IF;
 IF jsonb_typeof(p_changes) IS DISTINCT FROM 'array' THEN RAISE EXCEPTION 'invalid changes'; END IF;
 FOR change IN SELECT * FROM jsonb_array_elements(p_changes) LOOP
  IF change->>'kind' NOT IN ('classes','terms','students') OR change->>'kind' IS NULL THEN
   RAISE EXCEPTION 'entity outside phase B scope' USING ERRCODE='22023'; END IF;
  PERFORM public.noctua_academic_validate(change->>'kind',change->'key',change->'payload');
  IF change->'payload'='null'::jsonb THEN CONTINUE; END IF;
  SELECT payload INTO previous FROM public.noctua_academic_versions WHERE owner_id=principal AND kind=change->>'kind'
   AND key=change->'key' AND NOT deleted ORDER BY revision DESC LIMIT 1;
  IF change->>'kind'='classes' AND previous IS NOT NULL AND previous->'year' IS DISTINCT FROM change->'payload'->'year' THEN
   RAISE EXCEPTION 'class year is immutable' USING ERRCODE='22023'; END IF;
  IF change->>'kind'='students' THEN
   IF previous IS NOT NULL AND (previous->'personId' IS DISTINCT FROM change->'payload'->'personId' OR previous->'classId' IS DISTINCT FROM change->'payload'->'classId') THEN
    RAISE EXCEPTION 'enrollment identity is immutable' USING ERRCODE='22023'; END IF;
   IF change->'payload'->>'status'='Remanejado' OR
      (change->'payload'->>'status'<>'Ativo' AND change->'payload'->'left'='null'::jsonb) THEN
    RAISE EXCEPTION 'enrollment requires a movement chain beyond phase B' USING ERRCODE='22023'; END IF;
  END IF;
 END LOOP;
 result:=public.noctua_v2_push_unfenced(p_operation,p_changes);
 IF (result->>'accepted')::boolean AND EXISTS(
  SELECT FROM public.noctua_academic_records WHERE owner_id=principal AND kind='students' AND NOT deleted
  GROUP BY payload->>'classId',payload->>'number' HAVING count(*)>1
 ) THEN RAISE EXCEPTION 'conflicting official roster number' USING ERRCODE='23505'; END IF;
 RETURN result;
END $$;

CREATE OR REPLACE FUNCTION public.noctua_pull_academic(p_after bigint)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal();
BEGIN
 IF principal IS NULL OR NOT EXISTS(SELECT FROM public.noctua_academic_test_accounts WHERE owner_id=principal AND allowed)
   OR NOT EXISTS(SELECT FROM public.noctua_sync_protocols WHERE owner_id=principal) THEN
  RAISE EXCEPTION 'authorized academic test required' USING ERRCODE='42501'; END IF;
 RETURN public.noctua_v2_pull_unfenced(p_after);
END $$;

REVOKE ALL ON FUNCTION public.noctua_academic_status(),public.noctua_activate_academic(bigint,jsonb),
 public.noctua_push_academic(uuid,jsonb),public.noctua_pull_academic(bigint),public.noctua_push_class(uuid,text,bigint,boolean,jsonb),public.noctua_pull_classes(bigint)
 FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.noctua_academic_status(),public.noctua_activate_academic(bigint,jsonb),
 public.noctua_push_academic(uuid,jsonb),public.noctua_pull_academic(bigint),public.noctua_push_class(uuid,text,bigint,boolean,jsonb),public.noctua_pull_classes(bigint)
 TO authenticated;
COMMIT;
