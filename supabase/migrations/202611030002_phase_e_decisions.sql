-- E2/E3: immutable, owner-scoped decisions; CAS before any academic modification.
BEGIN;
CREATE TABLE IF NOT EXISTS public.noctua_e_decisions (
 owner_id uuid NOT NULL REFERENCES auth.users(id), resolution_id uuid NOT NULL,
 request jsonb NOT NULL, evidence jsonb NOT NULL, response jsonb NOT NULL,
 confirmed_at timestamptz NOT NULL DEFAULT statement_timestamp(), session_id uuid NOT NULL,
 PRIMARY KEY(owner_id,resolution_id)
);
ALTER TABLE public.noctua_e_decisions ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.noctua_e_decisions FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.noctua_e_decisions TO authenticated;
DROP POLICY IF EXISTS own_rows ON public.noctua_e_decisions;
CREATE POLICY own_rows ON public.noctua_e_decisions FOR SELECT TO authenticated USING(owner_id=public.noctua_sync_principal());

CREATE OR REPLACE FUNCTION public.noctua_decision_academic_e(p_resolution uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_e_principal(); result jsonb;
BEGIN
 SELECT response INTO result FROM public.noctua_e_decisions WHERE owner_id=principal AND resolution_id=p_resolution;
 RETURN result;
END $$;

CREATE OR REPLACE FUNCTION public.noctua_resolve_academic_e(p_resolution uuid,p_request jsonb,p_cancel boolean DEFAULT false)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_e_principal(); old public.noctua_e_decisions;
 head bigint; result jsonb; ev jsonb; current_record public.noctua_academic_records;
 base_payload jsonb; observed_payload jsonb; full_evidence jsonb:='[]'; wanted jsonb; observed_revision bigint;
BEGIN
 IF p_resolution IS NULL OR p_cancel IS NULL OR octet_length(p_request::text)>786432 THEN RAISE EXCEPTION 'invalid decision'; END IF;
 PERFORM public.noctua_academic_shape(p_request,'{"expectedSequence":"number","choice":"string","source":"string","localDigest":"string","hasChanges":"boolean","evidence":"array"}');
 IF p_request->>'expectedSequence' !~ '^[0-9]{1,18}$' OR p_request->>'choice' NOT IN('KEEP_LOCAL','KEEP_REMOTE') OR
  p_request->>'source' NOT IN('CONFLICT','RESTORE') OR p_request->>'localDigest' !~ '^[0-9a-f]{64}$' OR jsonb_array_length(p_request->'evidence')>16384
 THEN RAISE EXCEPTION 'invalid decision evidence'; END IF;
 PERFORM 1 FROM public.noctua_sync_heads WHERE owner_id=principal FOR UPDATE;
 SELECT * INTO old FROM public.noctua_e_decisions WHERE owner_id=principal AND resolution_id=p_resolution;
 IF FOUND THEN
  IF old.request IS DISTINCT FROM p_request THEN RAISE EXCEPTION 'decision identity reused'; END IF;
  RETURN old.response;
 END IF;
 SELECT sequence INTO head FROM public.noctua_academic_heads WHERE owner_id=principal FOR UPDATE;
 IF (SELECT count(*) FROM (SELECT DISTINCT item.value->>'kind',item.value->'key' FROM jsonb_array_elements(p_request->'evidence') item(value)) k)<>jsonb_array_length(p_request->'evidence') THEN RAISE EXCEPTION 'duplicate decision identity'; END IF;
 FOR ev IN SELECT * FROM jsonb_array_elements(p_request->'evidence') LOOP
  PERFORM public.noctua_academic_shape(ev,'{"kind":"string","key":"object","baseRevision":"number","revision":"number","local":"object?"}');
  IF ev->>'baseRevision' !~ '^[0-9]{1,18}$' OR ev->>'revision' !~ '^[0-9]{1,18}$' OR (ev->>'baseRevision')::bigint>(ev->>'revision')::bigint THEN RAISE EXCEPTION 'invalid decision revision'; END IF;
  PERFORM public.noctua_academic_validate(ev->>'kind',ev->'key',ev->'local');
  base_payload:=NULL;observed_payload:=NULL;
  IF (ev->>'baseRevision')::bigint>0 THEN
   SELECT payload INTO base_payload FROM public.noctua_academic_versions WHERE owner_id=principal AND kind=ev->>'kind' AND key=ev->'key' AND revision=(ev->>'baseRevision')::bigint;
   IF NOT FOUND THEN RAISE EXCEPTION 'unknown base evidence'; END IF;
  END IF;
  IF (ev->>'revision')::bigint>0 THEN
   SELECT payload INTO observed_payload FROM public.noctua_academic_versions WHERE owner_id=principal AND kind=ev->>'kind' AND key=ev->'key' AND revision=(ev->>'revision')::bigint;
   IF NOT FOUND THEN RAISE EXCEPTION 'unknown remote evidence'; END IF;
  END IF;
  full_evidence:=full_evidence||jsonb_build_array(ev||jsonb_build_object('base',base_payload,'remote',observed_payload));
  IF octet_length(full_evidence::text)>16777216 THEN RAISE EXCEPTION 'decision evidence exceeds capacity'; END IF;
 END LOOP;
 IF p_cancel OR head IS DISTINCT FROM (p_request->>'expectedSequence')::bigint THEN
  result:=jsonb_build_object('operation',p_resolution,'accepted',false,'sequence',NULL,'conflicts',jsonb_build_array(jsonb_build_object('reason',CASE WHEN p_cancel THEN 'LOCAL_CHANGED' ELSE 'REMOTE_CHANGED' END)));
 ELSE
  FOR ev IN SELECT * FROM jsonb_array_elements(full_evidence) LOOP
   SELECT * INTO current_record FROM public.noctua_academic_records WHERE owner_id=principal AND kind=ev->>'kind' AND key=ev->'key';
   IF coalesce(current_record.revision,0)<>(ev->>'revision')::bigint THEN RAISE EXCEPTION 'decision record changed' USING ERRCODE='40001'; END IF;
  END LOOP;
  IF (p_request->>'hasChanges')::boolean THEN
   result:=public.noctua_commit_academic_e(p_resolution);
  ELSE
   IF EXISTS(SELECT FROM public.noctua_e_transfers WHERE owner_id=principal AND operation_id=p_resolution) THEN RAISE EXCEPTION 'unexpected staged decision'; END IF;
   result:=jsonb_build_object('operation',p_resolution,'accepted',true,'sequence',head,'conflicts','[]'::jsonb);
  END IF;
  IF (result->>'accepted')::boolean THEN
   FOR ev IN SELECT * FROM jsonb_array_elements(full_evidence) LOOP
    wanted:=CASE WHEN p_request->>'choice'='KEEP_LOCAL' THEN ev->'local' ELSE ev->'remote' END;
    -- Absence in an old backup is not a deletion; local-only identities are retained.
    IF p_request->>'source'='RESTORE' AND ev->'local'='null' THEN wanted:=ev->'remote'; END IF;
    IF p_request->>'source'='RESTORE' AND (ev->>'revision')::bigint=0 THEN wanted:=ev->'local'; END IF;
    SELECT * INTO current_record FROM public.noctua_academic_records WHERE owner_id=principal AND kind=ev->>'kind' AND key=ev->'key';
    IF coalesce(current_record.payload,'null'::jsonb) IS DISTINCT FROM wanted THEN RAISE EXCEPTION 'decision differs from confirmed choice'; END IF;
   END LOOP;
  END IF;
 END IF;
 INSERT INTO public.noctua_e_decisions(owner_id,resolution_id,request,evidence,response,session_id)
 VALUES(principal,p_resolution,p_request,full_evidence,result,(auth.jwt()->>'session_id')::uuid);
 RETURN result;
END $$;
REVOKE ALL ON FUNCTION public.noctua_decision_academic_e(uuid),public.noctua_resolve_academic_e(uuid,jsonb,boolean) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.noctua_decision_academic_e(uuid),public.noctua_resolve_academic_e(uuid,jsonb,boolean) TO authenticated;

-- Closed, auditable decisions do not exhaust the abandoned-transfer quota.
CREATE OR REPLACE FUNCTION public.noctua_begin_academic_e(p_operation uuid,p_total integer,p_parts integer)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_e_principal(); t public.noctua_e_transfers;
BEGIN
 IF p_operation IS NULL OR p_total IS NULL OR p_total NOT BETWEEN 1 AND 16384 OR p_parts IS NULL OR p_parts NOT BETWEEN 1 AND p_total THEN RAISE EXCEPTION 'invalid manifest'; END IF;
 PERFORM 1 FROM public.noctua_sync_heads WHERE owner_id=principal FOR UPDATE;
 -- Bound abandoned unconfirmed transfers too; no client cleanup of audit evidence.
 IF NOT EXISTS(SELECT FROM public.noctua_e_transfers WHERE owner_id=principal AND operation_id=p_operation) AND
  (SELECT count(*) FROM public.noctua_e_transfers pending_transfer WHERE owner_id=principal AND NOT EXISTS(SELECT FROM public.noctua_academic_receipts r WHERE r.owner_id=pending_transfer.owner_id AND r.operation_id=pending_transfer.operation_id) AND NOT EXISTS(SELECT FROM public.noctua_e_decisions d WHERE d.owner_id=pending_transfer.owner_id AND d.resolution_id=pending_transfer.operation_id))>=4
 THEN RAISE EXCEPTION 'review pending transfers before creating another'; END IF;
 INSERT INTO public.noctua_e_transfers(owner_id,operation_id,total,parts) VALUES(principal,p_operation,p_total,p_parts) ON CONFLICT DO NOTHING;
 SELECT * INTO t FROM public.noctua_e_transfers WHERE owner_id=principal AND operation_id=p_operation FOR UPDATE;
 IF t.total<>p_total OR t.parts<>p_parts THEN RAISE EXCEPTION 'operation manifest reused'; END IF;
 RETURN jsonb_build_object('operation',p_operation,'total',t.total,'parts',t.parts);
END $$;
COMMIT;
