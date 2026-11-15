-- Candidate for separate administrative review; NEVER applied hosted by this task.
-- Add only the missing monotonic predecessor check to existing C; keep API/ACL/RLS.
BEGIN;
CREATE OR REPLACE FUNCTION public.noctua_enable_academic_c(p_expected_sequence bigint)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal(); scope integer; seq bigint;
BEGIN
 IF principal IS NULL OR NOT EXISTS(SELECT FROM public.noctua_academic_test_accounts WHERE owner_id=principal AND allowed) THEN
  RAISE EXCEPTION 'authorized test account required' USING ERRCODE='42501'; END IF;
 PERFORM 1 FROM public.noctua_sync_heads WHERE owner_id=principal FOR UPDATE;
 SELECT academic_scope INTO scope FROM public.noctua_sync_protocols WHERE owner_id=principal FOR UPDATE;
 IF scope IS NULL OR scope NOT IN(2,3) THEN RAISE EXCEPTION 'phase B transition required' USING ERRCODE='42501'; END IF;
 INSERT INTO public.noctua_academic_heads(owner_id) VALUES(principal) ON CONFLICT DO NOTHING;
 SELECT sequence INTO seq FROM public.noctua_academic_heads WHERE owner_id=principal FOR UPDATE;
 IF scope=3 THEN RETURN public.noctua_academic_status_c(); END IF;
 IF seq IS DISTINCT FROM p_expected_sequence THEN RAISE EXCEPTION 'receive changes before expanding scope' USING ERRCODE='40001'; END IF;
 UPDATE public.noctua_sync_protocols SET academic_scope=3 WHERE owner_id=principal;
 RETURN public.noctua_academic_status_c();
END $$;

COMMIT;
