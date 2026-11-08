-- GENERATED FOR REVIEW ONLY. Administrative authorization still required.
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
-- SOURCE 202611010001_academic_protocol.sql SHA256 32e3e7f43e4c8e999c900bf9b4ae8307170cae42228261d0c4d461fcf3755120
-- Phase A protocol only: no changes to v1, Auth, Room or existing academic rows.
-- Requires BOTH 202610300001 and 202610310001; do not enable clients before phases B-E.
DO $$ BEGIN
 IF to_regprocedure('public.noctua_sync_principal()') IS NULL OR
    position('auth.sessions' in pg_get_functiondef('public.noctua_sync_principal()'::regprocedure))=0 THEN
  RAISE EXCEPTION 'Active-session guard migration required';
 END IF;
END $$;
-- Exact transport types derived from Room v11. JSON converters retain nested values.
CREATE OR REPLACE FUNCTION public.noctua_academic_definition(p_kind text)
RETURNS jsonb LANGUAGE sql IMMUTABLE SET search_path=pg_catalog AS $definition$
 SELECT ($registry${"classes":{"key":["id"],"fields":{"id":"string","name":"string","stage":"string","subject":"string","year":"integer","shift":"string","schedule":"string","duration":"integer","scale":"number","low":"number","high":"number","countJustified":"boolean","startPresent":"boolean"},"references":[],"unique":[]},"students":{"key":["id"],"fields":{"id":"string","personId":"string","classId":"string","name":"string","number":"integer","joined":"string","left":"string?","status":"string","notes":"string"},"references":[{"fields":["classId"],"kind":"classes","key":["id"]}],"unique":[]},"terms":{"key":["classId","number"],"fields":{"classId":"string","number":"integer","start":"string","end":"string"},"references":[{"fields":["classId"],"kind":"classes","key":["id"]}],"unique":[]},"lessons":{"key":["id"],"fields":{"id":"string","classId":"string","date":"string","ordinal":"integer","state":"string","note":"string"},"references":[{"fields":["classId"],"kind":"classes","key":["id"]}],"unique":[["classId","date","ordinal"]]},"marks":{"key":["lessonId","studentId"],"fields":{"lessonId":"string","studentId":"string","mark":"string","reason":"string?"},"references":[{"fields":["lessonId"],"kind":"lessons","key":["id"]},{"fields":["studentId"],"kind":"students","key":["id"]}],"unique":[]},"activities":{"key":["id"],"fields":{"id":"string","classId":"string","name":"string","type":"string","date":"string","term":"integer","maximum":"number","description":"string","notes":"string"},"references":[{"fields":["classId"],"kind":"classes","key":["id"]}],"unique":[]},"grades":{"key":["activityId","studentId"],"fields":{"activityId":"string","studentId":"string","value":"number?"},"references":[{"fields":["activityId"],"kind":"activities","key":["id"]},{"fields":["studentId"],"kind":"students","key":["id"]}],"unique":[]},"import_drafts":{"key":["id"],"fields":{"id":"string","classId":"string","name":"string","number":"integer?","position":"integer","page":"integer","reason":"string","reviewed":"boolean","allowHomonym":"boolean"},"references":[{"fields":["classId"],"kind":"classes","key":["id"]}],"unique":[]},"recompositions":{"key":["studentId","term"],"fields":{"studentId":"string","term":"integer","value":"number?","date":"string"},"references":[{"fields":["studentId"],"kind":"students","key":["id"]}],"unique":[]},"relocations":{"key":["sourceStudentId"],"fields":{"sourceStudentId":"string","targetStudentId":"string?","effectiveDate":"string","externalTarget":"string?","corrections":"array"},"references":[{"fields":["sourceStudentId"],"kind":"students","key":["id"]},{"fields":["targetStudentId"],"kind":"students","key":["id"]}],"unique":[["targetStudentId"]]},"export_rows":{"key":["id"],"fields":{"id":"string","classId":"string","studentId":"string?","position":"integer","manualHidden":"boolean"},"references":[{"fields":["classId"],"kind":"classes","key":["id"]},{"fields":["studentId"],"kind":"students","key":["id"]}],"unique":[["classId","position"],["studentId"]]},"class_archives":{"key":["classId"],"fields":{"classId":"string"},"references":[{"fields":["classId"],"kind":"classes","key":["id"]}],"unique":[]},"lesson_records":{"key":["id"],"fields":{"id":"string","classId":"string","date":"string","title":"string","description":"string"},"references":[{"fields":["classId"],"kind":"classes","key":["id"]}],"unique":[]},"preserved_histories":{"key":["classId"],"fields":{"classId":"string","content":"object"},"references":[],"unique":[]},"weekly_timetables":{"key":["id"],"fields":{"id":"string","year":"integer","effective":"string","revision":"integer","createdAt":"string","slots":"array"},"references":[],"unique":[["year","effective","revision"]]}}$registry$::jsonb)->p_kind;
$definition$;

CREATE OR REPLACE FUNCTION public.noctua_academic_shape(p_value jsonb,p_fields jsonb)
RETURNS void LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog AS $$
DECLARE field text; rule text; value jsonb; actual text;
BEGIN
 IF jsonb_typeof(p_value) IS DISTINCT FROM 'object' OR p_fields IS NULL OR
    (SELECT array_agg(k ORDER BY k) FROM jsonb_object_keys(p_value) k) IS DISTINCT FROM
    (SELECT array_agg(k ORDER BY k) FROM jsonb_object_keys(p_fields) k) THEN
  RAISE EXCEPTION 'invalid academic fields' USING ERRCODE='22023';
 END IF;
 FOR field,rule IN SELECT * FROM jsonb_each_text(p_fields) LOOP
  value:=p_value->field; actual:=jsonb_typeof(value);
  IF actual='null' AND right(rule,1)='?' THEN CONTINUE; END IF;
  rule:=rtrim(rule,'?');
  IF rule='integer' THEN
   IF actual IS DISTINCT FROM 'number' OR value::text !~ '^-?[0-9]{1,10}$' OR
      (value::text)::numeric NOT BETWEEN -2147483648 AND 2147483647 THEN
    RAISE EXCEPTION 'invalid academic integer' USING ERRCODE='22023';
   END IF;
  ELSIF actual IS DISTINCT FROM rule THEN
   RAISE EXCEPTION 'invalid academic type' USING ERRCODE='22023';
  END IF;
 END LOOP;
END $$;

CREATE OR REPLACE FUNCTION public.noctua_academic_key(p_kind text,p_payload jsonb)
RETURNS jsonb LANGUAGE sql IMMUTABLE SET search_path=pg_catalog AS $$
 SELECT jsonb_object_agg(field,p_payload->field)
 FROM jsonb_array_elements_text(public.noctua_academic_definition(p_kind)->'key') field;
$$;

