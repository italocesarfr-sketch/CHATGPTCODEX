-- E4: accelerate the existing D rules; no new academic policy or public write path.
BEGIN;
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
COMMIT;
