-- Phase D. Review and run once AFTER A, B, C; never applied to the hosted project by tests.
BEGIN;
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
COMMIT;
