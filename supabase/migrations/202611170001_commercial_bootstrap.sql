-- Phase 3.1. Apply only after the seven original A-E migrations, by separate authorization.
-- No accounts are granted access here. No v1/test account is migrated.
BEGIN;
CREATE TABLE public.noctua_commercial_rollout (
 singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton), enabled boolean NOT NULL DEFAULT false
);
INSERT INTO public.noctua_commercial_rollout VALUES(true,false);
CREATE TABLE public.noctua_commercial_accounts (
 owner_id uuid PRIMARY KEY REFERENCES auth.users(id), enabled boolean NOT NULL DEFAULT false,
 granted_at timestamptz NOT NULL DEFAULT statement_timestamp(), initialized_at timestamptz,
 policy_version integer NOT NULL DEFAULT 1 CHECK(policy_version=1)
);
ALTER TABLE public.noctua_commercial_rollout ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.noctua_commercial_accounts ENABLE ROW LEVEL SECURITY;
-- Admin-managed entitlements, not the temporary test allowlist. No client table access.
REVOKE ALL ON public.noctua_commercial_rollout,public.noctua_commercial_accounts FROM PUBLIC,anon,authenticated;

CREATE FUNCTION public.noctua_commercial_status()
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal(); grant_row public.noctua_commercial_accounts;
 reason text:='INELIGIBLE'; initialized boolean:=false; seq bigint:=0;
BEGIN
 IF principal IS NULL THEN RAISE EXCEPTION 'active confirmed session required' USING ERRCODE='42501'; END IF;
 SELECT * INTO grant_row FROM public.noctua_commercial_accounts WHERE owner_id=principal;
 IF principal='f624fdc9-2225-4211-8efe-46f9af2c53e5'::uuid THEN reason:='REVIEW';
 ELSIF grant_row.enabled IS TRUE AND EXISTS(SELECT FROM public.noctua_commercial_rollout WHERE singleton AND enabled) THEN
  reason:='REVIEW';
  -- Any test enrollment, v1 data (including tombstones/receipts), or unmanaged v2 state needs its own procedure.
  IF NOT EXISTS(SELECT FROM public.noctua_academic_test_accounts WHERE owner_id=principal) AND
     NOT EXISTS(SELECT FROM public.noctua_sync_heads WHERE owner_id=principal AND sequence<>0) AND
     NOT EXISTS(SELECT FROM public.noctua_class_bundles WHERE owner_id=principal) AND
     NOT EXISTS(SELECT FROM public.noctua_class_versions WHERE owner_id=principal) AND
     NOT EXISTS(SELECT FROM public.noctua_sync_receipts WHERE owner_id=principal) THEN
   IF grant_row.initialized_at IS NOT NULL THEN
    IF EXISTS(SELECT FROM public.noctua_sync_protocols WHERE owner_id=principal AND protocol=2 AND academic_scope=5) AND
       EXISTS(SELECT FROM public.noctua_academic_heads WHERE owner_id=principal) THEN
     reason:='READY'; initialized:=true;
     SELECT coalesce(sequence,0) INTO seq FROM public.noctua_academic_heads WHERE owner_id=principal;
    END IF;
   ELSIF NOT EXISTS(SELECT FROM public.noctua_sync_protocols WHERE owner_id=principal) AND
      NOT EXISTS(SELECT FROM public.noctua_academic_heads WHERE owner_id=principal) AND
      NOT EXISTS(SELECT FROM public.noctua_academic_batches WHERE owner_id=principal) AND
      NOT EXISTS(SELECT FROM public.noctua_academic_records WHERE owner_id=principal) AND
      NOT EXISTS(SELECT FROM public.noctua_academic_versions WHERE owner_id=principal) AND
      NOT EXISTS(SELECT FROM public.noctua_academic_receipts WHERE owner_id=principal) AND
      NOT EXISTS(SELECT FROM public.noctua_academic_links WHERE owner_id=principal) AND
      NOT EXISTS(SELECT FROM public.noctua_academic_uniques WHERE owner_id=principal) AND
      NOT EXISTS(SELECT FROM public.noctua_e_transfers WHERE owner_id=principal) AND
      NOT EXISTS(SELECT FROM public.noctua_e_parts WHERE owner_id=principal) AND
      NOT EXISTS(SELECT FROM public.noctua_e_decisions WHERE owner_id=principal) THEN reason:='NEW';
   END IF;
  END IF;
 END IF;
 RETURN jsonb_build_object('eligible',reason IN('NEW','READY'),'initialized',initialized,
  'reason',reason,'academicSequence',coalesce(seq,0));
