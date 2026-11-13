"""Shared H13.2 fixture selection and original function sources; LOCAL ONLY."""
import importlib.util,json,os
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
CANDIDATE=ROOT/'supabase/migrations/202611130002_h132_structural_history.sql'
def load(name,file):
 spec=importlib.util.spec_from_file_location(name,ROOT/file);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
def function(file,name):
 source=(ROOT/file).read_text();start=source.index('CREATE OR REPLACE FUNCTION public.'+name+'(')
 marker='END $$;'if name=='noctua_d_history_guard'else'$$;'
 return source[start:source.index(marker,start)+len(marker)]
OLD_GET=function('supabase/migrations/202611030003_phase_e_indexed_graph.sql','noctua_d_get')
OLD_GUARD=function('supabase/migrations/202611020003_phase_d_scope.sql','noctua_d_history_guard')
OLD_GRAPH=function('supabase/migrations/202611030003_phase_e_indexed_graph.sql','noctua_d_graph')
BASELINE=OLD_GET+'\n'+OLD_GUARD+'\n'+OLD_GRAPH

def fixtures(mode='sql'):
 expected='noctua_phase_e_test'if mode=='sql'else'noctua_phase_b_test'
 assert os.environ.get('PGDATABASE')==expected and os.environ.get('PGHOST')in('127.0.0.1','localhost','/workspace/pg-local'),'Disposable loopback database only'
 # Base constructors reapply SQL on import. Never import them into the running HTTP DB.
 if mode=='http':os.environ['PGDATABASE']='noctua_phase_e_test'
 b=load('h132_base','scripts/test-phase-b-postgres.py');b.reset()
 f=load('h132_e','scripts/test-phase-e-postgres.py')
 bench=load('h132_h13','scripts/benchmark-h13.py')
 lab=load('h132_fixtures','scripts/h131/investigate.py')
 if mode=='http':os.environ['PGDATABASE']=expected
 return b,f,bench,lab

def metadata(f):
 return json.loads(f.execute("""SELECT jsonb_build_object(
 'functions',(SELECT jsonb_agg(jsonb_build_object('name',p.oid::regprocedure::text,'owner',p.proowner,'definer',p.prosecdef,'volatility',p.provolatile,'strict',p.proisstrict,'parallel',p.proparallel,'cost',p.procost,'config',p.proconfig,'acl',p.proacl) ORDER BY p.oid::regprocedure::text) FROM pg_proc p WHERE p.pronamespace='public'::regnamespace AND p.proname LIKE 'noctua_%'),
 'policies',(SELECT jsonb_agg(to_jsonb(p) ORDER BY tablename,policyname) FROM pg_policies p WHERE schemaname='public'),
 'rls',(SELECT jsonb_agg(jsonb_build_array(relname,relrowsecurity,relforcerowsecurity,relacl) ORDER BY relname) FROM pg_class WHERE relnamespace='public'::regnamespace AND relname LIKE 'noctua_%' AND relkind='r'),
 'timeout',current_setting('statement_timeout'));
 """))

def bodies(f):
 return json.loads(f.execute("SELECT jsonb_object_agg(oid::regprocedure::text,md5(prosrc)) FROM pg_proc WHERE pronamespace='public'::regnamespace AND proname LIKE 'noctua_%' AND proname NOT IN('noctua_d_get','noctua_d_history_guard','noctua_d_graph');"))

def state(f):
 import hashlib
 values=[]
 for name in ('academic_records','academic_versions','academic_batches','academic_receipts','academic_links','academic_uniques','academic_heads','e_parts','e_transfers','e_decisions'):
  values.append(f.execute("SELECT coalesce(string_agg(v::text,E'\\n' ORDER BY v::text),'') FROM (SELECT to_jsonb(t) v FROM public.noctua_"+name+" t) q;",f.A))
 return hashlib.sha256('\n'.join(values).encode()).hexdigest()


def lookup_source(key):
 # String-only JSONB keys have canonical text identity. Other keys must retain
 # original JSONB equality (e.g. numeric 1 == 1.0, although text differs).
 return ("CASE WHEN jsonb_typeof("+key+")='object' THEN CASE WHEN NOT EXISTS(SELECT FROM jsonb_each("+key+") WHERE jsonb_typeof(value) IS DISTINCT FROM 'string') THEN lookup_rows ELSE p_after END ELSE p_after END")
