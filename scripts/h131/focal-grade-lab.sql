-- RESEARCH ONLY. Never apply to Supabase. Not a migration, RPC or production writer.
-- Does NOT establish that the unchanged graph is valid. Differential tests MUST
-- expose this missing precondition rather than allowing this predicate to write.
CREATE SCHEMA h131_lab;
REVOKE ALL ON SCHEMA h131_lab FROM PUBLIC,anon,authenticated;
CREATE FUNCTION h131_lab.grade_predicate(p_owner uuid,p_changes jsonb)
RETURNS text LANGUAGE plpgsql SET search_path=pg_catalog AS $$
DECLARE c jsonb; old public.noctua_academic_records; s jsonb; a jsonb; school jsonb; terms jsonb;
BEGIN
 IF jsonb_typeof(p_changes)<>'array' OR jsonb_array_length(p_changes)<>1 THEN RETURN 'FULL_REQUIRED'; END IF;
 c:=p_changes->0;
 IF c->>'kind'<>'grades' OR c->'payload' IS NULL OR c->'payload'='null' THEN RETURN 'FULL_REQUIRED'; END IF;
 PERFORM public.noctua_academic_shape(c,'{"kind":"string","key":"object","base":"number","payload":"object?"}');
 IF c->>'base' !~ '^[0-9]{1,18}$' THEN RAISE EXCEPTION 'invalid revision'; END IF;
 PERFORM public.noctua_academic_validate('grades',c->'key',c->'payload');
 SELECT * INTO old FROM public.noctua_academic_records WHERE owner_id=p_owner AND kind='grades' AND key=c->'key' AND NOT deleted;
 IF NOT FOUND THEN RETURN 'FULL_REQUIRED'; END IF;
 IF old.payload-'value' IS DISTINCT FROM (c->'payload')-'value' THEN RETURN 'FULL_REQUIRED'; END IF;
 IF old.revision<>(c->>'base')::bigint THEN RETURN 'REJECT'; END IF;
 SELECT payload INTO s FROM public.noctua_academic_records WHERE owner_id=p_owner AND kind='students' AND key=jsonb_build_object('id',c->'payload'->>'studentId') AND NOT deleted;
 SELECT payload INTO a FROM public.noctua_academic_records WHERE owner_id=p_owner AND kind='activities' AND key=jsonb_build_object('id',c->'payload'->>'activityId') AND NOT deleted;
 IF s IS NULL OR a IS NULL THEN RETURN 'REJECT'; END IF;
 PERFORM public.noctua_academic_validate('students',public.noctua_academic_key('students',s),s);
 PERFORM public.noctua_academic_validate('activities',public.noctua_academic_key('activities',a),a);
 IF s->>'status'<>'Ativo' AND s->'left'='null' THEN RETURN 'REJECT'; END IF;
 IF s->'classId' IS DISTINCT FROM a->'classId' OR
    (c->'payload'->'value'<>'null' AND (c->'payload'->>'value')::numeric NOT BETWEEN 0 AND (a->>'maximum')::numeric) THEN RETURN 'REJECT'; END IF;
 SELECT payload INTO school FROM public.noctua_academic_records WHERE owner_id=p_owner AND kind='classes' AND key=jsonb_build_object('id',s->>'classId') AND NOT deleted;
 IF school IS NULL THEN RETURN 'REJECT'; END IF;
 SELECT coalesce(jsonb_agg(payload ORDER BY (payload->>'number')::integer),'[]') INTO terms
 FROM public.noctua_academic_records WHERE owner_id=p_owner AND kind='terms' AND NOT deleted AND payload->'classId'=s->'classId';
 PERFORM public.noctua_validate_class(s->>'classId',jsonb_build_object('school',school,'terms',terms));
 RETURN 'ACCEPT';
END $$;
REVOKE ALL ON FUNCTION h131_lab.grade_predicate(uuid,jsonb) FROM PUBLIC,anon,authenticated;

-- Runs the ORIGINAL writer inside a subtransaction that is ALWAYS rolled back.
-- This never installs the candidate in a write path, even in this disposable DB.
CREATE FUNCTION h131_lab.compare(p_operation uuid,p_changes jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE owner uuid:=public.noctua_e_principal(); focal text; full_ok boolean:=false; err text;
 r jsonb; started timestamptz; focal_ms numeric; full_ms numeric;
BEGIN
 started:=clock_timestamp();
 BEGIN
  focal:=h131_lab.grade_predicate(owner,p_changes);
 EXCEPTION WHEN OTHERS THEN focal:='REJECT'; END;
 focal_ms:=extract(epoch FROM clock_timestamp()-started)*1000;
 started:=clock_timestamp();
 BEGIN
  r:=public.noctua_e_guarded_write(p_operation,p_changes);full_ok:=(r->>'accepted')::boolean;
  RAISE EXCEPTION 'lab rollback' USING ERRCODE='P1311';
 EXCEPTION WHEN SQLSTATE 'P1311' THEN NULL;
 WHEN OTHERS THEN GET STACKED DIAGNOSTICS err=RETURNED_SQLSTATE;full_ok:=false;
 END;
 full_ms:=extract(epoch FROM clock_timestamp()-started)*1000;
 RETURN jsonb_build_object('candidate',focal,'integral_accepted',full_ok,'integral_sqlstate',err,
   'focal_predicate_ms',focal_ms,'integral_writer_rollback_ms',full_ms,
   'equivalent_on_case',focal='FULL_REQUIRED' OR (focal='ACCEPT')=full_ok,
   'production_route','INTEGRAL_UNCHANGED');
END $$;
REVOKE ALL ON FUNCTION h131_lab.compare(uuid,jsonb) FROM PUBLIC,anon;
GRANT USAGE ON SCHEMA h131_lab TO authenticated;
GRANT EXECUTE ON FUNCTION h131_lab.compare(uuid,jsonb) TO authenticated;
