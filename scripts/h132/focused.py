#!/usr/bin/env python3
"""Differential original/candidate full guards, SQLSTATEs and transactional state.
Only whitelisted disposable PostgreSQL; no scoped shortcut/certificate/hosted use.
"""
import copy,hashlib,json,random,unittest,uuid
import common as c
b,f,bench,lab=c.fixtures()
telemetry=bench.telemetry.Journal(c.ROOT/'build/h132/focused-progress.json','H13.2-focused')
RESULTS=[]
f.execute('DROP SCHEMA IF EXISTS h132_lab CASCADE; CREATE SCHEMA h132_lab; REVOKE ALL ON SCHEMA h132_lab FROM PUBLIC,anon,authenticated;')
f.execute(c.OLD_GET.replace('public.noctua_d_get','h132_lab.old_get'))
f.execute(c.OLD_GUARD.replace('public.noctua_d_history_guard','h132_lab.old_guard').replace('public.noctua_d_get','h132_lab.old_get'))
f.execute("""
CREATE FUNCTION h132_lab.inspect_get(rows jsonb,kind text,key jsonb,candidate boolean) RETURNS jsonb LANGUAGE plpgsql SET search_path=pg_catalog AS $$
DECLARE v jsonb; code text; msg text;
BEGIN
 IF candidate THEN v:=public.noctua_d_get(rows,kind,key);ELSE v:=h132_lab.old_get(rows,kind,key);END IF;
 RETURN jsonb_build_object('is_sql_null',v IS NULL,'value',v);
 EXCEPTION WHEN OTHERS THEN GET STACKED DIAGNOSTICS code=RETURNED_SQLSTATE,msg=MESSAGE_TEXT;RETURN jsonb_build_object('error',code,'message_hash',md5(msg));
END $$;
CREATE FUNCTION h132_lab.inspect_guard(before_rows jsonb,after_rows jsonb,candidate boolean) RETURNS jsonb LANGUAGE plpgsql SET search_path=pg_catalog AS $$
DECLARE code text;msg text;
BEGIN
 IF candidate THEN PERFORM public.noctua_d_history_guard(before_rows,after_rows);ELSE PERFORM h132_lab.old_guard(before_rows,after_rows);END IF;
 RETURN '{"accepted":true}'::jsonb;
 EXCEPTION WHEN OTHERS THEN GET STACKED DIAGNOSTICS code=RETURNED_SQLSTATE,msg=MESSAGE_TEXT;RETURN jsonb_build_object('error',code,'message_hash',md5(msg));
END $$;
CREATE FUNCTION h132_lab.state() RETURNS text LANGUAGE plpgsql SET search_path=pg_catalog AS $$
DECLARE t text;v text;result text:='';
BEGIN
 FOREACH t IN ARRAY ARRAY['academic_records','academic_versions','academic_batches','academic_receipts','academic_links','academic_uniques','academic_heads','e_decisions'] LOOP
  EXECUTE format('SELECT coalesce(string_agg(v::text,E''\\n'' ORDER BY v::text),'''') FROM (SELECT to_jsonb(t)-''confirmed_at'' v FROM public.noctua_%I t) q',t) INTO v;
  result:=result||t||v;
 END LOOP;
 RETURN encode(sha256(convert_to(result,'UTF8')),'hex');
END $$;
CREATE FUNCTION h132_lab.writer(op uuid,changes jsonb) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog AS $$
DECLARE reply jsonb;hash text;code text;msg text;
BEGIN
 BEGIN
  reply:=public.noctua_e_guarded_write(op,changes);hash:=h132_lab.state();
  RAISE EXCEPTION 'rollback differential probe' USING ERRCODE='P1321';
 EXCEPTION WHEN SQLSTATE 'P1321' THEN NULL;
 WHEN OTHERS THEN GET STACKED DIAGNOSTICS code=RETURNED_SQLSTATE,msg=MESSAGE_TEXT;
 END;
 RETURN jsonb_build_object('reply',reply,'state_after_write',hash,'error',code,'message_hash',md5(msg));
END $$;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA h132_lab FROM PUBLIC,anon,authenticated;
GRANT USAGE ON SCHEMA h132_lab TO authenticated;
GRANT EXECUTE ON FUNCTION h132_lab.writer(uuid,jsonb) TO authenticated;
""")

