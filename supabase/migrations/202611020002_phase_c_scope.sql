-- Phase C: explicit per-owner expansion. Run only after phase A and B; no hosted execution by CI.
BEGIN;
ALTER TABLE public.noctua_sync_protocols ADD COLUMN IF NOT EXISTS academic_scope integer NOT NULL DEFAULT 2 CHECK(academic_scope IN (2,3));

CREATE OR REPLACE FUNCTION public.noctua_academic_status_c()
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal(); result jsonb;
BEGIN
 result:=public.noctua_academic_status(); -- Confirms session; no client-supplied owner.
 RETURN result||jsonb_build_object('academicScope',coalesce((SELECT academic_scope FROM public.noctua_sync_protocols WHERE owner_id=principal),2),
  'academicSequence',coalesce((SELECT sequence FROM public.noctua_academic_heads WHERE owner_id=principal),0));
END $$;

CREATE OR REPLACE FUNCTION public.noctua_enable_academic_c(p_expected_sequence bigint)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal(); scope integer; seq bigint;
BEGIN
 IF principal IS NULL OR NOT EXISTS(SELECT FROM public.noctua_academic_test_accounts WHERE owner_id=principal AND allowed) THEN
  RAISE EXCEPTION 'authorized test account required' USING ERRCODE='42501'; END IF;
 PERFORM 1 FROM public.noctua_sync_heads WHERE owner_id=principal FOR UPDATE;
 SELECT academic_scope INTO scope FROM public.noctua_sync_protocols WHERE owner_id=principal FOR UPDATE;
 IF scope IS NULL THEN RAISE EXCEPTION 'protocol transition required' USING ERRCODE='42501'; END IF;
 INSERT INTO public.noctua_academic_heads(owner_id) VALUES(principal) ON CONFLICT DO NOTHING;
 SELECT sequence INTO seq FROM public.noctua_academic_heads WHERE owner_id=principal FOR UPDATE;
 IF scope=3 THEN RETURN public.noctua_academic_status_c(); END IF;
 IF seq IS DISTINCT FROM p_expected_sequence THEN RAISE EXCEPTION 'receive changes before expanding scope' USING ERRCODE='40001'; END IF;
 UPDATE public.noctua_sync_protocols SET academic_scope=3 WHERE owner_id=principal;
 RETURN public.noctua_academic_status_c();
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
 IF NOT EXISTS(SELECT FROM public.noctua_sync_protocols WHERE owner_id=principal AND academic_scope=2) THEN
  RAISE EXCEPTION 'phase C client required' USING ERRCODE='42501'; END IF;
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

CREATE OR REPLACE FUNCTION public.noctua_push_academic_c(p_operation uuid,p_changes jsonb)
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
 IF NOT EXISTS(SELECT FROM public.noctua_sync_protocols WHERE owner_id=principal AND academic_scope=3) THEN
  RAISE EXCEPTION 'phase C client required' USING ERRCODE='42501'; END IF;
 IF jsonb_typeof(p_changes) IS DISTINCT FROM 'array' THEN RAISE EXCEPTION 'invalid changes'; END IF;
 FOR change IN SELECT * FROM jsonb_array_elements(p_changes) LOOP
  IF change->>'kind' NOT IN ('classes','terms','students','activities','grades','lessons','marks','recompositions') OR change->>'kind' IS NULL THEN
   RAISE EXCEPTION 'entity outside phase C scope' USING ERRCODE='22023'; END IF;
  PERFORM public.noctua_academic_validate(change->>'kind',change->'key',change->'payload');
  IF change->'payload'='null'::jsonb THEN CONTINUE; END IF;
  SELECT payload INTO previous FROM public.noctua_academic_versions WHERE owner_id=principal AND kind=change->>'kind'
   AND key=change->'key' AND NOT deleted ORDER BY revision DESC LIMIT 1;
  IF change->>'kind'='classes' AND previous IS NOT NULL AND previous->'year' IS DISTINCT FROM change->'payload'->'year' THEN
   RAISE EXCEPTION 'class year is immutable' USING ERRCODE='22023'; END IF;
  IF change->>'kind' IN ('activities','lessons') AND previous IS NOT NULL AND previous->'classId' IS DISTINCT FROM change->'payload'->'classId' THEN
   RAISE EXCEPTION 'academic class identity is immutable' USING ERRCODE='22023'; END IF;
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
   OR NOT EXISTS(SELECT FROM public.noctua_sync_protocols WHERE owner_id=principal AND academic_scope=2) THEN
  RAISE EXCEPTION 'authorized phase B client required' USING ERRCODE='42501'; END IF;
 RETURN public.noctua_v2_pull_unfenced(p_after);
END $$;
CREATE OR REPLACE FUNCTION public.noctua_pull_academic_c(p_after bigint)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal();
BEGIN
 IF principal IS NULL OR NOT EXISTS(SELECT FROM public.noctua_academic_test_accounts WHERE owner_id=principal AND allowed)
   OR NOT EXISTS(SELECT FROM public.noctua_sync_protocols WHERE owner_id=principal AND academic_scope=3) THEN
  RAISE EXCEPTION 'authorized phase C client required' USING ERRCODE='42501'; END IF;
 RETURN public.noctua_v2_pull_unfenced(p_after);
END $$;
REVOKE ALL ON FUNCTION public.noctua_academic_status_c(),public.noctua_enable_academic_c(bigint),
 public.noctua_push_academic_c(uuid,jsonb),public.noctua_pull_academic_c(bigint) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.noctua_academic_status_c(),public.noctua_enable_academic_c(bigint),
 public.noctua_push_academic_c(uuid,jsonb),public.noctua_pull_academic_c(bigint) TO authenticated;
COMMIT;
