-- E1: persistent bounded transfers. Apply after A/B/C/D, never automatically hosted.
BEGIN;
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
COMMIT;