CREATE OR REPLACE FUNCTION public.noctua_academic_validate(p_kind text,p_key jsonb,p_payload jsonb)
RETURNS void LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog AS $$
DECLARE definition jsonb:=public.noctua_academic_definition(p_kind); field text; item jsonb; d text;
BEGIN
 IF definition IS NULL OR jsonb_typeof(p_key) IS DISTINCT FROM 'object' OR octet_length(p_key::text)>1400 OR
    (SELECT array_agg(k ORDER BY k) FROM jsonb_object_keys(p_key) k) IS DISTINCT FROM
    (SELECT array_agg(k ORDER BY k) FROM jsonb_array_elements_text(definition->'key') k) THEN
  RAISE EXCEPTION 'invalid academic identity' USING ERRCODE='22023';
 END IF;
 -- A tombstone still carries a valid, typed identity.
 FOR field IN SELECT jsonb_array_elements_text(definition->'key') LOOP
  PERFORM public.noctua_academic_shape(jsonb_build_object(field,p_key->field),
     jsonb_build_object(field,definition->'fields'->field));
  IF jsonb_typeof(p_key->field)='null' OR (jsonb_typeof(p_key->field)='string' AND
     length(btrim(p_key->>field)) NOT BETWEEN 1 AND 512) THEN
   RAISE EXCEPTION 'invalid academic key value' USING ERRCODE='22023';
  END IF;
 END LOOP;
 IF p_payload IS NULL OR p_payload='null'::jsonb THEN RETURN; END IF;
 IF octet_length(p_payload::text)>524288 THEN RAISE EXCEPTION 'academic row too large' USING ERRCODE='22023'; END IF;
 PERFORM public.noctua_academic_shape(p_payload,definition->'fields');
 IF public.noctua_academic_key(p_kind,p_payload) IS DISTINCT FROM p_key THEN
  RAISE EXCEPTION 'academic key mismatch' USING ERRCODE='22023';
 END IF;
 -- Validate dates without rewriting them. Timestamps are checked by the DTO/graph validator too.
 FOREACH field IN ARRAY ARRAY['date','joined','left','start','end','effectiveDate','effective'] LOOP
  IF p_payload ? field AND p_payload->field<>'null'::jsonb THEN
   d:=p_payload->>field;
   IF d !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' OR (d::date)::text<>d THEN
    RAISE EXCEPTION 'invalid academic date' USING ERRCODE='22023';
   END IF;
  END IF;
 END LOOP;
 IF p_kind='students' AND ((p_payload->>'number')::integer<1 OR length(btrim(p_payload->>'personId'))=0 OR
     length(btrim(p_payload->>'name'))<2 OR p_payload->>'status' NOT IN ('Ativo','Transferido','Remanejado') OR
     (p_payload->>'left')<(p_payload->>'joined')) THEN RAISE EXCEPTION 'invalid enrollment'; END IF;
 IF p_kind='terms' AND ((p_payload->>'number')::integer NOT BETWEEN 1 AND 4 OR
     p_payload->>'start'>p_payload->>'end') THEN RAISE EXCEPTION 'invalid term'; END IF;
 IF p_kind='lessons' AND ((p_payload->>'ordinal')::integer<1 OR p_payload->>'state' NOT IN
     ('PREVISTA','REALIZADA','CANCELADA')) THEN RAISE EXCEPTION 'invalid lesson'; END IF;
 IF p_kind='marks' AND (p_payload->>'mark' NOT IN ('P','F','FJ','NR') OR
     (p_payload->'reason'<>'null'::jsonb AND (p_payload->>'mark'<>'FJ' OR p_payload->>'reason' NOT IN
      ('Atestado médico','Acompanhamento médico','Falecimento familiar','Convocação oficial','Atividade escolar ou esportiva','Outro')))) THEN
  RAISE EXCEPTION 'invalid attendance';
 END IF;
 IF p_kind='activities' AND ((p_payload->>'term')::integer NOT BETWEEN 1 AND 4 OR
     (p_payload->>'maximum')::numeric<=0 OR length(btrim(p_payload->>'name'))=0) THEN RAISE EXCEPTION 'invalid activity'; END IF;
 IF p_kind='recompositions' AND ((p_payload->>'term')::integer NOT BETWEEN 1 AND 4 OR
     ((p_payload->>'value')::numeric NOT BETWEEN 0 AND 10)) THEN RAISE EXCEPTION 'invalid recomposition'; END IF;
 IF p_kind='import_drafts' AND ((p_payload->>'position')::integer<0 OR (p_payload->>'page')::integer<0) THEN
  RAISE EXCEPTION 'invalid draft'; END IF;
 IF p_kind='relocations' THEN
  FOR item IN SELECT * FROM jsonb_array_elements(p_payload->'corrections') LOOP
   PERFORM public.noctua_academic_shape(item,
    '{"id":"string","confirmedAt":"string","previousDate":"string","effectiveDate":"string","previousTarget":"string","externalTarget":"string?","removed":"array","operation":"string","sourceStudentId":"string?","targetStudentId":"string?","targetClassId":"string?","targetLabel":"string?"}');
   -- Deep chain/audit validation is an explicit phase D gate. No audit field is dropped.
  END LOOP;
 END IF;
 IF p_kind='preserved_histories' THEN
  PERFORM public.noctua_academic_shape(p_payload->'content',
   '{"school":"object","students":"array","terms":"array","lessons":"array","marks":"array","activities":"array","grades":"array","recompositions":"array","relocations":"array"}');
 END IF;
 IF p_kind='weekly_timetables' THEN
  IF (p_payload->>'year')::integer NOT BETWEEN 2000 AND 2100 OR
     left(p_payload->>'effective',4)<>p_payload->>'year' OR (p_payload->>'revision')::integer<1 THEN
   RAISE EXCEPTION 'invalid timetable revision'; END IF;
  FOR item IN SELECT * FROM jsonb_array_elements(p_payload->'slots') LOOP
   PERFORM public.noctua_academic_shape(item,
    '{"id":"string","classId":"string","weekday":"integer","ordinal":"integer","className":"string","subject":"string","shift":"string"}');
   IF (item->>'weekday')::integer NOT BETWEEN 1 AND 7 OR (item->>'ordinal')::integer NOT BETWEEN 1 AND 12 THEN
    RAISE EXCEPTION 'invalid legacy timetable slot'; END IF;
  END LOOP;
 END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.noctua_academic_heads (
 owner_id uuid PRIMARY KEY REFERENCES auth.users(id), sequence bigint NOT NULL DEFAULT 0 CHECK(sequence>=0)
);
CREATE TABLE IF NOT EXISTS public.noctua_academic_batches (
 owner_id uuid NOT NULL REFERENCES auth.users(id), sequence bigint NOT NULL CHECK(sequence>0),
 operation_id uuid NOT NULL, confirmed_at timestamptz NOT NULL DEFAULT statement_timestamp(),
 PRIMARY KEY(owner_id,sequence), UNIQUE(owner_id,operation_id)
);
CREATE TABLE IF NOT EXISTS public.noctua_academic_records (
 owner_id uuid NOT NULL REFERENCES auth.users(id), kind text NOT NULL, key jsonb NOT NULL,
 revision bigint NOT NULL CHECK(revision>0), sequence bigint NOT NULL,
 deleted boolean NOT NULL, payload jsonb,
 PRIMARY KEY(owner_id,kind,key), UNIQUE(owner_id,kind,key,deleted),
 FOREIGN KEY(owner_id,sequence) REFERENCES public.noctua_academic_batches(owner_id,sequence),
 CHECK((deleted AND payload IS NULL) OR (NOT deleted AND jsonb_typeof(payload)='object'))
);
CREATE TABLE IF NOT EXISTS public.noctua_academic_versions (
 owner_id uuid NOT NULL, kind text NOT NULL, key jsonb NOT NULL, revision bigint NOT NULL CHECK(revision>0),
 sequence bigint NOT NULL, deleted boolean NOT NULL, payload jsonb,
 PRIMARY KEY(owner_id,kind,key,revision),
 FOREIGN KEY(owner_id,sequence) REFERENCES public.noctua_academic_batches(owner_id,sequence),
 CHECK((deleted AND payload IS NULL) OR (NOT deleted AND jsonb_typeof(payload)='object'))
);
CREATE INDEX IF NOT EXISTS noctua_academic_versions_cursor ON public.noctua_academic_versions(owner_id,sequence);
CREATE TABLE IF NOT EXISTS public.noctua_academic_receipts (
 owner_id uuid NOT NULL REFERENCES auth.users(id), operation_id uuid NOT NULL, request jsonb NOT NULL, response jsonb NOT NULL,
 PRIMARY KEY(owner_id,operation_id)
);
-- Owned FK edges mirror the *physical* Room relationships. No invented FK for historical snapshots/slots.
CREATE TABLE IF NOT EXISTS public.noctua_academic_links (
 owner_id uuid NOT NULL, child_kind text NOT NULL, child_key jsonb NOT NULL, role text NOT NULL,
 parent_kind text NOT NULL, parent_key jsonb NOT NULL, live boolean NOT NULL DEFAULT false CHECK(NOT live),
 PRIMARY KEY(owner_id,child_kind,child_key,role),
 FOREIGN KEY(owner_id,child_kind,child_key,live) REFERENCES public.noctua_academic_records(owner_id,kind,key,deleted) DEFERRABLE INITIALLY DEFERRED,
 FOREIGN KEY(owner_id,parent_kind,parent_key,live) REFERENCES public.noctua_academic_records(owner_id,kind,key,deleted) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX IF NOT EXISTS noctua_academic_links_parent ON public.noctua_academic_links(owner_id,parent_kind,parent_key);
CREATE TABLE IF NOT EXISTS public.noctua_academic_uniques (
 owner_id uuid NOT NULL, kind text NOT NULL, fields jsonb NOT NULL, values jsonb NOT NULL,
 record_key jsonb NOT NULL, live boolean NOT NULL DEFAULT false CHECK(NOT live),
 CONSTRAINT noctua_academic_unique_value UNIQUE(owner_id,kind,fields,values) DEFERRABLE INITIALLY DEFERRED,
 FOREIGN KEY(owner_id,kind,record_key,live) REFERENCES public.noctua_academic_records(owner_id,kind,key,deleted) DEFERRABLE INITIALLY DEFERRED
);

DO $$ DECLARE tab text; BEGIN
 FOREACH tab IN ARRAY ARRAY['heads','batches','records','versions','receipts','links','uniques'] LOOP
  EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY','noctua_academic_'||tab);
  EXECUTE format('REVOKE ALL ON public.%I FROM PUBLIC,anon,authenticated','noctua_academic_'||tab);
  EXECUTE format('GRANT SELECT ON public.%I TO authenticated','noctua_academic_'||tab);
  EXECUTE format('DROP POLICY IF EXISTS own_rows ON public.%I','noctua_academic_'||tab);
  EXECUTE format('CREATE POLICY own_rows ON public.%I FOR SELECT TO authenticated USING(owner_id=public.noctua_sync_principal())','noctua_academic_'||tab);
 END LOOP;
END $$;

-- Structural graph checks; the full relocation/history business validator remains a phase D prerequisite.
CREATE OR REPLACE FUNCTION public.noctua_academic_graph(p_owner uuid)
RETURNS void LANGUAGE plpgsql SET search_path=pg_catalog AS $$
DECLARE row record;
BEGIN
 -- Same-owner FKs are validated now, before a positive receipt is assembled.
 SET CONSTRAINTS ALL IMMEDIATE;
 SET CONSTRAINTS ALL DEFERRED;
 FOR row IN SELECT key,payload FROM public.noctua_academic_records WHERE owner_id=p_owner AND kind='classes' AND NOT deleted LOOP
  PERFORM public.noctua_validate_class(row.payload->>'id',jsonb_build_object('school',row.payload,'terms',
   (SELECT coalesce(jsonb_agg(payload ORDER BY (payload->>'number')::integer),'[]'::jsonb)
    FROM public.noctua_academic_records WHERE owner_id=p_owner AND kind='terms' AND NOT deleted AND payload->>'classId'=row.payload->>'id')));
 END LOOP;
 IF EXISTS (
  SELECT 1 FROM public.noctua_academic_records r
  JOIN public.noctua_academic_records s ON s.owner_id=r.owner_id AND s.kind='students' AND NOT s.deleted AND s.key=jsonb_build_object('id',r.payload->>'studentId')
  JOIN public.noctua_academic_records p ON p.owner_id=r.owner_id AND NOT p.deleted AND
   ((r.kind='marks' AND p.kind='lessons' AND p.key=jsonb_build_object('id',r.payload->>'lessonId')) OR
    (r.kind='grades' AND p.kind='activities' AND p.key=jsonb_build_object('id',r.payload->>'activityId')))
  WHERE r.owner_id=p_owner AND NOT r.deleted AND r.kind IN ('marks','grades') AND
   (s.payload->>'classId'<>p.payload->>'classId' OR
    (r.kind='grades' AND (r.payload->>'value')::numeric NOT BETWEEN 0 AND (p.payload->>'maximum')::numeric))
 ) THEN RAISE EXCEPTION 'academic relationship mismatch'; END IF;
 IF EXISTS (
  SELECT 1 FROM public.noctua_academic_records r
  JOIN public.noctua_academic_records s ON s.owner_id=r.owner_id AND s.kind='students' AND NOT s.deleted AND s.key=jsonb_build_object('id',r.payload->>'studentId')
  WHERE r.owner_id=p_owner AND NOT r.deleted AND r.kind='export_rows' AND r.payload->>'classId'<>s.payload->>'classId'
 ) THEN RAISE EXCEPTION 'export enrollment mismatch'; END IF;
END $$;

CREATE OR REPLACE FUNCTION public.noctua_push_academic(p_operation uuid,p_changes jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal(); receipt public.noctua_academic_receipts;
 change jsonb; old public.noctua_academic_records; definition jsonb; ref jsonb; fields jsonb;
 parent jsonb; vals jsonb; field text; index integer; seq bigint; result jsonb; conflicts jsonb:='[]'; current_row jsonb;
BEGIN
 IF principal IS NULL THEN RAISE EXCEPTION 'active confirmed session required' USING ERRCODE='42501'; END IF;
 IF p_operation IS NULL OR jsonb_typeof(p_changes) IS DISTINCT FROM 'array' OR
    jsonb_array_length(p_changes) NOT BETWEEN 1 AND 256 OR octet_length(p_changes::text)>1048576 THEN
  RAISE EXCEPTION 'invalid academic batch' USING ERRCODE='22023'; END IF;
 FOR change IN SELECT * FROM jsonb_array_elements(p_changes) LOOP
  PERFORM public.noctua_academic_shape(change,'{"kind":"string","key":"object","base":"number","payload":"object?"}');
  IF change->>'base' !~ '^[0-9]{1,18}$' THEN RAISE EXCEPTION 'invalid base revision'; END IF;
  PERFORM public.noctua_academic_validate(change->>'kind',change->'key',change->'payload');
 END LOOP;
 IF (SELECT count(*) FROM (SELECT DISTINCT c->>'kind',c->'key' FROM jsonb_array_elements(p_changes) c) keys)<>jsonb_array_length(p_changes) THEN
  RAISE EXCEPTION 'duplicate academic identity'; END IF;
 INSERT INTO public.noctua_academic_heads(owner_id) VALUES(principal) ON CONFLICT DO NOTHING;
 PERFORM 1 FROM public.noctua_academic_heads WHERE owner_id=principal FOR UPDATE;
 SELECT * INTO receipt FROM public.noctua_academic_receipts WHERE owner_id=principal AND operation_id=p_operation;
 IF FOUND THEN
  IF receipt.request IS DISTINCT FROM p_changes THEN RAISE EXCEPTION 'operation identity reused'; END IF;
  RETURN receipt.response;
 END IF;
 -- CAS all records before changing any of them; the failed batch has a stable negative receipt.
 FOR change IN SELECT * FROM jsonb_array_elements(p_changes) LOOP
  SELECT * INTO old FROM public.noctua_academic_records WHERE owner_id=principal AND kind=change->>'kind' AND key=change->'key';
  IF coalesce(old.revision,0)<>(change->>'base')::bigint THEN
   current_row:=CASE WHEN old.revision IS NULL THEN 'null'::jsonb ELSE
    jsonb_build_object('revision',old.revision,'sequence',old.sequence,'payload',old.payload) END;
   conflicts:=conflicts||jsonb_build_array(jsonb_build_object('kind',change->>'kind','key',change->'key','current',current_row));
  END IF;
 END LOOP;
 IF jsonb_array_length(conflicts)>0 THEN
  result:=jsonb_build_object('operation',p_operation,'accepted',false,'conflicts',conflicts,'sequence',NULL);
  INSERT INTO public.noctua_academic_receipts VALUES(principal,p_operation,p_changes,result);
  RETURN result;
 END IF;
 UPDATE public.noctua_academic_heads SET sequence=sequence+1 WHERE owner_id=principal RETURNING sequence INTO seq;
 INSERT INTO public.noctua_academic_batches(owner_id,sequence,operation_id) VALUES(principal,seq,p_operation);
 FOR change IN SELECT * FROM jsonb_array_elements(p_changes) LOOP
  SELECT * INTO old FROM public.noctua_academic_records WHERE owner_id=principal AND kind=change->>'kind' AND key=change->'key';
  IF old.revision IS NOT NULL AND NOT old.deleted AND change->>'kind'='weekly_timetables' AND old.payload IS DISTINCT FROM change->'payload' THEN
   RAISE EXCEPTION 'timetable revisions are immutable'; END IF;
  IF old.revision IS NOT NULL AND NOT old.deleted AND change->>'kind'='relocations' AND change->'payload'<>'null'::jsonb AND
     (jsonb_array_length(change->'payload'->'corrections')<jsonb_array_length(old.payload->'corrections') OR
      EXISTS(SELECT FROM jsonb_array_elements(old.payload->'corrections') WITH ORDINALITY AS audit(value,n)
       WHERE audit.value IS DISTINCT FROM change->'payload'->'corrections'->(audit.n::integer-1))) THEN
   RAISE EXCEPTION 'previous administrative audits are immutable'; END IF;
  -- No cascades: replacement edges and tokens reflect only explicitly changed records.
  DELETE FROM public.noctua_academic_links WHERE owner_id=principal AND child_kind=change->>'kind' AND child_key=change->'key';
  DELETE FROM public.noctua_academic_uniques WHERE owner_id=principal AND kind=change->>'kind' AND record_key=change->'key';
  INSERT INTO public.noctua_academic_records(owner_id,kind,key,revision,sequence,deleted,payload)
   VALUES(principal,change->>'kind',change->'key',(change->>'base')::bigint+1,seq,change->'payload'='null'::jsonb,nullif(change->'payload','null'::jsonb))
   ON CONFLICT(owner_id,kind,key) DO UPDATE SET revision=excluded.revision,sequence=excluded.sequence,deleted=excluded.deleted,payload=excluded.payload;
  INSERT INTO public.noctua_academic_versions VALUES(principal,change->>'kind',change->'key',(change->>'base')::bigint+1,seq,
   change->'payload'='null'::jsonb,nullif(change->'payload','null'::jsonb));
  IF change->'payload'='null'::jsonb THEN CONTINUE; END IF;
  definition:=public.noctua_academic_definition(change->>'kind');
  FOR ref IN SELECT * FROM jsonb_array_elements(definition->'references') LOOP
   parent:='{}'::jsonb;
   FOR index IN 0..jsonb_array_length(ref->'fields')-1 LOOP
    parent:=parent||jsonb_build_object(ref->'key'->>index,change->'payload'->(ref->'fields'->>index));
   END LOOP;
   IF NOT EXISTS(SELECT FROM jsonb_each(parent) WHERE value='null'::jsonb) THEN
    INSERT INTO public.noctua_academic_links(owner_id,child_kind,child_key,role,parent_kind,parent_key)
     VALUES(principal,change->>'kind',change->'key',(ref->'fields')::text,ref->>'kind',parent);
   END IF;
  END LOOP;
  FOR fields IN SELECT * FROM jsonb_array_elements(definition->'unique') LOOP
   SELECT jsonb_agg(change->'payload'->f ORDER BY n) INTO vals FROM jsonb_array_elements_text(fields) WITH ORDINALITY AS k(f,n);
   IF NOT EXISTS(SELECT FROM jsonb_array_elements(vals) v WHERE v='null'::jsonb) THEN
    INSERT INTO public.noctua_academic_uniques(owner_id,kind,fields,values,record_key)
     VALUES(principal,change->>'kind',fields,vals,change->'key');
   END IF;
  END LOOP;
 END LOOP;
 PERFORM public.noctua_academic_graph(principal);
 result:=jsonb_build_object('operation',p_operation,'accepted',true,'sequence',seq,'conflicts','[]'::jsonb);
 INSERT INTO public.noctua_academic_receipts VALUES(principal,p_operation,p_changes,result);
 RETURN result;
END $$;

-- Page boundaries are whole transactions. No receiving cursor can split a relocation/deletion batch.
CREATE OR REPLACE FUNCTION public.noctua_pull_academic(p_after bigint)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal(); result jsonb;
BEGIN
 IF principal IS NULL THEN RAISE EXCEPTION 'active confirmed session required' USING ERRCODE='42501'; END IF;
 IF p_after IS NULL OR p_after<0 THEN RAISE EXCEPTION 'invalid academic cursor'; END IF;
 SELECT coalesce(jsonb_agg(jsonb_build_object('operation',b.operation_id,'sequence',b.sequence,'records',
   (SELECT jsonb_agg(jsonb_build_object('kind',v.kind,'key',v.key,'revision',v.revision,'payload',v.payload) ORDER BY v.kind,v.key)
    FROM public.noctua_academic_versions v WHERE v.owner_id=principal AND v.sequence=b.sequence)) ORDER BY b.sequence),'[]'::jsonb)
 INTO result FROM (SELECT * FROM public.noctua_academic_batches WHERE owner_id=principal AND sequence>p_after ORDER BY sequence LIMIT 1) b;
 RETURN result;
END $$;

REVOKE ALL ON FUNCTION public.noctua_academic_definition(text),public.noctua_academic_shape(jsonb,jsonb),
 public.noctua_academic_key(text,jsonb),public.noctua_academic_validate(text,jsonb,jsonb),public.noctua_academic_graph(uuid)
 FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.noctua_push_academic(uuid,jsonb),public.noctua_pull_academic(bigint) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.noctua_push_academic(uuid,jsonb),public.noctua_pull_academic(bigint) TO authenticated;
COMMENT ON FUNCTION public.noctua_push_academic(uuid,jsonb) IS 'Experimental phase A: row CAS, atomic batch, active owner session; not enabled in Android';

-- SOURCE 202611020001_phase_b_fence.sql SHA256 680bbf805f30b4c15ebb3b8cda1ff286183083e2fe7437e4c8f625ffa4c4031a
-- Phase B, apply AFTER the three earlier migrations. No academic row is deleted.
-- Never reapply the original v1/guard migrations after these fenced entry points.
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

-- SOURCE 202611020002_phase_c_scope.sql SHA256 8d6df95a1c1eb57e8603790fa6f93c8867f00ab3f1e4cf3e4d811c09a1b34d06
-- Phase C: explicit per-owner expansion. Run only after phase A and B; no hosted execution by CI.
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

-- SOURCE 202611020003_phase_d_scope.sql SHA256 7e51af0f29843f02c93a34b1be8c005f96e5d27c70045c2b28fc40d9ba07f3df
-- Phase D. Review and run once AFTER A, B, C; never applied to the hosted project by tests.
ALTER TABLE public.noctua_sync_protocols DROP CONSTRAINT IF EXISTS noctua_sync_protocols_academic_scope_check;
ALTER TABLE public.noctua_sync_protocols ADD CONSTRAINT noctua_sync_protocols_academic_scope_check CHECK(academic_scope IN(2,3,4));

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
 IF seq IS DISTINCT FROM p_expected_sequence THEN RAISE EXCEPTION 'receive changes before expanding scope' USING ERRCODE='40001'; END IF;
 UPDATE public.noctua_sync_protocols SET academic_scope=3 WHERE owner_id=principal;
 RETURN public.noctua_academic_status_c();
END $$;

-- Private projection. Historical school/enrollments never become live rows or acquire artificial FKs.
CREATE OR REPLACE FUNCTION public.noctua_d_projection(p_owner uuid)
RETURNS jsonb LANGUAGE plpgsql STABLE SET search_path=pg_catalog AS $$
DECLARE rows jsonb; h jsonb; kind text; payload jsonb;
BEGIN
 SELECT coalesce(jsonb_agg(jsonb_build_object('kind',r.kind,'key',r.key,'payload',r.payload)),'[]') INTO rows
 FROM public.noctua_academic_records r WHERE r.owner_id=p_owner AND NOT r.deleted;
 FOR h IN SELECT value->'payload' FROM jsonb_array_elements(rows) WHERE value->>'kind'='preserved_histories' LOOP
  payload:=h->'content'->'school';rows:=rows||jsonb_build_array(jsonb_build_object('kind','classes','key',public.noctua_academic_key('classes',payload),'payload',payload));
  FOREACH kind IN ARRAY ARRAY['students','terms','lessons','marks','activities','grades','recompositions','relocations'] LOOP
   FOR payload IN SELECT * FROM jsonb_array_elements(h->'content'->kind) LOOP
    rows:=rows||jsonb_build_array(jsonb_build_object('kind',kind,'key',public.noctua_academic_key(kind,payload),'payload',payload));
   END LOOP;
  END LOOP;
 END LOOP;
 RETURN rows;
END $$;
CREATE OR REPLACE FUNCTION public.noctua_d_get(p_rows jsonb,p_kind text,p_key jsonb)
RETURNS jsonb LANGUAGE sql IMMUTABLE SET search_path=pg_catalog AS $$
 SELECT value->'payload' FROM jsonb_array_elements(p_rows) WHERE value->>'kind'=p_kind AND value->'key'=p_key;
$$;
-- Same NFD/marks/case/alphanumeric normalization as RosterParser for Portuguese subjects.
CREATE OR REPLACE FUNCTION public.noctua_d_subject(p_value text)
RETURNS text LANGUAGE sql IMMUTABLE SET search_path=pg_catalog AS $$
 SELECT regexp_replace(lower(regexp_replace(normalize(p_value,NFD),U&'[\0300-\036f]','','g')),'[^[:alnum:]]','','g');
$$;
CREATE OR REPLACE FUNCTION public.noctua_d_graph(p_owner uuid)
RETURNS void LANGUAGE plpgsql SET search_path=pg_catalog AS $$
DECLARE rows jsonb:=public.noctua_d_projection(p_owner); r jsonb; p jsonb; ref jsonb; parent jsonb; k jsonb;
 h jsonb; s jsonb; t jsonb; a jsonb; b jsonb; link jsonb; audit jsonb; previous jsonb; removed jsonb; x jsonb; y jsonb;
 f text; n integer; step integer; sid text; seen text[]; classes text[]; ids text[]; term integer;
BEGIN
 IF EXISTS(SELECT FROM jsonb_array_elements(rows) GROUP BY value->>'kind',value->'key' HAVING count(*)>1) THEN
  RAISE EXCEPTION 'duplicate historical or live identity'; END IF;
 FOR r IN SELECT * FROM jsonb_array_elements(rows) LOOP
  p:=r->'payload';PERFORM public.noctua_academic_validate(r->>'kind',r->'key',p);
  FOR ref IN SELECT * FROM jsonb_array_elements(public.noctua_academic_definition(r->>'kind')->'references') LOOP
   k:='{}';FOR n IN 0..jsonb_array_length(ref->'fields')-1 LOOP k:=k||jsonb_build_object(ref->'key'->>n,p->(ref->'fields'->>n)); END LOOP;
   IF NOT EXISTS(SELECT FROM jsonb_each(k) WHERE value='null') AND public.noctua_d_get(rows,ref->>'kind',k) IS NULL THEN
    RAISE EXCEPTION 'missing academic relationship'; END IF;
  END LOOP;
  IF r->>'kind'='classes' THEN
   PERFORM public.noctua_validate_class(p->>'id',jsonb_build_object('school',p,'terms',
    (SELECT coalesce(jsonb_agg(value->'payload' ORDER BY (value->'payload'->>'number')::integer),'[]') FROM jsonb_array_elements(rows)
     WHERE value->>'kind'='terms' AND value->'payload'->>'classId'=p->>'id')));
  ELSIF r->>'kind'='students' THEN
   IF p->>'status'<>'Ativo' AND p->'left'='null' THEN RAISE EXCEPTION 'inactive enrollment without exit'; END IF;
   IF p->>'status'='Remanejado' AND public.noctua_d_get(rows,'relocations',jsonb_build_object('sourceStudentId',p->>'id')) IS NULL THEN
    RAISE EXCEPTION 'moved enrollment without chain'; END IF;
  ELSIF r->>'kind' IN('marks','grades') THEN
   s:=public.noctua_d_get(rows,'students',jsonb_build_object('id',p->>'studentId'));
   parent:=CASE WHEN r->>'kind'='marks' THEN public.noctua_d_get(rows,'lessons',jsonb_build_object('id',p->>'lessonId'))
    ELSE public.noctua_d_get(rows,'activities',jsonb_build_object('id',p->>'activityId')) END;
   IF s->>'classId' IS DISTINCT FROM parent->>'classId' OR
    (r->>'kind'='grades' AND p->'value'<>'null' AND (p->>'value')::numeric NOT BETWEEN 0 AND (parent->>'maximum')::numeric) THEN
    RAISE EXCEPTION 'academic relationship mismatch'; END IF;
  ELSIF r->>'kind'='recompositions' THEN
   s:=public.noctua_d_get(rows,'students',jsonb_build_object('id',p->>'studentId'));
   IF public.noctua_d_get(rows,'terms',jsonb_build_object('classId',s->>'classId','number',(p->>'term')::integer)) IS NULL THEN
    RAISE EXCEPTION 'recomposition without term'; END IF;
  ELSIF r->>'kind'='lesson_records' THEN
   IF btrim(p->>'title')='' AND btrim(p->>'description')='' THEN RAISE EXCEPTION 'empty journal'; END IF;
  ELSIF r->>'kind'='export_rows' THEN
   IF (p->>'position')::integer<0 THEN RAISE EXCEPTION 'invalid layout position'; END IF;
   IF p->'studentId'='null' THEN
    IF left(p->>'id',6)<>'blank:' OR length(p->>'id')<=6 OR (p->>'manualHidden')::boolean THEN RAISE EXCEPTION 'invalid blank row'; END IF;
   ELSIF p->>'id'<>'student:'||(p->>'studentId') THEN RAISE EXCEPTION 'layout student identity mismatch'; END IF;
  ELSIF r->>'kind'='weekly_timetables' THEN
   IF p->>'createdAt' !~ 'Z$' THEN RAISE EXCEPTION 'invalid revision instant'; END IF;
   PERFORM (p->>'createdAt')::timestamptz;
   FOR x IN SELECT * FROM jsonb_array_elements(p->'slots') LOOP
    FOREACH f IN ARRAY ARRAY['id','classId','className','subject','shift'] LOOP
     IF btrim(x->>f)='' THEN RAISE EXCEPTION 'empty timetable slot'; END IF;
    END LOOP;
    a:=public.noctua_d_get(rows,'classes',jsonb_build_object('id',x->>'classId'));
    IF a IS NOT NULL AND a->'year' IS DISTINCT FROM p->'year' THEN RAISE EXCEPTION 'slot year mismatch'; END IF;
   END LOOP;
   IF EXISTS(SELECT FROM jsonb_array_elements(p->'slots') GROUP BY value->>'id' HAVING count(*)>1) OR
    EXISTS(SELECT FROM jsonb_array_elements(p->'slots') GROUP BY value->>'weekday',value->>'ordinal',lower(btrim(value->>'shift')) HAVING count(*)>1) OR
    EXISTS(SELECT FROM jsonb_array_elements(p->'slots') GROUP BY value->>'classId',value->>'weekday',value->>'ordinal' HAVING count(*)>1) THEN
    RAISE EXCEPTION 'conflicting timetable slots'; END IF;
  END IF;
 END LOOP;
 IF EXISTS(SELECT FROM jsonb_array_elements(rows) WHERE value->>'kind'='lessons'
  GROUP BY value->'payload'->>'classId',value->'payload'->>'date',value->'payload'->>'ordinal' HAVING count(*)>1) THEN
  RAISE EXCEPTION 'duplicate historical or live lesson period'; END IF;
 IF EXISTS(SELECT FROM jsonb_array_elements(rows) WHERE value->>'kind'='students'
  GROUP BY value->'payload'->>'classId',value->'payload'->>'number' HAVING count(*)>1) THEN RAISE EXCEPTION 'duplicate roster number'; END IF;
 IF EXISTS(SELECT FROM jsonb_array_elements(rows) r CROSS JOIN LATERAL jsonb_array_elements(r.value->'payload'->'slots') s
   WHERE r.value->>'kind'='weekly_timetables' GROUP BY s.value->>'id'
   HAVING count(DISTINCT jsonb_build_array(s.value->>'classId',s.value->>'weekday',s.value->>'ordinal'))>1) THEN
   RAISE EXCEPTION 'slot identity changed across revisions'; END IF;
 IF EXISTS(SELECT FROM jsonb_array_elements(rows) WHERE value->>'kind'='export_rows' GROUP BY value->'payload'->>'classId'
  HAVING min((value->'payload'->>'position')::integer)<>0 OR max((value->'payload'->>'position')::integer)<>count(*)-1) THEN
  RAISE EXCEPTION 'non contiguous export layout'; END IF;
 -- Internal/external movement and append-only administrative sequence, including historical fragments.
 IF EXISTS(SELECT FROM jsonb_array_elements(rows) WHERE value->>'kind'='relocations' AND value->'payload'->'targetStudentId'<>'null'
  GROUP BY value->'payload'->>'targetStudentId' HAVING count(*)>1) THEN RAISE EXCEPTION 'duplicate relocation target'; END IF;
 FOR link IN SELECT value->'payload' FROM jsonb_array_elements(rows) WHERE value->>'kind'='relocations' LOOP
  s:=public.noctua_d_get(rows,'students',jsonb_build_object('id',link->>'sourceStudentId'));
  a:=public.noctua_d_get(rows,'classes',jsonb_build_object('id',s->>'classId'));
  IF link->>'effectiveDate'<=s->>'joined' OR (link->>'effectiveDate')::date-1 IS DISTINCT FROM (s->>'left')::date OR
   s->>'status'<>'Remanejado' OR extract(year FROM (link->>'effectiveDate')::date)<>(a->>'year')::integer THEN
   RAISE EXCEPTION 'invalid source exit'; END IF;
  t:=NULL;
  IF link->'targetStudentId'='null' THEN
   IF link->'externalTarget'='null' OR btrim(link->>'externalTarget')='' THEN RAISE EXCEPTION 'missing external target'; END IF;
  ELSE
   t:=public.noctua_d_get(rows,'students',jsonb_build_object('id',link->>'targetStudentId'));
   b:=public.noctua_d_get(rows,'classes',jsonb_build_object('id',t->>'classId'));
   IF link->'externalTarget'<>'null' OR s->'personId' IS DISTINCT FROM t->'personId' OR t->'joined' IS DISTINCT FROM link->'effectiveDate' OR
    a->>'id'=b->>'id' OR a->'year' IS DISTINCT FROM b->'year' OR a->'stage' IS DISTINCT FROM b->'stage' OR
    public.noctua_d_subject(a->>'subject') IS DISTINCT FROM public.noctua_d_subject(b->>'subject') OR a->'scale' IS DISTINCT FROM b->'scale' OR
    a->'countJustified' IS DISTINCT FROM b->'countJustified' THEN RAISE EXCEPTION 'incompatible internal movement'; END IF;
   FOR term IN 1..4 LOOP
    x:=public.noctua_d_get(rows,'terms',jsonb_build_object('classId',a->>'id','number',term));
    y:=public.noctua_d_get(rows,'terms',jsonb_build_object('classId',b->>'id','number',term));
    IF x->'start' IS DISTINCT FROM y->'start' OR x->'end' IS DISTINCT FROM y->'end' THEN RAISE EXCEPTION 'incompatible calendars'; END IF;
   END LOOP;
  END IF;
  previous:=NULL;
  IF EXISTS(SELECT FROM jsonb_array_elements(link->'corrections') GROUP BY value->>'id' HAVING count(*)>1) THEN RAISE EXCEPTION 'duplicate correction'; END IF;
  FOR audit,step IN SELECT value,ordinality::integer FROM jsonb_array_elements(link->'corrections') WITH ORDINALITY LOOP
   IF btrim(audit->>'id')='' OR btrim(audit->>'previousTarget')='' OR audit->>'confirmedAt' !~ 'Z$' THEN RAISE EXCEPTION 'invalid correction'; END IF;
   PERFORM (audit->>'confirmedAt')::timestamptz;
   FOREACH f IN ARRAY ARRAY['previousDate','effectiveDate'] LOOP
    IF audit->>f !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' OR (audit->>f)::date::text IS DISTINCT FROM audit->>f OR
     extract(year FROM (audit->>f)::date)<>(a->>'year')::integer OR audit->>f<=s->>'joined' THEN RAISE EXCEPTION 'invalid correction date'; END IF;
   END LOOP;
   IF previous IS NOT NULL AND (audit->'previousDate' IS DISTINCT FROM previous->'effectiveDate' OR audit->'previousTarget' IS DISTINCT FROM previous->'externalTarget') THEN
    RAISE EXCEPTION 'broken correction sequence'; END IF;
   IF audit->>'operation'='CONVERSAO_INTERNA' THEN
    IF t IS NULL OR step<>jsonb_array_length(link->'corrections') OR audit->'externalTarget'<>'null' OR
     audit->'sourceStudentId' IS DISTINCT FROM s->'id' OR audit->'targetStudentId' IS DISTINCT FROM t->'id' OR
     audit->'targetClassId' IS DISTINCT FROM t->'classId' OR coalesce(btrim(audit->>'targetLabel'),'')='' THEN RAISE EXCEPTION 'conversion audit mismatch'; END IF;
   ELSIF audit->>'operation'='CORRECAO_EXTERNA' THEN
    IF audit->'externalTarget'='null' OR length(audit->>'externalTarget') NOT BETWEEN 2 AND 160 OR
     audit->'sourceStudentId'<>'null' OR audit->'targetStudentId'<>'null' OR audit->'targetClassId'<>'null' OR audit->'targetLabel'<>'null' THEN RAISE EXCEPTION 'external audit mismatch'; END IF;
   ELSE RAISE EXCEPTION 'unsupported correction'; END IF;
   IF EXISTS(SELECT FROM jsonb_array_elements(audit->'removed') GROUP BY value->'attendance'->>'lessonId' HAVING count(*)>1) THEN RAISE EXCEPTION 'duplicate removed occurrence'; END IF;
   FOR removed IN SELECT * FROM jsonb_array_elements(audit->'removed') LOOP
    PERFORM public.noctua_academic_shape(removed,'{"lesson":"object","attendance":"object"}');x:=removed->'lesson';y:=removed->'attendance';
    PERFORM public.noctua_academic_validate('lessons',public.noctua_academic_key('lessons',x),x);
    PERFORM public.noctua_academic_validate('marks',public.noctua_academic_key('marks',y),y);
    IF y->'studentId' IS DISTINCT FROM s->'id' OR y->'lessonId' IS DISTINCT FROM x->'id' OR x->'classId' IS DISTINCT FROM s->'classId' OR
     x->>'date'<audit->>'effectiveDate' THEN RAISE EXCEPTION 'removed occurrence mismatch'; END IF;
   END LOOP;
   previous:=audit;
  END LOOP;
  IF previous IS NOT NULL AND (previous->'effectiveDate' IS DISTINCT FROM link->'effectiveDate' OR previous->'externalTarget' IS DISTINCT FROM link->'externalTarget' OR
   ((previous->>'operation'='CONVERSAO_INTERNA') IS DISTINCT FROM (link->'targetStudentId'<>'null'))) THEN RAISE EXCEPTION 'current target diverges from audit'; END IF;
 END LOOP;
 -- Walk each lineage (never infer by personId alone); detect repeated classes and recomp conflicts.
 FOR s IN SELECT value->'payload' FROM jsonb_array_elements(rows) WHERE value->>'kind'='students' LOOP
  ids:=ARRAY[]::text[];classes:=ARRAY[]::text[];sid:=s->>'id';
  LOOP
   t:=public.noctua_d_get(rows,'students',jsonb_build_object('id',sid));
   IF sid=ANY(ids) OR t->>'classId'=ANY(classes) OR t->'personId' IS DISTINCT FROM s->'personId' THEN RAISE EXCEPTION 'cyclic or incompatible lineage'; END IF;
   ids:=array_append(ids,sid);classes:=array_append(classes,t->>'classId');
   SELECT value->'payload'->>'sourceStudentId' INTO sid FROM jsonb_array_elements(rows)
    WHERE value->>'kind'='relocations' AND value->'payload'->>'targetStudentId'=sid;
   EXIT WHEN sid IS NULL;
  END LOOP;
  IF EXISTS(SELECT FROM jsonb_array_elements(rows) WHERE value->>'kind'='recompositions' AND value->'payload'->>'studentId'=ANY(ids)
   GROUP BY value->'payload'->>'term' HAVING count(*)>1) THEN RAISE EXCEPTION 'duplicate lineage recomposition'; END IF;
 END LOOP;
 -- Historical storage is self-contained, scoped to its class, and connected to a live enrollment.
 FOR h IN SELECT payload FROM public.noctua_academic_records WHERE owner_id=p_owner AND kind='preserved_histories' AND NOT deleted LOOP
  IF h->'classId' IS DISTINCT FROM h->'content'->'school'->'id' OR jsonb_array_length(h->'content'->'students')=0 THEN RAISE EXCEPTION 'invalid preserved origin'; END IF;
  FOR f IN SELECT unnest(ARRAY['students','terms','lessons','activities']) LOOP
   IF EXISTS(SELECT FROM jsonb_array_elements(h->'content'->f) WHERE value->'classId' IS DISTINCT FROM h->'classId') THEN RAISE EXCEPTION 'foreign historical class'; END IF;
  END LOOP;
  SELECT array_agg(value->>'id') INTO ids FROM jsonb_array_elements(h->'content'->'students');
  FOREACH f IN ARRAY ARRAY['marks','grades','recompositions'] LOOP
   IF EXISTS(SELECT FROM jsonb_array_elements(h->'content'->f) WHERE NOT(value->>'studentId'=ANY(ids))) THEN RAISE EXCEPTION 'foreign historical enrollment'; END IF;
  END LOOP;
  IF EXISTS(SELECT FROM jsonb_array_elements(h->'content'->'relocations') WHERE NOT(value->>'sourceStudentId'=ANY(ids) OR coalesce(value->>'targetStudentId'=ANY(ids),false))) THEN RAISE EXCEPTION 'foreign historical link'; END IF;
  FOR sid IN SELECT unnest(ids) LOOP
   IF NOT EXISTS(
    WITH RECURSIVE connected(id) AS (
     SELECT payload->>'id' FROM public.noctua_academic_records WHERE owner_id=p_owner AND kind='students' AND NOT deleted
     UNION
     SELECT CASE WHEN r.value->'payload'->>'sourceStudentId'=c.id THEN r.value->'payload'->>'targetStudentId' ELSE r.value->'payload'->>'sourceStudentId' END
     FROM connected c JOIN jsonb_array_elements(rows) r ON r.value->>'kind'='relocations' AND r.value->'payload'->'targetStudentId'<>'null' AND
      (r.value->'payload'->>'sourceStudentId'=c.id OR r.value->'payload'->>'targetStudentId'=c.id)
    ) SELECT FROM connected WHERE id=sid
   ) THEN RAISE EXCEPTION 'orphan preserved history'; END IF;
  END LOOP;
 END LOOP;
END $$;

-- Guard immutable fragments even when links move from physical rows into a history payload.
CREATE OR REPLACE FUNCTION public.noctua_d_history_guard(p_before jsonb,p_after jsonb)
RETURNS void LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog AS $$
DECLARE h jsonb; next jsonb; kind text; r jsonb; k jsonb; current jsonb;
BEGIN
 FOR h IN SELECT value->'payload' FROM jsonb_array_elements(p_before) WHERE value->>'kind'='preserved_histories' LOOP
  next:=public.noctua_d_get(p_after,'preserved_histories',jsonb_build_object('classId',h->>'classId'));
  IF next IS NOT NULL AND (next->'content'->'school' IS DISTINCT FROM h->'content'->'school' OR
   next->'content'->'terms' IS DISTINCT FROM h->'content'->'terms') THEN RAISE EXCEPTION 'historical class context is immutable'; END IF;
  FOREACH kind IN ARRAY ARRAY['students','lessons','activities','marks','grades','recompositions'] LOOP
   IF next IS NOT NULL AND NOT((next->'content'->kind) <@ (h->'content'->kind)) THEN RAISE EXCEPTION 'preserved fragment cannot be rewritten'; END IF;
   FOR r IN SELECT * FROM jsonb_array_elements(h->'content'->kind) LOOP
    k:=public.noctua_academic_key(kind,r);current:=public.noctua_d_get(p_after,kind,k);
    IF current IS NOT NULL AND current IS DISTINCT FROM r THEN RAISE EXCEPTION 'preserved academic record is immutable'; END IF;
    IF kind IN('marks','grades','recompositions') AND current IS NULL AND public.noctua_d_get(p_after,'students',jsonb_build_object('id',r->>'studentId')) IS NOT NULL THEN
     RAISE EXCEPTION 'historical enrollment still needs its academic record'; END IF;
    IF kind IN('lessons','activities') AND current IS NULL AND EXISTS(SELECT FROM jsonb_array_elements(p_after) WHERE value->>'kind'='students' AND
     value->'payload'->>'classId'=r->>'classId' AND value->'payload'->>'joined'<=r->>'date' AND
     (value->'payload'->'left'='null' OR value->'payload'->>'left'>=r->>'date')) THEN RAISE EXCEPTION 'historical enrollment still needs its lesson or activity'; END IF;
    IF kind='students' AND current IS NOT NULL AND next IS NULL THEN RAISE EXCEPTION 'historical enrollment cannot become live'; END IF;
   END LOOP;
  END LOOP;
 END LOOP;
 FOR r IN SELECT * FROM jsonb_array_elements(p_before) WHERE value->>'kind' IN('students','classes','activities','lessons') LOOP
  current:=public.noctua_d_get(p_after,r->>'kind',r->'key');
  IF current IS NULL THEN CONTINUE; END IF;
  IF (r->>'kind'='classes' AND r->'payload'->'year' IS DISTINCT FROM current->'year') OR
   (r->>'kind'<>'classes' AND r->'payload'->'classId' IS DISTINCT FROM current->'classId') OR
   (r->>'kind'='students' AND r->'payload'->'personId' IS DISTINCT FROM current->'personId') THEN RAISE EXCEPTION 'historical identity changed'; END IF;
 END LOOP;
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
 IF seq IS DISTINCT FROM p_expected_sequence THEN RAISE EXCEPTION 'receive changes before expanding scope' USING ERRCODE='40001'; END IF;
 UPDATE public.noctua_sync_protocols SET academic_scope=4 WHERE owner_id=principal;
 RETURN public.noctua_academic_status_c();
END $$;
CREATE OR REPLACE FUNCTION public.noctua_push_academic_d(p_operation uuid,p_changes jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal(); change jsonb; previous jsonb; result jsonb; before_rows jsonb; after_rows jsonb; r jsonb; replacement jsonb; n integer;
BEGIN
 IF principal IS NULL OR NOT EXISTS(SELECT FROM public.noctua_academic_test_accounts WHERE owner_id=principal AND allowed) THEN RAISE EXCEPTION 'authorized test account required' USING ERRCODE='42501'; END IF;
 PERFORM 1 FROM public.noctua_sync_heads WHERE owner_id=principal FOR UPDATE;
 IF NOT EXISTS(SELECT FROM public.noctua_sync_protocols WHERE owner_id=principal AND academic_scope=4) THEN RAISE EXCEPTION 'phase D client required' USING ERRCODE='42501'; END IF;
 IF jsonb_typeof(p_changes) IS DISTINCT FROM 'array' THEN RAISE EXCEPTION 'invalid changes'; END IF;
 before_rows:=public.noctua_d_projection(principal);
 FOR change IN SELECT * FROM jsonb_array_elements(p_changes) LOOP
  PERFORM public.noctua_academic_validate(change->>'kind',change->'key',change->'payload');
  IF change->'payload'='null' THEN CONTINUE; END IF;
  SELECT payload INTO previous FROM public.noctua_academic_versions WHERE owner_id=principal AND kind=change->>'kind' AND key=change->'key' AND NOT deleted ORDER BY revision DESC LIMIT 1;
  IF previous IS NOT NULL AND ((change->>'kind'='classes' AND previous->'year' IS DISTINCT FROM change->'payload'->'year') OR
   (change->>'kind' IN('students','activities','lessons','lesson_records','import_drafts','export_rows') AND previous->'classId' IS DISTINCT FROM change->'payload'->'classId') OR
   (change->>'kind'='students' AND previous->'personId' IS DISTINCT FROM change->'payload'->'personId')) THEN RAISE EXCEPTION 'academic identity is immutable'; END IF;
 END LOOP;
 result:=public.noctua_v2_push_unfenced(p_operation,p_changes);
 IF NOT(result->>'accepted')::boolean THEN RETURN result; END IF;
 PERFORM public.noctua_d_graph(principal);
 after_rows:=public.noctua_d_projection(principal);
 PERFORM public.noctua_d_history_guard(before_rows,after_rows);
 FOR r IN SELECT value->'payload' FROM jsonb_array_elements(before_rows) WHERE value->>'kind'='relocations' LOOP
  replacement:=public.noctua_d_get(after_rows,'relocations',jsonb_build_object('sourceStudentId',r->>'sourceStudentId'));
  IF replacement IS NULL THEN
   IF EXISTS(SELECT FROM jsonb_array_elements(after_rows) WHERE value->>'kind'='students' AND value->'payload'->>'id' IN(r->>'sourceStudentId',r->>'targetStudentId')) THEN RAISE EXCEPTION 'movement chain cannot be removed'; END IF;
  ELSE
   IF jsonb_array_length(replacement->'corrections')<jsonb_array_length(r->'corrections') THEN RAISE EXCEPTION 'audit cannot be removed'; END IF;
   FOR n IN 0..jsonb_array_length(r->'corrections')-1 LOOP
    IF replacement->'corrections'->n IS DISTINCT FROM r->'corrections'->n THEN RAISE EXCEPTION 'audit cannot be rewritten'; END IF;
   END LOOP;
   IF r->'targetStudentId'<>'null' AND replacement IS DISTINCT FROM r THEN RAISE EXCEPTION 'confirmed internal link is immutable'; END IF;
   IF replacement IS DISTINCT FROM r THEN
    n:=jsonb_array_length(r->'corrections');
    IF jsonb_array_length(replacement->'corrections')<=n OR replacement->'corrections'->n->'previousDate' IS DISTINCT FROM r->'effectiveDate' OR
     replacement->'corrections'->n->'previousTarget' IS DISTINCT FROM r->'externalTarget' THEN RAISE EXCEPTION 'movement change requires an appended audit'; END IF;
   END IF;
  END IF;
 END LOOP;
 RETURN result;
END $$;
CREATE OR REPLACE FUNCTION public.noctua_pull_academic_d(p_after bigint)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal();
BEGIN
 IF principal IS NULL OR NOT EXISTS(SELECT FROM public.noctua_academic_test_accounts WHERE owner_id=principal AND allowed) OR
  NOT EXISTS(SELECT FROM public.noctua_sync_protocols WHERE owner_id=principal AND academic_scope=4) THEN RAISE EXCEPTION 'authorized phase D client required' USING ERRCODE='42501'; END IF;
 RETURN public.noctua_v2_pull_unfenced(p_after);
END $$;
REVOKE ALL ON FUNCTION public.noctua_d_history_guard(jsonb,jsonb),public.noctua_d_projection(uuid),public.noctua_d_get(jsonb,text,jsonb),public.noctua_d_subject(text),public.noctua_d_graph(uuid),
 public.noctua_enable_academic_d(bigint),public.noctua_push_academic_d(uuid,jsonb),public.noctua_pull_academic_d(bigint) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.noctua_enable_academic_d(bigint),public.noctua_push_academic_d(uuid,jsonb),public.noctua_pull_academic_d(bigint) TO authenticated;

-- SOURCE 202611030001_phase_e_paging.sql SHA256 0eb5a4bad62743a28c1cd42e28e70711fd197a1b317804cbcd64e7beb1eb9813
-- E1: persistent bounded transfers. Apply after A/B/C/D, never automatically hosted.
ALTER TABLE public.noctua_sync_protocols DROP CONSTRAINT IF EXISTS noctua_sync_protocols_academic_scope_check;
ALTER TABLE public.noctua_sync_protocols ADD CONSTRAINT noctua_sync_protocols_academic_scope_check CHECK(academic_scope IN(2,3,4,5));
CREATE TABLE IF NOT EXISTS public.noctua_e_transfers (
 owner_id uuid NOT NULL REFERENCES auth.users(id), operation_id uuid NOT NULL,
 total integer NOT NULL CHECK(total BETWEEN 1 AND 16384), parts integer NOT NULL CHECK(parts BETWEEN 1 AND 16384 AND parts<=total),
 created_at timestamptz NOT NULL DEFAULT statement_timestamp(), PRIMARY KEY(owner_id,operation_id)
);
CREATE TABLE IF NOT EXISTS public.noctua_e_parts (
 owner_id uuid NOT NULL, operation_id uuid NOT NULL, part integer NOT NULL CHECK(part>=0),
 changes jsonb NOT NULL CHECK(jsonb_typeof(changes)='array' AND jsonb_array_length(changes) BETWEEN 1 AND 128 AND octet_length(changes::text)<=786432),
 PRIMARY KEY(owner_id,operation_id,part), FOREIGN KEY(owner_id,operation_id) REFERENCES public.noctua_e_transfers(owner_id,operation_id)
);
ALTER TABLE public.noctua_e_transfers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.noctua_e_parts ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.noctua_e_transfers,public.noctua_e_parts FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.noctua_e_transfers,public.noctua_e_parts TO authenticated;
DROP POLICY IF EXISTS own_rows ON public.noctua_e_transfers;
CREATE POLICY own_rows ON public.noctua_e_transfers FOR SELECT TO authenticated USING(owner_id=public.noctua_sync_principal());
DROP POLICY IF EXISTS own_rows ON public.noctua_e_parts;
CREATE POLICY own_rows ON public.noctua_e_parts FOR SELECT TO authenticated USING(owner_id=public.noctua_sync_principal());

CREATE OR REPLACE FUNCTION public.noctua_e_principal()
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal();
BEGIN
 IF principal IS NULL OR NOT EXISTS(SELECT FROM public.noctua_academic_test_accounts WHERE owner_id=principal AND allowed) OR
  NOT EXISTS(SELECT FROM public.noctua_sync_protocols WHERE owner_id=principal AND academic_scope=5)
 THEN RAISE EXCEPTION 'authorized phase E account required' USING ERRCODE='42501'; END IF;
 RETURN principal;
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
 IF seq IS DISTINCT FROM p_expected_sequence THEN RAISE EXCEPTION 'receive changes before expanding scope' USING ERRCODE='40001'; END IF;
 UPDATE public.noctua_sync_protocols SET academic_scope=5 WHERE owner_id=principal;
 RETURN public.noctua_academic_status_c();
END $$;

-- Existing row-CAS and D graph/audit guards, with bounded E transaction capacity.
CREATE OR REPLACE FUNCTION public.noctua_e_write(p_operation uuid,p_changes jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal(); receipt public.noctua_academic_receipts;
 change jsonb; old public.noctua_academic_records; definition jsonb; ref jsonb; fields jsonb;
 parent jsonb; vals jsonb; field text; index integer; seq bigint; result jsonb; conflicts jsonb:='[]'; current_row jsonb;
BEGIN
 IF principal IS NULL THEN RAISE EXCEPTION 'active confirmed session required' USING ERRCODE='42501'; END IF;
 IF p_operation IS NULL OR jsonb_typeof(p_changes) IS DISTINCT FROM 'array' OR
    jsonb_array_length(p_changes) NOT BETWEEN 1 AND 16384 OR octet_length(p_changes::text)>16777216 THEN
  RAISE EXCEPTION 'invalid academic batch' USING ERRCODE='22023'; END IF;
 FOR change IN SELECT * FROM jsonb_array_elements(p_changes) LOOP
  PERFORM public.noctua_academic_shape(change,'{"kind":"string","key":"object","base":"number","payload":"object?"}');
  IF change->>'base' !~ '^[0-9]{1,18}$' THEN RAISE EXCEPTION 'invalid base revision'; END IF;
  PERFORM public.noctua_academic_validate(change->>'kind',change->'key',change->'payload');
 END LOOP;
 IF (SELECT count(*) FROM (SELECT DISTINCT c->>'kind',c->'key' FROM jsonb_array_elements(p_changes) c) keys)<>jsonb_array_length(p_changes) THEN
  RAISE EXCEPTION 'duplicate academic identity'; END IF;
 INSERT INTO public.noctua_academic_heads(owner_id) VALUES(principal) ON CONFLICT DO NOTHING;
 PERFORM 1 FROM public.noctua_academic_heads WHERE owner_id=principal FOR UPDATE;
 SELECT * INTO receipt FROM public.noctua_academic_receipts WHERE owner_id=principal AND operation_id=p_operation;
 IF FOUND THEN
  IF receipt.request IS DISTINCT FROM p_changes THEN RAISE EXCEPTION 'operation identity reused'; END IF;
  RETURN receipt.response;
 END IF;
 -- CAS all records before changing any of them; the failed batch has a stable negative receipt.
 FOR change IN SELECT * FROM jsonb_array_elements(p_changes) LOOP
  SELECT * INTO old FROM public.noctua_academic_records WHERE owner_id=principal AND kind=change->>'kind' AND key=change->'key';
  IF coalesce(old.revision,0)<>(change->>'base')::bigint THEN
   current_row:=CASE WHEN old.revision IS NULL THEN 'null'::jsonb ELSE
    jsonb_build_object('revision',old.revision,'sequence',old.sequence,'payload',old.payload) END;
   conflicts:=conflicts||jsonb_build_array(jsonb_build_object('kind',change->>'kind','key',change->'key','current',current_row));
  END IF;
 END LOOP;
 IF jsonb_array_length(conflicts)>0 THEN
  result:=jsonb_build_object('operation',p_operation,'accepted',false,'conflicts',conflicts,'sequence',NULL);
  INSERT INTO public.noctua_academic_receipts VALUES(principal,p_operation,p_changes,result);
  RETURN result;
 END IF;
 UPDATE public.noctua_academic_heads SET sequence=sequence+1 WHERE owner_id=principal RETURNING sequence INTO seq;
 INSERT INTO public.noctua_academic_batches(owner_id,sequence,operation_id) VALUES(principal,seq,p_operation);
 FOR change IN SELECT * FROM jsonb_array_elements(p_changes) LOOP
  SELECT * INTO old FROM public.noctua_academic_records WHERE owner_id=principal AND kind=change->>'kind' AND key=change->'key';
  IF old.revision IS NOT NULL AND NOT old.deleted AND change->>'kind'='weekly_timetables' AND old.payload IS DISTINCT FROM change->'payload' THEN
   RAISE EXCEPTION 'timetable revisions are immutable'; END IF;
  IF old.revision IS NOT NULL AND NOT old.deleted AND change->>'kind'='relocations' AND change->'payload'<>'null'::jsonb AND
     (jsonb_array_length(change->'payload'->'corrections')<jsonb_array_length(old.payload->'corrections') OR
      EXISTS(SELECT FROM jsonb_array_elements(old.payload->'corrections') WITH ORDINALITY AS audit(value,n)
       WHERE audit.value IS DISTINCT FROM change->'payload'->'corrections'->(audit.n::integer-1))) THEN
   RAISE EXCEPTION 'previous administrative audits are immutable'; END IF;
  -- No cascades: replacement edges and tokens reflect only explicitly changed records.
  DELETE FROM public.noctua_academic_links WHERE owner_id=principal AND child_kind=change->>'kind' AND child_key=change->'key';
  DELETE FROM public.noctua_academic_uniques WHERE owner_id=principal AND kind=change->>'kind' AND record_key=change->'key';
  INSERT INTO public.noctua_academic_records(owner_id,kind,key,revision,sequence,deleted,payload)
   VALUES(principal,change->>'kind',change->'key',(change->>'base')::bigint+1,seq,change->'payload'='null'::jsonb,nullif(change->'payload','null'::jsonb))
   ON CONFLICT(owner_id,kind,key) DO UPDATE SET revision=excluded.revision,sequence=excluded.sequence,deleted=excluded.deleted,payload=excluded.payload;
  INSERT INTO public.noctua_academic_versions VALUES(principal,change->>'kind',change->'key',(change->>'base')::bigint+1,seq,
   change->'payload'='null'::jsonb,nullif(change->'payload','null'::jsonb));
  IF change->'payload'='null'::jsonb THEN CONTINUE; END IF;
  definition:=public.noctua_academic_definition(change->>'kind');
  FOR ref IN SELECT * FROM jsonb_array_elements(definition->'references') LOOP
   parent:='{}'::jsonb;
   FOR index IN 0..jsonb_array_length(ref->'fields')-1 LOOP
    parent:=parent||jsonb_build_object(ref->'key'->>index,change->'payload'->(ref->'fields'->>index));
   END LOOP;
   IF NOT EXISTS(SELECT FROM jsonb_each(parent) WHERE value='null'::jsonb) THEN
    INSERT INTO public.noctua_academic_links(owner_id,child_kind,child_key,role,parent_kind,parent_key)
     VALUES(principal,change->>'kind',change->'key',(ref->'fields')::text,ref->>'kind',parent);
   END IF;
  END LOOP;
  FOR fields IN SELECT * FROM jsonb_array_elements(definition->'unique') LOOP
   SELECT jsonb_agg(change->'payload'->f ORDER BY n) INTO vals FROM jsonb_array_elements_text(fields) WITH ORDINALITY AS k(f,n);
   IF NOT EXISTS(SELECT FROM jsonb_array_elements(vals) v WHERE v='null'::jsonb) THEN
    INSERT INTO public.noctua_academic_uniques(owner_id,kind,fields,values,record_key)
     VALUES(principal,change->>'kind',fields,vals,change->'key');
   END IF;
  END LOOP;
 END LOOP;
 PERFORM public.noctua_academic_graph(principal);
 result:=jsonb_build_object('operation',p_operation,'accepted',true,'sequence',seq,'conflicts','[]'::jsonb);
 INSERT INTO public.noctua_academic_receipts VALUES(principal,p_operation,p_changes,result);
 RETURN result;
END $$;

CREATE OR REPLACE FUNCTION public.noctua_e_guarded_write(p_operation uuid,p_changes jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_sync_principal(); change jsonb; previous jsonb; result jsonb; before_rows jsonb; after_rows jsonb; r jsonb; replacement jsonb; n integer;
BEGIN
 IF principal IS NULL OR NOT EXISTS(SELECT FROM public.noctua_academic_test_accounts WHERE owner_id=principal AND allowed) THEN RAISE EXCEPTION 'authorized test account required' USING ERRCODE='42501'; END IF;
 PERFORM 1 FROM public.noctua_sync_heads WHERE owner_id=principal FOR UPDATE;
 IF NOT EXISTS(SELECT FROM public.noctua_sync_protocols WHERE owner_id=principal AND academic_scope=5) THEN RAISE EXCEPTION 'phase E client required' USING ERRCODE='42501'; END IF;
 IF jsonb_typeof(p_changes) IS DISTINCT FROM 'array' THEN RAISE EXCEPTION 'invalid changes'; END IF;
 before_rows:=public.noctua_d_projection(principal);
 FOR change IN SELECT * FROM jsonb_array_elements(p_changes) LOOP
  PERFORM public.noctua_academic_validate(change->>'kind',change->'key',change->'payload');
  IF change->'payload'='null' THEN CONTINUE; END IF;
  SELECT payload INTO previous FROM public.noctua_academic_versions WHERE owner_id=principal AND kind=change->>'kind' AND key=change->'key' AND NOT deleted ORDER BY revision DESC LIMIT 1;
  IF previous IS NOT NULL AND ((change->>'kind'='classes' AND previous->'year' IS DISTINCT FROM change->'payload'->'year') OR
   (change->>'kind' IN('students','activities','lessons','lesson_records','import_drafts','export_rows') AND previous->'classId' IS DISTINCT FROM change->'payload'->'classId') OR
   (change->>'kind'='students' AND previous->'personId' IS DISTINCT FROM change->'payload'->'personId')) THEN RAISE EXCEPTION 'academic identity is immutable'; END IF;
 END LOOP;
 result:=public.noctua_e_write(p_operation,p_changes);
 IF NOT(result->>'accepted')::boolean THEN RETURN result; END IF;
 PERFORM public.noctua_d_graph(principal);
 after_rows:=public.noctua_d_projection(principal);
 PERFORM public.noctua_d_history_guard(before_rows,after_rows);
 FOR r IN SELECT value->'payload' FROM jsonb_array_elements(before_rows) WHERE value->>'kind'='relocations' LOOP
  replacement:=public.noctua_d_get(after_rows,'relocations',jsonb_build_object('sourceStudentId',r->>'sourceStudentId'));
  IF replacement IS NULL THEN
   IF EXISTS(SELECT FROM jsonb_array_elements(after_rows) WHERE value->>'kind'='students' AND value->'payload'->>'id' IN(r->>'sourceStudentId',r->>'targetStudentId')) THEN RAISE EXCEPTION 'movement chain cannot be removed'; END IF;
  ELSE
   IF jsonb_array_length(replacement->'corrections')<jsonb_array_length(r->'corrections') THEN RAISE EXCEPTION 'audit cannot be removed'; END IF;
   FOR n IN 0..jsonb_array_length(r->'corrections')-1 LOOP
    IF replacement->'corrections'->n IS DISTINCT FROM r->'corrections'->n THEN RAISE EXCEPTION 'audit cannot be rewritten'; END IF;
   END LOOP;
   IF r->'targetStudentId'<>'null' AND replacement IS DISTINCT FROM r THEN RAISE EXCEPTION 'confirmed internal link is immutable'; END IF;
   IF replacement IS DISTINCT FROM r THEN
    n:=jsonb_array_length(r->'corrections');
    IF jsonb_array_length(replacement->'corrections')<=n OR replacement->'corrections'->n->'previousDate' IS DISTINCT FROM r->'effectiveDate' OR
     replacement->'corrections'->n->'previousTarget' IS DISTINCT FROM r->'externalTarget' THEN RAISE EXCEPTION 'movement change requires an appended audit'; END IF;
   END IF;
  END IF;
 END LOOP;
 RETURN result;
END $$;

CREATE OR REPLACE FUNCTION public.noctua_begin_academic_e(p_operation uuid,p_total integer,p_parts integer)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_e_principal(); t public.noctua_e_transfers;
BEGIN
 IF p_operation IS NULL OR p_total IS NULL OR p_total NOT BETWEEN 1 AND 16384 OR p_parts IS NULL OR p_parts NOT BETWEEN 1 AND p_total THEN RAISE EXCEPTION 'invalid manifest'; END IF;
 PERFORM 1 FROM public.noctua_sync_heads WHERE owner_id=principal FOR UPDATE;
 -- Bound abandoned unconfirmed transfers too; no client cleanup of audit evidence.
 IF NOT EXISTS(SELECT FROM public.noctua_e_transfers WHERE owner_id=principal AND operation_id=p_operation) AND
  (SELECT count(*) FROM public.noctua_e_transfers pending_transfer WHERE owner_id=principal AND NOT EXISTS(SELECT FROM public.noctua_academic_receipts r WHERE r.owner_id=pending_transfer.owner_id AND r.operation_id=pending_transfer.operation_id))>=4
 THEN RAISE EXCEPTION 'review pending transfers before creating another'; END IF;
 INSERT INTO public.noctua_e_transfers(owner_id,operation_id,total,parts) VALUES(principal,p_operation,p_total,p_parts) ON CONFLICT DO NOTHING;
 SELECT * INTO t FROM public.noctua_e_transfers WHERE owner_id=principal AND operation_id=p_operation FOR UPDATE;
 IF t.total<>p_total OR t.parts<>p_parts THEN RAISE EXCEPTION 'operation manifest reused'; END IF;
 RETURN jsonb_build_object('operation',p_operation,'total',t.total,'parts',t.parts);
END $$;
CREATE OR REPLACE FUNCTION public.noctua_stage_academic_e(p_operation uuid,p_part integer,p_changes jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_e_principal(); t public.noctua_e_transfers; old jsonb; change jsonb;
BEGIN
 SELECT * INTO t FROM public.noctua_e_transfers WHERE owner_id=principal AND operation_id=p_operation FOR UPDATE;
 IF NOT FOUND OR p_part IS NULL OR p_part<0 OR p_part>=t.parts OR jsonb_typeof(p_changes) IS DISTINCT FROM 'array' OR
  jsonb_array_length(p_changes) NOT BETWEEN 1 AND 128 OR octet_length(p_changes::text)>786432 THEN RAISE EXCEPTION 'invalid transfer part'; END IF;
 FOR change IN SELECT * FROM jsonb_array_elements(p_changes) LOOP
  PERFORM public.noctua_academic_shape(change,'{"kind":"string","key":"object","base":"number","payload":"object?"}');
  IF change->>'base' !~ '^[0-9]{1,18}$' THEN RAISE EXCEPTION 'invalid base revision'; END IF;
  PERFORM public.noctua_academic_validate(change->>'kind',change->'key',change->'payload');
 END LOOP;
 SELECT changes INTO old FROM public.noctua_e_parts WHERE owner_id=principal AND operation_id=p_operation AND part=p_part;
 IF FOUND THEN
  IF old IS DISTINCT FROM p_changes THEN RAISE EXCEPTION 'transfer part reused'; END IF;
 ELSE
  INSERT INTO public.noctua_e_parts VALUES(principal,p_operation,p_part,p_changes);
 END IF;
 IF (SELECT coalesce(sum(octet_length(changes::text)),0)>16777216 OR coalesce(sum(jsonb_array_length(changes)),0)>t.total
  FROM public.noctua_e_parts WHERE owner_id=principal AND operation_id=p_operation) THEN RAISE EXCEPTION 'transfer limit exceeded'; END IF;
 RETURN jsonb_build_object('operation',p_operation,'part',p_part);
END $$;
CREATE OR REPLACE FUNCTION public.noctua_commit_academic_e(p_operation uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_e_principal(); t public.noctua_e_transfers; changes jsonb;
BEGIN
 -- Same account lock/order as all writers. Parts are immutable and locked by manifest.
 PERFORM 1 FROM public.noctua_sync_heads WHERE owner_id=principal FOR UPDATE;
 SELECT * INTO t FROM public.noctua_e_transfers WHERE owner_id=principal AND operation_id=p_operation FOR UPDATE;
 IF NOT FOUND OR (SELECT count(*) FROM public.noctua_e_parts WHERE owner_id=principal AND operation_id=p_operation)<>t.parts
 THEN RAISE EXCEPTION 'incomplete transfer'; END IF;
 SELECT jsonb_agg(c.value ORDER BY part,c.n) INTO changes FROM public.noctua_e_parts p,
  LATERAL jsonb_array_elements(p.changes) WITH ORDINALITY c(value,n) WHERE owner_id=principal AND operation_id=p_operation;
 IF jsonb_array_length(changes)<>t.total OR octet_length(changes::text)>16777216 THEN RAISE EXCEPTION 'manifest mismatch'; END IF;
 RETURN public.noctua_e_guarded_write(p_operation,changes);
END $$;
CREATE OR REPLACE FUNCTION public.noctua_pull_academic_e(p_after bigint,p_offset integer)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_e_principal(); b public.noctua_academic_batches;
 total integer; v record; rows jsonb:='[]'; item jsonb; bytes integer:=2; size integer;
BEGIN
 IF p_after IS NULL OR p_after<0 OR p_offset IS NULL OR p_offset<0 THEN RAISE EXCEPTION 'invalid cursor'; END IF;
 SELECT * INTO b FROM public.noctua_academic_batches WHERE owner_id=principal AND sequence>p_after ORDER BY sequence LIMIT 1;
 IF NOT FOUND THEN
  IF p_offset<>0 THEN RAISE EXCEPTION 'missing transfer'; END IF;
  RETURN '[]';
 END IF;
 SELECT count(*) INTO total FROM public.noctua_academic_versions WHERE owner_id=principal AND sequence=b.sequence;
 IF total NOT BETWEEN 1 AND 16384 OR p_offset>=total THEN RAISE EXCEPTION 'invalid transfer offset'; END IF;
 FOR v IN SELECT kind,key,revision,payload FROM public.noctua_academic_versions WHERE owner_id=principal AND sequence=b.sequence ORDER BY kind,key OFFSET p_offset LIMIT 128 LOOP
  item:=jsonb_build_object('kind',v.kind,'key',v.key,'revision',v.revision,'payload',v.payload);size:=octet_length(item::text)+2;
  IF size+2>786432 THEN RAISE EXCEPTION 'row exceeds transfer capacity'; END IF;
  EXIT WHEN bytes+size>786432;
  rows:=rows||jsonb_build_array(item);bytes:=bytes+size;
 END LOOP;
 RETURN jsonb_build_array(jsonb_build_object('operation',b.operation_id,'sequence',b.sequence,'total',total,'offset',p_offset,'records',rows));
END $$;
REVOKE ALL ON FUNCTION public.noctua_e_principal(),public.noctua_e_write(uuid,jsonb),public.noctua_e_guarded_write(uuid,jsonb),
 public.noctua_enable_academic_e(bigint),public.noctua_begin_academic_e(uuid,integer,integer),public.noctua_stage_academic_e(uuid,integer,jsonb),
 public.noctua_commit_academic_e(uuid),public.noctua_pull_academic_e(bigint,integer) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.noctua_enable_academic_e(bigint),public.noctua_begin_academic_e(uuid,integer,integer),
 public.noctua_stage_academic_e(uuid,integer,jsonb),public.noctua_commit_academic_e(uuid),public.noctua_pull_academic_e(bigint,integer) TO authenticated;

-- SOURCE 202611030002_phase_e_decisions.sql SHA256 f7785853b4438e72aea24a17b8b91812c4a250efd0a5e6ab8d61c8c3e44b54bf
-- E2/E3: immutable, owner-scoped decisions; CAS before any academic modification.
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

-- SOURCE 202611030003_phase_e_indexed_graph.sql SHA256 9ecd918240458a9367f3acba286a79445999b1b7058ac0e48fe3eab8ccc6f918
-- E4: accelerate the existing D rules; no new academic policy or public write path.
CREATE OR REPLACE FUNCTION public.noctua_d_index(p_rows jsonb)
RETURNS jsonb LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog AS $$
DECLARE result jsonb;
BEGIN
 IF EXISTS(SELECT FROM jsonb_array_elements(p_rows) GROUP BY value->>'kind',value->'key' HAVING count(*)>1) THEN
  RAISE EXCEPTION 'duplicate historical or live identity'; END IF;
 SELECT coalesce(jsonb_object_agg(kind,entries),'{}') INTO result FROM (
  SELECT value->>'kind' kind,jsonb_object_agg((value->'key')::text,value->'payload') entries
  FROM jsonb_array_elements(p_rows) GROUP BY value->>'kind'
 ) grouped;
 RETURN result;
END $$;
CREATE OR REPLACE FUNCTION public.noctua_d_get(p_rows jsonb,p_kind text,p_key jsonb)
RETURNS jsonb LANGUAGE sql IMMUTABLE SET search_path=pg_catalog AS $$
 SELECT CASE WHEN jsonb_typeof(p_rows)='object' THEN p_rows->p_kind->(p_key::text)
 ELSE (SELECT value->'payload' FROM jsonb_array_elements(p_rows) WHERE value->>'kind'=p_kind AND value->'key'=p_key) END;
$$;

CREATE OR REPLACE FUNCTION public.noctua_d_graph(p_owner uuid)
RETURNS void LANGUAGE plpgsql SET search_path=pg_catalog AS $$
DECLARE rows jsonb:=public.noctua_d_projection(p_owner); indexed jsonb; r jsonb; p jsonb; ref jsonb; parent jsonb; k jsonb;
 h jsonb; s jsonb; t jsonb; a jsonb; b jsonb; link jsonb; audit jsonb; previous jsonb; removed jsonb; x jsonb; y jsonb;
 f text; n integer; step integer; sid text; seen text[]; classes text[]; ids text[]; term integer;
BEGIN
 IF EXISTS(SELECT FROM jsonb_array_elements(rows) GROUP BY value->>'kind',value->'key' HAVING count(*)>1) THEN
  RAISE EXCEPTION 'duplicate historical or live identity'; END IF;
 indexed:=public.noctua_d_index(rows);
 FOR r IN SELECT * FROM jsonb_array_elements(rows) LOOP
  p:=r->'payload';PERFORM public.noctua_academic_validate(r->>'kind',r->'key',p);
  FOR ref IN SELECT * FROM jsonb_array_elements(public.noctua_academic_definition(r->>'kind')->'references') LOOP
   k:='{}';FOR n IN 0..jsonb_array_length(ref->'fields')-1 LOOP k:=k||jsonb_build_object(ref->'key'->>n,p->(ref->'fields'->>n)); END LOOP;
   IF NOT EXISTS(SELECT FROM jsonb_each(k) WHERE value='null') AND public.noctua_d_get(indexed,ref->>'kind',k) IS NULL THEN
    RAISE EXCEPTION 'missing academic relationship'; END IF;
  END LOOP;
  IF r->>'kind'='classes' THEN
   PERFORM public.noctua_validate_class(p->>'id',jsonb_build_object('school',p,'terms',
    (SELECT coalesce(jsonb_agg(value->'payload' ORDER BY (value->'payload'->>'number')::integer),'[]') FROM jsonb_array_elements(rows)
     WHERE value->>'kind'='terms' AND value->'payload'->>'classId'=p->>'id')));
  ELSIF r->>'kind'='students' THEN
   IF p->>'status'<>'Ativo' AND p->'left'='null' THEN RAISE EXCEPTION 'inactive enrollment without exit'; END IF;
   IF p->>'status'='Remanejado' AND public.noctua_d_get(indexed,'relocations',jsonb_build_object('sourceStudentId',p->>'id')) IS NULL THEN
    RAISE EXCEPTION 'moved enrollment without chain'; END IF;
  ELSIF r->>'kind' IN('marks','grades') THEN
   s:=public.noctua_d_get(indexed,'students',jsonb_build_object('id',p->>'studentId'));
   parent:=CASE WHEN r->>'kind'='marks' THEN public.noctua_d_get(indexed,'lessons',jsonb_build_object('id',p->>'lessonId'))
    ELSE public.noctua_d_get(indexed,'activities',jsonb_build_object('id',p->>'activityId')) END;
   IF s->>'classId' IS DISTINCT FROM parent->>'classId' OR
    (r->>'kind'='grades' AND p->'value'<>'null' AND (p->>'value')::numeric NOT BETWEEN 0 AND (parent->>'maximum')::numeric) THEN
    RAISE EXCEPTION 'academic relationship mismatch'; END IF;
  ELSIF r->>'kind'='recompositions' THEN
   s:=public.noctua_d_get(indexed,'students',jsonb_build_object('id',p->>'studentId'));
   IF public.noctua_d_get(indexed,'terms',jsonb_build_object('classId',s->>'classId','number',(p->>'term')::integer)) IS NULL THEN
    RAISE EXCEPTION 'recomposition without term'; END IF;
  ELSIF r->>'kind'='lesson_records' THEN
   IF btrim(p->>'title')='' AND btrim(p->>'description')='' THEN RAISE EXCEPTION 'empty journal'; END IF;
  ELSIF r->>'kind'='export_rows' THEN
   IF (p->>'position')::integer<0 THEN RAISE EXCEPTION 'invalid layout position'; END IF;
   IF p->'studentId'='null' THEN
    IF left(p->>'id',6)<>'blank:' OR length(p->>'id')<=6 OR (p->>'manualHidden')::boolean THEN RAISE EXCEPTION 'invalid blank row'; END IF;
   ELSIF p->>'id'<>'student:'||(p->>'studentId') THEN RAISE EXCEPTION 'layout student identity mismatch'; END IF;
  ELSIF r->>'kind'='weekly_timetables' THEN
   IF p->>'createdAt' !~ 'Z$' THEN RAISE EXCEPTION 'invalid revision instant'; END IF;
   PERFORM (p->>'createdAt')::timestamptz;
   FOR x IN SELECT * FROM jsonb_array_elements(p->'slots') LOOP
    FOREACH f IN ARRAY ARRAY['id','classId','className','subject','shift'] LOOP
     IF btrim(x->>f)='' THEN RAISE EXCEPTION 'empty timetable slot'; END IF;
    END LOOP;
    a:=public.noctua_d_get(indexed,'classes',jsonb_build_object('id',x->>'classId'));
    IF a IS NOT NULL AND a->'year' IS DISTINCT FROM p->'year' THEN RAISE EXCEPTION 'slot year mismatch'; END IF;
   END LOOP;
   IF EXISTS(SELECT FROM jsonb_array_elements(p->'slots') GROUP BY value->>'id' HAVING count(*)>1) OR
    EXISTS(SELECT FROM jsonb_array_elements(p->'slots') GROUP BY value->>'weekday',value->>'ordinal',lower(btrim(value->>'shift')) HAVING count(*)>1) OR
    EXISTS(SELECT FROM jsonb_array_elements(p->'slots') GROUP BY value->>'classId',value->>'weekday',value->>'ordinal' HAVING count(*)>1) THEN
    RAISE EXCEPTION 'conflicting timetable slots'; END IF;
  END IF;
 END LOOP;
 IF EXISTS(SELECT FROM jsonb_array_elements(rows) WHERE value->>'kind'='lessons'
  GROUP BY value->'payload'->>'classId',value->'payload'->>'date',value->'payload'->>'ordinal' HAVING count(*)>1) THEN
  RAISE EXCEPTION 'duplicate historical or live lesson period'; END IF;
 IF EXISTS(SELECT FROM jsonb_array_elements(rows) WHERE value->>'kind'='students'
  GROUP BY value->'payload'->>'classId',value->'payload'->>'number' HAVING count(*)>1) THEN RAISE EXCEPTION 'duplicate roster number'; END IF;
 IF EXISTS(SELECT FROM jsonb_array_elements(rows) r CROSS JOIN LATERAL jsonb_array_elements(r.value->'payload'->'slots') s
   WHERE r.value->>'kind'='weekly_timetables' GROUP BY s.value->>'id'
   HAVING count(DISTINCT jsonb_build_array(s.value->>'classId',s.value->>'weekday',s.value->>'ordinal'))>1) THEN
   RAISE EXCEPTION 'slot identity changed across revisions'; END IF;
 IF EXISTS(SELECT FROM jsonb_array_elements(rows) WHERE value->>'kind'='export_rows' GROUP BY value->'payload'->>'classId'
  HAVING min((value->'payload'->>'position')::integer)<>0 OR max((value->'payload'->>'position')::integer)<>count(*)-1) THEN
  RAISE EXCEPTION 'non contiguous export layout'; END IF;
 -- Internal/external movement and append-only administrative sequence, including historical fragments.
 IF EXISTS(SELECT FROM jsonb_array_elements(rows) WHERE value->>'kind'='relocations' AND value->'payload'->'targetStudentId'<>'null'
  GROUP BY value->'payload'->>'targetStudentId' HAVING count(*)>1) THEN RAISE EXCEPTION 'duplicate relocation target'; END IF;
 FOR link IN SELECT value->'payload' FROM jsonb_array_elements(rows) WHERE value->>'kind'='relocations' LOOP
  s:=public.noctua_d_get(indexed,'students',jsonb_build_object('id',link->>'sourceStudentId'));
  a:=public.noctua_d_get(indexed,'classes',jsonb_build_object('id',s->>'classId'));
  IF link->>'effectiveDate'<=s->>'joined' OR (link->>'effectiveDate')::date-1 IS DISTINCT FROM (s->>'left')::date OR
   s->>'status'<>'Remanejado' OR extract(year FROM (link->>'effectiveDate')::date)<>(a->>'year')::integer THEN
   RAISE EXCEPTION 'invalid source exit'; END IF;
  t:=NULL;
  IF link->'targetStudentId'='null' THEN
   IF link->'externalTarget'='null' OR btrim(link->>'externalTarget')='' THEN RAISE EXCEPTION 'missing external target'; END IF;
  ELSE
   t:=public.noctua_d_get(indexed,'students',jsonb_build_object('id',link->>'targetStudentId'));
   b:=public.noctua_d_get(indexed,'classes',jsonb_build_object('id',t->>'classId'));
   IF link->'externalTarget'<>'null' OR s->'personId' IS DISTINCT FROM t->'personId' OR t->'joined' IS DISTINCT FROM link->'effectiveDate' OR
    a->>'id'=b->>'id' OR a->'year' IS DISTINCT FROM b->'year' OR a->'stage' IS DISTINCT FROM b->'stage' OR
    public.noctua_d_subject(a->>'subject') IS DISTINCT FROM public.noctua_d_subject(b->>'subject') OR a->'scale' IS DISTINCT FROM b->'scale' OR
    a->'countJustified' IS DISTINCT FROM b->'countJustified' THEN RAISE EXCEPTION 'incompatible internal movement'; END IF;
   FOR term IN 1..4 LOOP
    x:=public.noctua_d_get(indexed,'terms',jsonb_build_object('classId',a->>'id','number',term));
    y:=public.noctua_d_get(indexed,'terms',jsonb_build_object('classId',b->>'id','number',term));
    IF x->'start' IS DISTINCT FROM y->'start' OR x->'end' IS DISTINCT FROM y->'end' THEN RAISE EXCEPTION 'incompatible calendars'; END IF;
   END LOOP;
  END IF;
  previous:=NULL;
  IF EXISTS(SELECT FROM jsonb_array_elements(link->'corrections') GROUP BY value->>'id' HAVING count(*)>1) THEN RAISE EXCEPTION 'duplicate correction'; END IF;
  FOR audit,step IN SELECT value,ordinality::integer FROM jsonb_array_elements(link->'corrections') WITH ORDINALITY LOOP
   IF btrim(audit->>'id')='' OR btrim(audit->>'previousTarget')='' OR audit->>'confirmedAt' !~ 'Z$' THEN RAISE EXCEPTION 'invalid correction'; END IF;
   PERFORM (audit->>'confirmedAt')::timestamptz;
   FOREACH f IN ARRAY ARRAY['previousDate','effectiveDate'] LOOP
    IF audit->>f !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' OR (audit->>f)::date::text IS DISTINCT FROM audit->>f OR
     extract(year FROM (audit->>f)::date)<>(a->>'year')::integer OR audit->>f<=s->>'joined' THEN RAISE EXCEPTION 'invalid correction date'; END IF;
   END LOOP;
   IF previous IS NOT NULL AND (audit->'previousDate' IS DISTINCT FROM previous->'effectiveDate' OR audit->'previousTarget' IS DISTINCT FROM previous->'externalTarget') THEN
    RAISE EXCEPTION 'broken correction sequence'; END IF;
   IF audit->>'operation'='CONVERSAO_INTERNA' THEN
    IF t IS NULL OR step<>jsonb_array_length(link->'corrections') OR audit->'externalTarget'<>'null' OR
     audit->'sourceStudentId' IS DISTINCT FROM s->'id' OR audit->'targetStudentId' IS DISTINCT FROM t->'id' OR
     audit->'targetClassId' IS DISTINCT FROM t->'classId' OR coalesce(btrim(audit->>'targetLabel'),'')='' THEN RAISE EXCEPTION 'conversion audit mismatch'; END IF;
   ELSIF audit->>'operation'='CORRECAO_EXTERNA' THEN
    IF audit->'externalTarget'='null' OR length(audit->>'externalTarget') NOT BETWEEN 2 AND 160 OR
     audit->'sourceStudentId'<>'null' OR audit->'targetStudentId'<>'null' OR audit->'targetClassId'<>'null' OR audit->'targetLabel'<>'null' THEN RAISE EXCEPTION 'external audit mismatch'; END IF;
   ELSE RAISE EXCEPTION 'unsupported correction'; END IF;
   IF EXISTS(SELECT FROM jsonb_array_elements(audit->'removed') GROUP BY value->'attendance'->>'lessonId' HAVING count(*)>1) THEN RAISE EXCEPTION 'duplicate removed occurrence'; END IF;
   FOR removed IN SELECT * FROM jsonb_array_elements(audit->'removed') LOOP
    PERFORM public.noctua_academic_shape(removed,'{"lesson":"object","attendance":"object"}');x:=removed->'lesson';y:=removed->'attendance';
    PERFORM public.noctua_academic_validate('lessons',public.noctua_academic_key('lessons',x),x);
    PERFORM public.noctua_academic_validate('marks',public.noctua_academic_key('marks',y),y);
    IF y->'studentId' IS DISTINCT FROM s->'id' OR y->'lessonId' IS DISTINCT FROM x->'id' OR x->'classId' IS DISTINCT FROM s->'classId' OR
     x->>'date'<audit->>'effectiveDate' THEN RAISE EXCEPTION 'removed occurrence mismatch'; END IF;
   END LOOP;
   previous:=audit;
  END LOOP;
  IF previous IS NOT NULL AND (previous->'effectiveDate' IS DISTINCT FROM link->'effectiveDate' OR previous->'externalTarget' IS DISTINCT FROM link->'externalTarget' OR
   ((previous->>'operation'='CONVERSAO_INTERNA') IS DISTINCT FROM (link->'targetStudentId'<>'null'))) THEN RAISE EXCEPTION 'current target diverges from audit'; END IF;
 END LOOP;
 -- Walk each lineage (never infer by personId alone); detect repeated classes and recomp conflicts.
 FOR s IN SELECT value->'payload' FROM jsonb_array_elements(rows) WHERE value->>'kind'='students' LOOP
  ids:=ARRAY[]::text[];classes:=ARRAY[]::text[];sid:=s->>'id';
  LOOP
   t:=public.noctua_d_get(indexed,'students',jsonb_build_object('id',sid));
   IF sid=ANY(ids) OR t->>'classId'=ANY(classes) OR t->'personId' IS DISTINCT FROM s->'personId' THEN RAISE EXCEPTION 'cyclic or incompatible lineage'; END IF;
   ids:=array_append(ids,sid);classes:=array_append(classes,t->>'classId');
   SELECT value->'payload'->>'sourceStudentId' INTO sid FROM jsonb_array_elements(rows)
    WHERE value->>'kind'='relocations' AND value->'payload'->>'targetStudentId'=sid;
   EXIT WHEN sid IS NULL;
  END LOOP;
  IF EXISTS(SELECT FROM jsonb_array_elements(rows) WHERE value->>'kind'='recompositions' AND value->'payload'->>'studentId'=ANY(ids)
   GROUP BY value->'payload'->>'term' HAVING count(*)>1) THEN RAISE EXCEPTION 'duplicate lineage recomposition'; END IF;
 END LOOP;
 -- Historical storage is self-contained, scoped to its class, and connected to a live enrollment.
 FOR h IN SELECT payload FROM public.noctua_academic_records WHERE owner_id=p_owner AND kind='preserved_histories' AND NOT deleted LOOP
  IF h->'classId' IS DISTINCT FROM h->'content'->'school'->'id' OR jsonb_array_length(h->'content'->'students')=0 THEN RAISE EXCEPTION 'invalid preserved origin'; END IF;
  FOR f IN SELECT unnest(ARRAY['students','terms','lessons','activities']) LOOP
   IF EXISTS(SELECT FROM jsonb_array_elements(h->'content'->f) WHERE value->'classId' IS DISTINCT FROM h->'classId') THEN RAISE EXCEPTION 'foreign historical class'; END IF;
  END LOOP;
  SELECT array_agg(value->>'id') INTO ids FROM jsonb_array_elements(h->'content'->'students');
  FOREACH f IN ARRAY ARRAY['marks','grades','recompositions'] LOOP
   IF EXISTS(SELECT FROM jsonb_array_elements(h->'content'->f) WHERE NOT(value->>'studentId'=ANY(ids))) THEN RAISE EXCEPTION 'foreign historical enrollment'; END IF;
  END LOOP;
  IF EXISTS(SELECT FROM jsonb_array_elements(h->'content'->'relocations') WHERE NOT(value->>'sourceStudentId'=ANY(ids) OR coalesce(value->>'targetStudentId'=ANY(ids),false))) THEN RAISE EXCEPTION 'foreign historical link'; END IF;
  FOR sid IN SELECT unnest(ids) LOOP
   IF NOT EXISTS(
    WITH RECURSIVE connected(id) AS (
     SELECT payload->>'id' FROM public.noctua_academic_records WHERE owner_id=p_owner AND kind='students' AND NOT deleted
     UNION
     SELECT CASE WHEN r.value->'payload'->>'sourceStudentId'=c.id THEN r.value->'payload'->>'targetStudentId' ELSE r.value->'payload'->>'sourceStudentId' END
     FROM connected c JOIN jsonb_array_elements(rows) r ON r.value->>'kind'='relocations' AND r.value->'payload'->'targetStudentId'<>'null' AND
      (r.value->'payload'->>'sourceStudentId'=c.id OR r.value->'payload'->>'targetStudentId'=c.id)
    ) SELECT FROM connected WHERE id=sid
   ) THEN RAISE EXCEPTION 'orphan preserved history'; END IF;
  END LOOP;
 END LOOP;
END $$;

CREATE OR REPLACE FUNCTION public.noctua_commit_academic_e(p_operation uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE principal uuid:=public.noctua_e_principal(); t public.noctua_e_transfers; changes jsonb; receipt public.noctua_academic_receipts;
BEGIN
 -- Same account lock/order as all writers. Parts are immutable and locked by manifest.
 PERFORM 1 FROM public.noctua_sync_heads WHERE owner_id=principal FOR UPDATE;
 SELECT * INTO t FROM public.noctua_e_transfers WHERE owner_id=principal AND operation_id=p_operation FOR UPDATE;
 IF NOT FOUND OR (SELECT count(*) FROM public.noctua_e_parts WHERE owner_id=principal AND operation_id=p_operation)<>t.parts
 THEN RAISE EXCEPTION 'incomplete transfer'; END IF;
 SELECT jsonb_agg(c.value ORDER BY part,c.n) INTO changes FROM public.noctua_e_parts p,
  LATERAL jsonb_array_elements(p.changes) WITH ORDINALITY c(value,n) WHERE owner_id=principal AND operation_id=p_operation;
 IF jsonb_array_length(changes)<>t.total OR octet_length(changes::text)>16777216 THEN RAISE EXCEPTION 'manifest mismatch'; END IF;
 SELECT * INTO receipt FROM public.noctua_academic_receipts WHERE owner_id=principal AND operation_id=p_operation;
 IF FOUND THEN
  IF receipt.request IS DISTINCT FROM changes THEN RAISE EXCEPTION 'operation identity reused'; END IF;
  RETURN receipt.response;
 END IF;
 RETURN public.noctua_e_guarded_write(p_operation,changes);
END $$;
REVOKE ALL ON FUNCTION public.noctua_d_index(jsonb) FROM PUBLIC,anon,authenticated;

NOTIFY pgrst, 'reload schema';
COMMIT;