def activate():b.reset();b.activate();f.d.c.enable();f.d.enable();f.enable();f.execute(c.CANDIDATE.read_text())
def state():return f.execute('SELECT h132_lab.state();')
def push(rows):
 op,parts,_=f.begin(rows)
 for n,p in enumerate(parts):f.stage(op,n,p)
 result,m=bench.sql_commit(f,op);assert result and result['accepted'];return result

def compare_writer(name,rows,owner=f.A):
 op=str(uuid.uuid4());before=state();answers=[]
 for label,sql in [('before',c.BASELINE),('after',c.CANDIDATE.read_text())]:
  f.execute(sql)
  r=json.loads(f.execute("SET statement_timeout='8s'; SELECT h132_lab.writer('"+op+"',"+f.literal(rows)+");",owner));answers.append(r)
  assert state()==before,'Differential writer leaked a transaction'
 assert answers[0]==answers[1],name+' differential mismatch (private evidence omitted)'
 RESULTS.append(dict(case=name,equal=True,accepted=(answers[0]['reply']or{}).get('accepted'),sqlstate=answers[0]['error'],state_hash_equal=True))
 f.execute(c.CANDIDATE.read_text())

class H132Test(unittest.TestCase):
 def setUp(self):activate()
 def test_01_exact_predicates_metadata_idempotence_and_scope(self):
  original=c.OLD_GUARD
  new=c.function('scripts/h132/candidate.sql','noctua_d_history_guard')
  begin=new.index(' -- H132_LOOKUP_BEGIN');end=new.index(' -- H132_LOOKUP_END',begin)+len(' -- H132_LOOKUP_END\n')
  normalized=(new[:begin]+new[end:]).replace(c.lookup_source('k'),'lookup_rows').replace(c.lookup_source("r->'key'"),'lookup_rows').replace('current jsonb; lookup_rows jsonb:=p_after;','current jsonb;').replace('public.noctua_d_get(lookup_rows,','public.noctua_d_get(p_after,')
  self.assertEqual(original,normalized,'Historical predicate text changed')
  graph=c.function('scripts/h132/candidate.sql','noctua_d_graph')
  start=graph.index(' -- H132_SUBSETS_BEGIN')-1;end=graph.index(' -- H132_SUBSETS_END',start)+len(' -- H132_SUBSETS_END')
  normalized_graph=(graph[:start]+graph[end:]).replace(' term integer; relocation_rows jsonb; recomposition_rows jsonb;',' term integer;').replace('jsonb_array_elements(relocation_rows)','jsonb_array_elements(rows)').replace('jsonb_array_elements(recomposition_rows)','jsonb_array_elements(rows)')
  self.assertEqual(c.OLD_GRAPH,normalized_graph,'Graph predicate text changed')
  f.execute(c.BASELINE);before=c.metadata(f);body=c.bodies(f);f.execute(c.CANDIDATE.read_text());f.execute(c.CANDIDATE.read_text())
  self.assertEqual(before,c.metadata(f));self.assertEqual(body,c.bodies(f))
  for call in ['noctua_d_get(\'[]\',\'students\',\'{}\')',"noctua_d_history_guard('[]','[]')"]:
   self.assertIn('permission denied',f.execute('SELECT public.'+call+';',f.A,fails=True))
 def test_02_getter_generated_equivalence_null_scalar_duplicate_and_collation(self):
  rng=random.Random(132);cases=[]
  for value in [None,0,False,'text',[],{},[None],[1]]:
   cases.append((value,'students',{'id':'x'}))
  for i in range(400):
   kind=rng.choice(['students','classes','STUDENTS','ação','']);key=rng.choice([{'id':'x'},{'id':1},None,{},['x']]);payload=rng.choice([None,{},0,{'id':'x','value':None},{'value':0}])
   row={'kind':kind,'key':key,'payload':payload}
   rows=[row]*rng.choice([0,1,1,2]);cases.append((rows,kind if i%3 else 'other',key))
   if kind and isinstance(key,dict):cases.append(({kind:{json.dumps(key,ensure_ascii=False,sort_keys=True,separators=(', ',': ')):payload}},kind,key))
  for rows,kind,key in cases:
   args=','.join([f.literal(rows),f.literal(kind)+'#>>\'{}\'',f.literal(key)])
   self.assertEqual(f.execute('SELECT h132_lab.inspect_get('+args+',false);'),f.execute('SELECT h132_lab.inspect_get('+args+',true);'))
  self.assertEqual(f.execute("SELECT h132_lab.inspect_get(NULL,NULL,NULL,false);"),f.execute("SELECT h132_lab.inspect_get(NULL,NULL,NULL,true);"))
  for rows in [None,0,False,'text',[],{},[None],[dict(kind='students',key=None,payload=None)]]:
   for kind in ['NULL',"'students'"]:
    for key in ['NULL',"'null'::jsonb"]:
     args=f.literal(rows)+','+kind+','+key
     self.assertEqual(f.execute('SELECT h132_lab.inspect_get('+args+',false);'),f.execute('SELECT h132_lab.inspect_get('+args+',true);'))
  f.execute("CREATE COLLATION h132_lab.ci (provider=icu,locale='und-u-ks-level2',deterministic=false);")
  for kind in ('students','STUDENTS'):
   for candidate in ('false','true'):
    args=f.literal([dict(kind='students',key={'id':'x'},payload={'value':0})])+",'"+kind+"' COLLATE h132_lab.ci,'{\"id\":\"x\"}',"+candidate
    value=f.execute('SELECT h132_lab.inspect_get('+args+');')
    if candidate=='false':baseline=value
    else:self.assertEqual(baseline,value)
  f.execute('DROP COLLATION h132_lab.ci;');RESULTS.append(dict(case='getter-generated',probes=len(cases)+35,equal=True))
 def test_03_guard_generated_adversarial_and_legacy_fallback(self):
  push(f.d.rows());projection=json.loads(f.execute("SELECT public.noctua_d_projection('"+f.A+"');"));cases=[(projection,copy.deepcopy(projection)),([],[]),([],None),([],{}),(projection,projection+projection)]
  rng=random.Random(133)
  for i in range(120):
   after=copy.deepcopy(projection);n=rng.randrange(len(after));row=after[n]
   choice=i%6
   if choice==0:del after[n]
   elif choice==1:after.append(copy.deepcopy(row))
   elif choice==2:row['payload']['classId']='other-fixture-class'
   elif choice==3:row['payload']['personId']='other-fixture-person'
   elif choice==4:row['payload']=None
   else:row.pop(rng.choice(['key','kind','payload']))
   cases.append((projection,after))
  for before,after in cases:
   args=f.literal(before)+','+f.literal(after)
   self.assertEqual(f.execute('SELECT h132_lab.inspect_guard('+args+',false);'),f.execute('SELECT h132_lab.inspect_guard('+args+',true);'))
  RESULTS.append(dict(case='historical-guard-generated',probes=len(cases),equal=True))
 def test_06_numeric_key_equality_requires_array_lookup(self):
  push(f.d.rows());projection=json.loads(f.execute("SELECT public.noctua_d_projection('"+f.A+"');"))
  before=copy.deepcopy(projection)
  historical=next(r['payload'] for r in before if r['kind']=='preserved_histories')
  historical['content']['recompositions'][0]['term']=1.0
  args=f.literal(before)+','+f.literal(projection)
  original=f.execute('SELECT h132_lab.inspect_guard('+args+',false);')
  self.assertEqual('{"accepted": true}',original)
  self.assertEqual(original,f.execute('SELECT h132_lab.inspect_guard('+args+',true);'))
  # Arbitrary legacy nested/numeric/boolean keys also retain JSONB equality.
  for key in ({'id':1},{'id':1.0},{'id':True},{'id':{'n':1}},None):
   left=[dict(kind='classes',key=key,payload=dict(id='synthetic',year=2026))]
   args=f.literal(left)+','+f.literal(left)
   self.assertEqual(f.execute('SELECT h132_lab.inspect_guard('+args+',false);'),f.execute('SELECT h132_lab.inspect_guard('+args+',true);'))
  RESULTS.append(dict(case='numeric-jsonb-key-and-legacy-fallback',probes=6,equal=True))
 def test_04_full_bootstrap_grade_activity_movement_history_and_rollback(self):
  compare_writer('all-15-bootstrap',f.d.rows());push(f.d.rows())
  rows=f.d.rows();g=copy.deepcopy(next(r for r in rows if r['kind']=='grades'and r['payload']['studentId']=='ds2'));g['base']=1
  for v in [None,0,7,10,-1,11]:
   g['payload']['value']=v;compare_writer('grade-'+str(v),[g])
  compare_writer('stale-grade',[dict(g,base=0)])
  a=copy.deepcopy(next(r for r in rows if r['kind']=='activities'));a['base']=1;a['payload']['maximum']=7;compare_writer('activity-compatible',[a]);a['payload']['maximum']=1;compare_writer('activity-would-invalidate-grades',[a])
  audit=copy.deepcopy(next(r for r in rows if r['kind']=='relocations'));audit['base']=1;audit['payload']['corrections'][0]['previousTarget']='forged';compare_writer('immutable-movement-audit',[audit])
  h=copy.deepcopy(next(r for r in rows if r['kind']=='preserved_histories'));h['base']=1;h['payload']['content']['grades'][0]['value']=9
  compare_writer('immutable-history',[h]);g['payload']['value']=0;compare_writer('mixed-invalid-history-rollback',[g,h])
  delta=c.ROOT/'app/build/academic-phase-d-validation/source-deletion.json';self.assertTrue(delta.exists(),'Run JVM fixtures first')
  compare_writer('movement-source-to-preserved-history',json.loads(delta.read_text()))
 def test_05_frozen_receipt_replay_and_conflict_metadata(self):
  rows=f.d.rows();op,parts,_=f.begin(rows)
  for n,p in enumerate(parts):f.stage(op,n,p)
  f.execute(c.BASELINE);r=f.commit(op);before=state();f.execute(c.CANDIDATE.read_text());self.assertEqual(r,f.commit(op));self.assertEqual(before,state())
  conflict,parts,_=f.begin([rows[0]])
  for n,p in enumerate(parts):f.stage(conflict,n,p)
  f.execute(c.BASELINE);r=f.commit(conflict);self.assertFalse(r['accepted']);before=state();f.execute(c.CANDIDATE.read_text());self.assertEqual(r,f.commit(conflict));self.assertEqual(before,state())
  RESULTS.append(dict(case='confirmed-and-conflict-receipts',equal=True))

if __name__=='__main__':
 import sys
 suite=unittest.defaultTestLoader.loadTestsFromTestCase(H132Test)
 result=unittest.TextTestRunner(verbosity=2).run(suite)
 telemetry.event(stage='finished',completed=result.wasSuccessful(),measurement=dict(tests=result.testsRun,failures=len(result.failures),errors=len(result.errors)),profile=None)
 (c.ROOT/'build/h132/focused.json').write_text(json.dumps(dict(tests=result.testsRun,failures=len(result.failures),errors=len(result.errors),skipped=len(result.skipped),comparisons=RESULTS,hosted_executed=False),indent=2)+'\n')
 f.execute(c.BASELINE);b.reset();f.execute('DROP SCHEMA h132_lab CASCADE;')
 sys.exit(0 if result.wasSuccessful()else 1)
