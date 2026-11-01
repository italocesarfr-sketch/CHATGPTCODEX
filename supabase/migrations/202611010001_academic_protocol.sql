-- Phase A protocol only: no changes to v1, Auth, Room or existing academic rows.
-- Requires BOTH 202610300001 and 202610310001; do not enable clients before phases B-E.
BEGIN;
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
COMMIT;
