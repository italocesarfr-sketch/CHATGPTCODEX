-- Phase 3.4, PREPARED ONLY. Explicit administrative roster, never domain/user_metadata grants.
-- Requires the reviewed 202611170001 commercial migration, original A-E, no H13/PT409.
BEGIN;
SET LOCAL lock_timeout='5s';
DO $preflight$
BEGIN
 IF NOT EXISTS(SELECT FROM pg_proc WHERE oid=to_regprocedure('public.noctua_ensure_academic_e()')
   AND pg_get_userbyid(proowner)=current_user AND md5(prosrc)='5643946c948863d959da836be1975cc9') THEN
  RAISE EXCEPTION 'Unexpected commercial initializer or migration role; separate review required';
 END IF;
 IF NOT EXISTS(SELECT FROM pg_proc p JOIN pg_language l ON l.oid=p.prolang
  WHERE p.oid=to_regprocedure('public.noctua_academic_definition(text)') AND l.lanname='sql') OR
  EXISTS(SELECT FROM pg_proc WHERE oid IN(to_regprocedure('public.noctua_enable_academic_c(bigint)'),
   to_regprocedure('public.noctua_enable_academic_d(bigint)'),to_regprocedure('public.noctua_enable_academic_e(bigint)'))
   AND position('PT409' in prosrc)>0) THEN
  RAISE EXCEPTION 'Unsupported H13/PT409 candidate or missing original protocol; separate review required';
 END IF;
END $preflight$;

CREATE TABLE public.noctua_commercial_roster (
 id uuid PRIMARY KEY DEFAULT pg_catalog.gen_random_uuid(),
 email text NOT NULL UNIQUE CHECK(email=lower(btrim(email)) AND length(email) BETWEEN 3 AND 254 AND position('@' in email)>1),
 institution_reference text NOT NULL CHECK(institution_reference ~ '^[A-Za-z0-9._:-]{1,120}$'),
 enabled boolean NOT NULL DEFAULT false,
 created_at timestamptz NOT NULL DEFAULT statement_timestamp(),
 expires_at timestamptz NOT NULL CHECK(expires_at>created_at)
);
-- Immutable provenance only, NOT permission to read an institution's academic data.
CREATE TABLE public.noctua_commercial_claims (
 owner_id uuid PRIMARY KEY REFERENCES auth.users(id),
 roster_id uuid NOT NULL UNIQUE REFERENCES public.noctua_commercial_roster(id),
 institution_reference text NOT NULL,
 granted_at timestamptz NOT NULL DEFAULT statement_timestamp(),
 policy_version integer NOT NULL DEFAULT 1 CHECK(policy_version=1)
);
ALTER TABLE public.noctua_commercial_roster ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.noctua_commercial_claims ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.noctua_commercial_roster,public.noctua_commercial_claims FROM PUBLIC,anon,authenticated;

CREATE FUNCTION public.noctua_commercial_claim_immutable() RETURNS trigger
LANGUAGE plpgsql SET search_path=pg_catalog AS $$
BEGIN RAISE EXCEPTION 'Commercial claim provenance is immutable'; END $$;
REVOKE ALL ON FUNCTION public.noctua_commercial_claim_immutable() FROM PUBLIC,anon,authenticated;
CREATE TRIGGER noctua_commercial_claim_immutable BEFORE UPDATE OR DELETE ON public.noctua_commercial_claims
 FOR EACH ROW EXECUTE FUNCTION public.noctua_commercial_claim_immutable();

CREATE FUNCTION public.noctua_provision_commercial() RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal(); verified_email text;
 roster public.noctua_commercial_roster; status jsonb;
BEGIN
 IF principal IS NULL THEN RAISE EXCEPTION 'Active confirmed session required' USING ERRCODE='42501'; END IF;
 -- Same lock order as the initializer: policy -> roster (new path only) -> entitlement -> v1 head.
 PERFORM 1 FROM public.noctua_commercial_rollout WHERE singleton FOR SHARE;
 IF NOT EXISTS(SELECT FROM public.noctua_commercial_rollout WHERE singleton AND enabled) OR
    EXISTS(SELECT FROM public.noctua_commercial_accounts WHERE owner_id=principal) OR
    EXISTS(SELECT FROM public.noctua_academic_test_accounts WHERE owner_id=principal) THEN RETURN false; END IF;
 SELECT lower(btrim(u.email)) INTO verified_email FROM auth.users u
  WHERE u.id=principal AND u.email_confirmed_at IS NOT NULL AND NOT coalesce(u.is_anonymous,false);
 IF verified_email IS NULL THEN RETURN false; END IF;
 SELECT * INTO roster FROM public.noctua_commercial_roster
  WHERE email=verified_email AND enabled AND expires_at>statement_timestamp() FOR UPDATE;
 IF NOT FOUND OR roster.expires_at<=clock_timestamp() OR
    EXISTS(SELECT FROM public.noctua_commercial_claims WHERE owner_id=principal OR roster_id=roster.id) THEN RETURN false; END IF;
 -- This subtransaction rolls the tentative grant/head back when the original eligibility
 -- guard refuses legacy/v1/unmanaged state. It never adopts or clears that state.
 BEGIN
  INSERT INTO public.noctua_commercial_accounts(owner_id,enabled) VALUES(principal,true) ON CONFLICT DO NOTHING;
  IF NOT FOUND THEN RETURN false; END IF;
  INSERT INTO public.noctua_sync_heads(owner_id) VALUES(principal) ON CONFLICT DO NOTHING;
  PERFORM 1 FROM public.noctua_sync_heads WHERE owner_id=principal FOR UPDATE;
  status:=public.noctua_commercial_status();
  IF status->>'reason'<>'NEW' THEN RAISE EXCEPTION 'Not a new eligible commercial account' USING ERRCODE='P0034'; END IF;
  INSERT INTO public.noctua_commercial_claims(owner_id,roster_id,institution_reference)
   VALUES(principal,roster.id,roster.institution_reference);
  RETURN true;
 EXCEPTION WHEN SQLSTATE 'P0034' THEN RETURN false;
 END;
END $$;
REVOKE ALL ON FUNCTION public.noctua_provision_commercial() FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.noctua_ensure_academic_e()
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal(); result jsonb;
BEGIN
 IF principal IS NULL THEN RAISE EXCEPTION 'active confirmed session required' USING ERRCODE='42501'; END IF;
 -- Hold policy/entitlement stable through initialization. The v1 owner lock serializes old writers too.
 PERFORM 1 FROM public.noctua_commercial_rollout WHERE singleton FOR SHARE;
 PERFORM public.noctua_provision_commercial();
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
-- Existing public signature/ACL retained; no new public RPC, no account granted by deployment.
NOTIFY pgrst,'reload schema';
COMMIT;
