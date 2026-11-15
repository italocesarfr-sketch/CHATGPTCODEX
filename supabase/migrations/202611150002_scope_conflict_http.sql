-- Candidate ONLY: requires separate administrative approval; NEVER applied hosted here.
-- Keep owner/session checks, predecessor checks, locks and revision comparison unchanged.
-- A stale head is a domain conflict, not a transaction that PostgREST should retry.
BEGIN;
DO $$ BEGIN
 IF to_regprocedure('public.noctua_enable_academic_c(bigint)') IS NULL OR
    to_regprocedure('public.noctua_enable_academic_d(bigint)') IS NULL OR
    to_regprocedure('public.noctua_enable_academic_e(bigint)') IS NULL THEN
  RAISE EXCEPTION 'complete A-E installation required';
 END IF;
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
 IF scope NOT IN(2,3) THEN RAISE EXCEPTION 'phase D client required' USING ERRCODE='42501'; END IF;
 IF scope=3 THEN RETURN public.noctua_academic_status_c(); END IF;
 IF seq IS DISTINCT FROM p_expected_sequence THEN RAISE EXCEPTION 'receive changes before expanding scope' USING ERRCODE='PT409'; END IF;
 UPDATE public.noctua_sync_protocols SET academic_scope=3 WHERE owner_id=principal;
 RETURN public.noctua_academic_status_c();
END $$;

CREATE OR REPLACE FUNCTION public.noctua_enable_academic_d(p_expected_sequence bigint)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal(); scope integer; seq bigint;
BEGIN
 IF principal IS NULL OR NOT EXISTS(SELECT FROM public.noctua_academic_test_accounts WHERE owner_id=principal AND allowed) THEN RAISE EXCEPTION 'authorized test account required' USING ERRCODE='42501'; END IF;
 PERFORM 1 FROM public.noctua_sync_heads WHERE owner_id=principal FOR UPDATE;
 SELECT academic_scope INTO scope FROM public.noctua_sync_protocols WHERE owner_id=principal FOR UPDATE;
 IF scope NOT IN(3,4) OR scope IS NULL THEN RAISE EXCEPTION 'phase C transition required' USING ERRCODE='42501'; END IF;
 SELECT sequence INTO seq FROM public.noctua_academic_heads WHERE owner_id=principal FOR UPDATE;
 IF scope=4 THEN RETURN public.noctua_academic_status_c(); END IF;
 IF seq IS DISTINCT FROM p_expected_sequence THEN RAISE EXCEPTION 'receive changes before expanding scope' USING ERRCODE='PT409'; END IF;
 UPDATE public.noctua_sync_protocols SET academic_scope=4 WHERE owner_id=principal;
 RETURN public.noctua_academic_status_c();
END $$;

CREATE OR REPLACE FUNCTION public.noctua_enable_academic_e(p_expected_sequence bigint)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal(); scope integer; seq bigint;
BEGIN
 IF principal IS NULL OR NOT EXISTS(SELECT FROM public.noctua_academic_test_accounts WHERE owner_id=principal AND allowed)
 THEN RAISE EXCEPTION 'authorized test account required' USING ERRCODE='42501'; END IF;
 PERFORM 1 FROM public.noctua_sync_heads WHERE owner_id=principal FOR UPDATE;
 SELECT academic_scope INTO scope FROM public.noctua_sync_protocols WHERE owner_id=principal FOR UPDATE;
 IF scope IS NULL OR scope NOT IN(4,5) THEN RAISE EXCEPTION 'phase D transition required' USING ERRCODE='42501'; END IF;
 SELECT sequence INTO seq FROM public.noctua_academic_heads WHERE owner_id=principal FOR UPDATE;
 IF scope=5 THEN RETURN public.noctua_academic_status_c(); END IF;
 IF seq IS DISTINCT FROM p_expected_sequence THEN RAISE EXCEPTION 'receive changes before expanding scope' USING ERRCODE='PT409'; END IF;
 UPDATE public.noctua_sync_protocols SET academic_scope=5 WHERE owner_id=principal;
 RETURN public.noctua_academic_status_c();
END $$;
COMMIT;
