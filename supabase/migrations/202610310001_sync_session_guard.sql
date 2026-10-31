-- Additional guard for online revocation; no academic row is changed.
-- Apply AFTER 202610300001_class_sync.sql. Same RPC signatures and payloads.
BEGIN;
DO $$ BEGIN
    IF to_regclass('auth.sessions') IS NULL OR
       (SELECT count(*) FROM information_schema.columns WHERE table_schema='auth' AND table_name='sessions' AND column_name IN ('id','user_id','not_after'))<>3 THEN
        RAISE EXCEPTION 'Expected Supabase Auth sessions schema unavailable; no change applied';
    END IF;
    IF NOT has_table_privilege(
       (SELECT pg_get_userbyid(proowner) FROM pg_proc WHERE oid='public.noctua_sync_principal()'::regprocedure),
       'auth.sessions','SELECT') THEN
        RAISE EXCEPTION 'The principal function owner cannot inspect Auth sessions; no change applied';
    END IF;
END $$;
CREATE OR REPLACE FUNCTION public.noctua_sync_principal()
RETURNS uuid LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE claims jsonb:=auth.jwt(); sid uuid; principal uuid:=auth.uid();
BEGIN
    IF principal IS NULL OR claims->>'role' IS DISTINCT FROM 'authenticated' OR
       jsonb_typeof(claims->'exp') IS DISTINCT FROM 'number' OR
       COALESCE(claims->>'exp','') !~ '^[0-9]{1,12}$' OR
       COALESCE(claims->>'session_id','') !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' THEN
        RETURN NULL;
    END IF;
    IF (claims->>'exp')::bigint<=extract(epoch FROM statement_timestamp()) THEN RETURN NULL; END IF;
    sid:=(claims->>'session_id')::uuid;
    RETURN (SELECT u.id FROM auth.users u JOIN auth.sessions s ON s.user_id=u.id
        WHERE u.id=principal AND s.id=sid AND u.email_confirmed_at IS NOT NULL
          AND NOT COALESCE(u.is_anonymous,false)
          AND (s.not_after IS NULL OR s.not_after>statement_timestamp()));
END $$;
REVOKE ALL ON FUNCTION public.noctua_sync_principal() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.noctua_sync_principal() TO authenticated;
CREATE OR REPLACE FUNCTION public.noctua_push_class(p_operation uuid,p_id text,p_base bigint,p_deleted boolean,p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE owner uuid:=public.noctua_sync_principal(); old public.noctua_class_bundles; receipt public.noctua_sync_receipts;
    request jsonb; result jsonb; seq bigint; accepted boolean;
BEGIN
    IF owner IS NULL THEN
        RAISE EXCEPTION 'active confirmed session required' USING ERRCODE='42501';
    END IF;
    IF p_operation IS NULL OR p_id IS NULL OR length(p_id) NOT BETWEEN 1 AND 128 OR p_base IS NULL OR p_base<0 OR p_deleted IS NULL THEN RAISE EXCEPTION 'invalid operation'; END IF;
    IF (p_deleted AND p_payload IS NOT NULL) OR (NOT p_deleted AND p_payload IS NULL) THEN RAISE EXCEPTION 'invalid payload'; END IF;
    IF NOT p_deleted THEN
        IF octet_length(p_payload::text)>16384 THEN RAISE EXCEPTION 'payload too large'; END IF;
        PERFORM public.noctua_validate_class(p_id,p_payload);
    END IF;
    -- One owner lock orders all commits/cursors and concurrent retries, including first inserts.
    INSERT INTO public.noctua_sync_heads(owner_id) VALUES(owner) ON CONFLICT DO NOTHING;
    PERFORM 1 FROM public.noctua_sync_heads WHERE owner_id=owner FOR UPDATE;
    request:=jsonb_build_object('id',p_id,'base',p_base,'deleted',p_deleted,'payload',p_payload);
    SELECT * INTO receipt FROM public.noctua_sync_receipts WHERE owner_id=owner AND operation_id=p_operation;
    IF FOUND THEN
        IF receipt.request IS DISTINCT FROM request THEN RAISE EXCEPTION 'operation identity reused'; END IF;
        RETURN receipt.response;
    END IF;
    SELECT * INTO old FROM public.noctua_class_bundles WHERE owner_id=owner AND id=p_id;
    IF old.id IS NULL AND p_base<>0 THEN RAISE EXCEPTION 'missing base revision'; END IF;
    accepted:=COALESCE(old.revision,0)=p_base;
    IF accepted THEN
        UPDATE public.noctua_sync_heads SET sequence=sequence+1 WHERE owner_id=owner RETURNING sequence INTO seq;
        INSERT INTO public.noctua_class_bundles VALUES(owner,p_id,p_base+1,seq,p_deleted,p_payload)
        ON CONFLICT(owner_id,id) DO UPDATE SET revision=EXCLUDED.revision,sequence=EXCLUDED.sequence,deleted=EXCLUDED.deleted,payload=EXCLUDED.payload
        RETURNING * INTO old;
        INSERT INTO public.noctua_class_versions(owner_id,id,revision,sequence,deleted,payload)
        VALUES(owner,p_id,old.revision,seq,p_deleted,p_payload);
    END IF;
    result:=jsonb_build_object('operationId',p_operation::text,'accepted',accepted,'record',jsonb_build_object(
        'id',old.id,'revision',old.revision,'sequence',old.sequence,'deleted',old.deleted,'payload',COALESCE(old.payload::text,'')));
    INSERT INTO public.noctua_sync_receipts(owner_id,operation_id,request,response) VALUES(owner,p_operation,request,result);
    RETURN result;
END $$;
CREATE OR REPLACE FUNCTION public.noctua_pull_classes(p_after bigint)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE owner uuid:=public.noctua_sync_principal(); result jsonb;
BEGIN
    IF owner IS NULL THEN
        RAISE EXCEPTION 'active confirmed session required' USING ERRCODE='42501';
    END IF;
    IF p_after IS NULL OR p_after<0 THEN RAISE EXCEPTION 'invalid cursor'; END IF;
    SELECT COALESCE(jsonb_agg(jsonb_build_object('id',id,'revision',revision,'sequence',sequence,'deleted',deleted,
        'payload',COALESCE(payload::text,'')) ORDER BY sequence),'[]'::jsonb) INTO result
    FROM (SELECT * FROM public.noctua_class_bundles WHERE owner_id=owner AND sequence>p_after ORDER BY sequence LIMIT 100) page;
    RETURN result;
END $$;
REVOKE ALL ON FUNCTION public.noctua_push_class(uuid,text,bigint,boolean,jsonb),public.noctua_pull_classes(bigint) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.noctua_push_class(uuid,text,bigint,boolean,jsonb),public.noctua_pull_classes(bigint) TO authenticated;
COMMENT ON FUNCTION public.noctua_sync_principal() IS 'Confirmed owner and active, non-expired Supabase session; access JWT alone is insufficient after logout';
COMMIT;
