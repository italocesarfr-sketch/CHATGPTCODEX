-- NOcTuA 0.21.0 foundation. Apply with the Supabase SQL editor as an administrator.
-- No keys in this file. No academic uploads run during installation. Re-runnable.
BEGIN;
CREATE TABLE IF NOT EXISTS public.noctua_sync_heads (
    owner_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE RESTRICT,
    sequence bigint NOT NULL DEFAULT 0 CHECK(sequence >= 0)
);
CREATE TABLE IF NOT EXISTS public.noctua_class_bundles (
    owner_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    id text NOT NULL CHECK(length(id) BETWEEN 1 AND 128),
    revision bigint NOT NULL CHECK(revision > 0), sequence bigint NOT NULL CHECK(sequence > 0),
    deleted boolean NOT NULL, payload jsonb,
    PRIMARY KEY(owner_id,id), UNIQUE(owner_id,sequence),
    CHECK((deleted AND payload IS NULL) OR (NOT deleted AND payload IS NOT NULL))
);
CREATE TABLE IF NOT EXISTS public.noctua_class_versions (
    owner_id uuid NOT NULL, id text NOT NULL, revision bigint NOT NULL,
    sequence bigint NOT NULL, deleted boolean NOT NULL, payload jsonb,
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY(owner_id,id,revision), UNIQUE(owner_id,sequence),
    FOREIGN KEY(owner_id,id) REFERENCES public.noctua_class_bundles(owner_id,id) ON DELETE RESTRICT
);
CREATE TABLE IF NOT EXISTS public.noctua_sync_receipts (
    owner_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    operation_id uuid NOT NULL, request jsonb NOT NULL, response jsonb NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY(owner_id,operation_id)
);
ALTER TABLE public.noctua_sync_heads ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.noctua_class_bundles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.noctua_class_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.noctua_sync_receipts ENABLE ROW LEVEL SECURITY;
-- Direct writes are never granted: ownership and revisions are assigned only by the RPC.
REVOKE ALL ON public.noctua_sync_heads, public.noctua_class_bundles,
    public.noctua_class_versions, public.noctua_sync_receipts FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.noctua_sync_heads, public.noctua_class_bundles,
    public.noctua_class_versions, public.noctua_sync_receipts TO authenticated;
-- Check confirmation in server-owned Auth data, including direct SELECT access.
-- No client metadata/email or caller-supplied UUID is used as an authority.
CREATE OR REPLACE FUNCTION public.noctua_sync_principal()
RETURNS uuid LANGUAGE sql STABLE SECURITY DEFINER SET search_path=pg_catalog AS $$
    SELECT u.id FROM auth.users u WHERE u.id=auth.uid()
        AND u.email_confirmed_at IS NOT NULL AND NOT COALESCE(u.is_anonymous,false)
$$;
REVOKE ALL ON FUNCTION public.noctua_sync_principal() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.noctua_sync_principal() TO authenticated;
DROP POLICY IF EXISTS own_rows ON public.noctua_sync_heads;
CREATE POLICY own_rows ON public.noctua_sync_heads FOR SELECT TO authenticated USING(owner_id=(SELECT public.noctua_sync_principal()));
DROP POLICY IF EXISTS own_rows ON public.noctua_class_bundles;
CREATE POLICY own_rows ON public.noctua_class_bundles FOR SELECT TO authenticated USING(owner_id=(SELECT public.noctua_sync_principal()));
DROP POLICY IF EXISTS own_rows ON public.noctua_class_versions;
CREATE POLICY own_rows ON public.noctua_class_versions FOR SELECT TO authenticated USING(owner_id=(SELECT public.noctua_sync_principal()));
DROP POLICY IF EXISTS own_rows ON public.noctua_sync_receipts;
CREATE POLICY own_rows ON public.noctua_sync_receipts FOR SELECT TO authenticated USING(owner_id=(SELECT public.noctua_sync_principal()));