END $$;

CREATE FUNCTION public.noctua_ensure_academic_e()
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal(); result jsonb;
BEGIN
 IF principal IS NULL THEN RAISE EXCEPTION 'active confirmed session required' USING ERRCODE='42501'; END IF;
 -- Hold policy/entitlement stable through initialization. The v1 owner lock serializes old writers too.
 PERFORM 1 FROM public.noctua_commercial_rollout WHERE singleton FOR SHARE;
 PERFORM 1 FROM public.noctua_commercial_accounts WHERE owner_id=principal FOR UPDATE;
 result:=public.noctua_commercial_status();
 IF result->>'reason'<>'NEW' THEN RETURN result; END IF;
 INSERT INTO public.noctua_sync_heads(owner_id) VALUES(principal) ON CONFLICT DO NOTHING;
 PERFORM 1 FROM public.noctua_sync_heads WHERE owner_id=principal FOR UPDATE;
 result:=public.noctua_commercial_status(); -- Recheck after competing v1 write/initialization.
 IF result->>'reason'<>'NEW' THEN RETURN result; END IF;
 INSERT INTO public.noctua_sync_protocols(owner_id,protocol,academic_scope) VALUES(principal,2,5);
 INSERT INTO public.noctua_academic_heads(owner_id) VALUES(principal);
 UPDATE public.noctua_commercial_accounts SET initialized_at=statement_timestamp() WHERE owner_id=principal;
 RETURN public.noctua_commercial_status();
END $$;

CREATE FUNCTION public.noctua_e_authorized()
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal(); commercial jsonb;
BEGIN
 IF principal IS NULL THEN RETURN false; END IF;
 -- Preserve the old test path; do not insert a commercial user into its allowlist.
 IF EXISTS(SELECT FROM public.noctua_academic_test_accounts WHERE owner_id=principal AND allowed) THEN RETURN true; END IF;
 commercial:=public.noctua_commercial_status();
 RETURN commercial->>'reason'='READY';
END $$;

CREATE OR REPLACE FUNCTION public.noctua_e_principal()
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal();
BEGIN
 IF principal IS NULL OR NOT public.noctua_e_authorized() OR
    NOT EXISTS(SELECT FROM public.noctua_sync_protocols WHERE owner_id=principal AND academic_scope=5)
 THEN RAISE EXCEPTION 'authorized phase E account required' USING ERRCODE='42501'; END IF;
 RETURN principal;
END $$;

-- Change ONLY the authorization expression in the reviewed original guarded writer.
-- Abort on an unexpected definition; preserve every graph/history/audit predicate and its ACL.
DO $guard$
DECLARE definition text; original text:='NOT EXISTS(SELECT FROM public.noctua_academic_test_accounts WHERE owner_id=principal AND allowed)';
BEGIN
 SELECT pg_get_functiondef('public.noctua_e_guarded_write(uuid,jsonb)'::regprocedure) INTO definition;
 IF (length(definition)-length(replace(definition,original,'')))/length(original)<>1 THEN
  RAISE EXCEPTION 'Unexpected phase E authorization guard; separate review required'; END IF;
 EXECUTE replace(definition,original,'NOT public.noctua_e_authorized()');
END $guard$;

REVOKE ALL ON FUNCTION public.noctua_commercial_status(),public.noctua_ensure_academic_e(),public.noctua_e_authorized(),public.noctua_e_principal() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.noctua_commercial_status(),public.noctua_ensure_academic_e() TO authenticated;
-- Existing E RPC privileges, RLS policies, revisions, receipts and academic validators are untouched.
NOTIFY pgrst,'reload schema';
COMMIT;
