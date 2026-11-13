-- H13: identical academic registry, cached PL/pgSQL constants instead of repeatedly
-- planning a SQL function with a large JSONB literal. No validator/RPC/RLS changes.
-- Apply only after E4; no deployment to hosted projects is implied by this file.
BEGIN;
DO $$ BEGIN
 IF to_regprocedure('public.noctua_d_index(jsonb)') IS NULL OR
    to_regprocedure('public.noctua_resolve_academic_e(uuid,jsonb,boolean)') IS NULL THEN
  RAISE EXCEPTION 'H13 requires E1-E4 and decision/reconciliation migrations';
 END IF;
END $$;
CREATE OR REPLACE FUNCTION public.noctua_academic_definition(p_kind text)
RETURNS jsonb LANGUAGE plpgsql IMMUTABLE SET search_path=pg_catalog AS $definition$
BEGIN
 RETURN CASE p_kind COLLATE pg_catalog."C"
  WHEN 'classes' THEN $registry${"key":["id"],"fields":{"id":"string","name":"string","stage":"string","subject":"string","year":"integer","shift":"string","schedule":"string","duration":"integer","scale":"number","low":"number","high":"number","countJustified":"boolean","startPresent":"boolean"},"references":[],"unique":[]}$registry$::jsonb
  WHEN 'students' THEN $registry${"key":["id"],"fields":{"id":"string","personId":"string","classId":"string","name":"string","number":"integer","joined":"string","left":"string?","status":"string","notes":"string"},"references":[{"fields":["classId"],"kind":"classes","key":["id"]}],"unique":[]}$registry$::jsonb
  WHEN 'terms' THEN $registry${"key":["classId","number"],"fields":{"classId":"string","number":"integer","start":"string","end":"string"},"references":[{"fields":["classId"],"kind":"classes","key":["id"]}],"unique":[]}$registry$::jsonb
  WHEN 'lessons' THEN $registry${"key":["id"],"fields":{"id":"string","classId":"string","date":"string","ordinal":"integer","state":"string","note":"string"},"references":[{"fields":["classId"],"kind":"classes","key":["id"]}],"unique":[["classId","date","ordinal"]]}$registry$::jsonb
  WHEN 'marks' THEN $registry${"key":["lessonId","studentId"],"fields":{"lessonId":"string","studentId":"string","mark":"string","reason":"string?"},"references":[{"fields":["lessonId"],"kind":"lessons","key":["id"]},{"fields":["studentId"],"kind":"students","key":["id"]}],"unique":[]}$registry$::jsonb
  WHEN 'activities' THEN $registry${"key":["id"],"fields":{"id":"string","classId":"string","name":"string","type":"string","date":"string","term":"integer","maximum":"number","description":"string","notes":"string"},"references":[{"fields":["classId"],"kind":"classes","key":["id"]}],"unique":[]}$registry$::jsonb
  WHEN 'grades' THEN $registry${"key":["activityId","studentId"],"fields":{"activityId":"string","studentId":"string","value":"number?"},"references":[{"fields":["activityId"],"kind":"activities","key":["id"]},{"fields":["studentId"],"kind":"students","key":["id"]}],"unique":[]}$registry$::jsonb
  WHEN 'import_drafts' THEN $registry${"key":["id"],"fields":{"id":"string","classId":"string","name":"string","number":"integer?","position":"integer","page":"integer","reason":"string","reviewed":"boolean","allowHomonym":"boolean"},"references":[{"fields":["classId"],"kind":"classes","key":["id"]}],"unique":[]}$registry$::jsonb
  WHEN 'recompositions' THEN $registry${"key":["studentId","term"],"fields":{"studentId":"string","term":"integer","value":"number?","date":"string"},"references":[{"fields":["studentId"],"kind":"students","key":["id"]}],"unique":[]}$registry$::jsonb
  WHEN 'relocations' THEN $registry${"key":["sourceStudentId"],"fields":{"sourceStudentId":"string","targetStudentId":"string?","effectiveDate":"string","externalTarget":"string?","corrections":"array"},"references":[{"fields":["sourceStudentId"],"kind":"students","key":["id"]},{"fields":["targetStudentId"],"kind":"students","key":["id"]}],"unique":[["targetStudentId"]]}$registry$::jsonb
  WHEN 'export_rows' THEN $registry${"key":["id"],"fields":{"id":"string","classId":"string","studentId":"string?","position":"integer","manualHidden":"boolean"},"references":[{"fields":["classId"],"kind":"classes","key":["id"]},{"fields":["studentId"],"kind":"students","key":["id"]}],"unique":[["classId","position"],["studentId"]]}$registry$::jsonb
  WHEN 'class_archives' THEN $registry${"key":["classId"],"fields":{"classId":"string"},"references":[{"fields":["classId"],"kind":"classes","key":["id"]}],"unique":[]}$registry$::jsonb
  WHEN 'lesson_records' THEN $registry${"key":["id"],"fields":{"id":"string","classId":"string","date":"string","title":"string","description":"string"},"references":[{"fields":["classId"],"kind":"classes","key":["id"]}],"unique":[]}$registry$::jsonb
  WHEN 'preserved_histories' THEN $registry${"key":["classId"],"fields":{"classId":"string","content":"object"},"references":[],"unique":[]}$registry$::jsonb
  WHEN 'weekly_timetables' THEN $registry${"key":["id"],"fields":{"id":"string","year":"integer","effective":"string","revision":"integer","createdAt":"string","slots":"array"},"references":[],"unique":[["year","effective","revision"]]}$registry$::jsonb
  ELSE NULL
 END;
END $definition$;
-- Preserve the private helper boundary; the authenticated RPCs retain their guards.
REVOKE ALL ON FUNCTION public.noctua_academic_definition(text) FROM PUBLIC,anon,authenticated;
COMMIT;