CREATE OR REPLACE FUNCTION public.noctua_validate_class(p_id text,p_payload jsonb)
RETURNS void LANGUAGE plpgsql SET search_path=pg_catalog AS $$
DECLARE s jsonb; t jsonb; previous_end date; start_day date; end_day date; n integer:=0; field text;
BEGIN
    IF jsonb_typeof(p_payload) IS DISTINCT FROM 'object' OR
       (SELECT array_agg(k ORDER BY k) FROM jsonb_object_keys(p_payload) k) IS DISTINCT FROM ARRAY['school','terms']::text[] THEN
        RAISE EXCEPTION 'invalid class bundle';
    END IF;
    s:=p_payload->'school';
    IF jsonb_typeof(s) IS DISTINCT FROM 'object' OR
       (SELECT array_agg(k ORDER BY k) FROM jsonb_object_keys(s) k) IS DISTINCT FROM
       ARRAY['countJustified','duration','high','id','low','name','scale','schedule','shift','stage','startPresent','subject','year']::text[] THEN
        RAISE EXCEPTION 'invalid class fields';
    END IF;
    FOREACH field IN ARRAY ARRAY['id','name','stage','subject','shift','schedule'] LOOP
        IF jsonb_typeof(s->field) IS DISTINCT FROM 'string' THEN RAISE EXCEPTION 'invalid text field'; END IF;
    END LOOP;
    FOREACH field IN ARRAY ARRAY['year','duration','scale','low','high'] LOOP
        IF jsonb_typeof(s->field) IS DISTINCT FROM 'number' THEN RAISE EXCEPTION 'invalid numeric field'; END IF;
    END LOOP;
    IF s->>'id' IS DISTINCT FROM p_id OR (s->>'year')::integer NOT BETWEEN 2000 AND 2100 OR
       (s->>'duration')::integer NOT BETWEEN 1 AND 240 OR (s->>'scale')::numeric<=0 OR
       (s->>'low')::numeric<0 OR (s->>'low')::numeric>=(s->>'high')::numeric OR
       (s->>'high')::numeric>(s->>'scale')::numeric OR
       length(btrim(s->>'name'))=0 OR length(btrim(s->>'subject'))=0 OR
       s->>'schedule' !~ '^(1[0-2]|[0-9])(,(1[0-2]|[0-9])){6}$' OR
       jsonb_typeof(s->'countJustified')<>'boolean' OR jsonb_typeof(s->'startPresent')<>'boolean' THEN
        RAISE EXCEPTION 'invalid class values';
    END IF;
    IF jsonb_typeof(p_payload->'terms') IS DISTINCT FROM 'array' OR jsonb_array_length(p_payload->'terms')<>4 THEN
        RAISE EXCEPTION 'four terms required';
    END IF;
    FOR t IN SELECT value FROM jsonb_array_elements(p_payload->'terms') ORDER BY (value->>'number')::integer LOOP
        n:=n+1;
        IF (SELECT array_agg(k ORDER BY k) FROM jsonb_object_keys(t) k) IS DISTINCT FROM ARRAY['classId','end','number','start']::text[] OR
           t->>'classId' IS DISTINCT FROM p_id OR (t->>'number')::integer IS DISTINCT FROM n THEN
            RAISE EXCEPTION 'invalid term identity';
        END IF;
        IF jsonb_typeof(t->'classId') IS DISTINCT FROM 'string' OR jsonb_typeof(t->'start') IS DISTINCT FROM 'string' OR
           jsonb_typeof(t->'end') IS DISTINCT FROM 'string' OR jsonb_typeof(t->'number') IS DISTINCT FROM 'number' THEN RAISE EXCEPTION 'invalid term types'; END IF;
        IF t->>'start' !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' OR t->>'end' !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN RAISE EXCEPTION 'invalid date'; END IF;
        start_day:=(t->>'start')::date; end_day:=(t->>'end')::date;
        IF start_day>end_day OR extract(year FROM start_day)<>(s->>'year')::integer OR
           extract(year FROM end_day)<>(s->>'year')::integer OR (previous_end IS NOT NULL AND start_day<=previous_end) THEN
            RAISE EXCEPTION 'invalid term period';
        END IF;
        previous_end:=end_day;
    END LOOP;
END $$;
REVOKE ALL ON FUNCTION public.noctua_validate_class(text,jsonb) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.noctua_push_class(p_operation uuid,p_id text,p_base bigint,p_deleted boolean,p_payload jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE owner uuid:=auth.uid(); old public.noctua_class_bundles; receipt public.noctua_sync_receipts;
    request jsonb; result jsonb; seq bigint; accepted boolean;
BEGIN
    IF owner IS NULL OR NOT EXISTS(SELECT 1 FROM auth.users WHERE id=owner AND email_confirmed_at IS NOT NULL AND NOT COALESCE(is_anonymous,false)) THEN
        RAISE EXCEPTION 'confirmed authentication required' USING ERRCODE='42501';
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
DECLARE owner uuid:=auth.uid(); result jsonb;
BEGIN
    IF owner IS NULL OR NOT EXISTS(SELECT 1 FROM auth.users WHERE id=owner AND email_confirmed_at IS NOT NULL AND NOT COALESCE(is_anonymous,false)) THEN
        RAISE EXCEPTION 'confirmed authentication required' USING ERRCODE='42501';
    END IF;
    IF p_after IS NULL OR p_after<0 THEN RAISE EXCEPTION 'invalid cursor'; END IF;
    SELECT COALESCE(jsonb_agg(jsonb_build_object('id',id,'revision',revision,'sequence',sequence,'deleted',deleted,
        'payload',COALESCE(payload::text,'')) ORDER BY sequence),'[]'::jsonb) INTO result
    FROM (SELECT * FROM public.noctua_class_bundles WHERE owner_id=owner AND sequence>p_after ORDER BY sequence LIMIT 100) page;
    RETURN result;
END $$;
REVOKE ALL ON FUNCTION public.noctua_push_class(uuid,text,bigint,boolean,jsonb),public.noctua_pull_classes(bigint) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.noctua_push_class(uuid,text,bigint,boolean,jsonb),public.noctua_pull_classes(bigint) TO authenticated;
COMMENT ON TABLE public.noctua_class_bundles IS 'Stage 1: class settings and terms only; not an academic cloud backup';
COMMIT;
