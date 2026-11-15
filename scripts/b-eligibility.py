#!/usr/bin/env python3
"""One protected B login, one read-only status RPC, one own-session logout. No APK."""
import base64,hashlib,http.client,importlib.util,json,os,re,ssl,time,uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('shared_auth',ROOT/'scripts/hosted-auth-checks.py');auth=importlib.util.module_from_spec(spec);spec.loader.exec_module(auth)
HOST=auth.TARGET+'.supabase.co'
REPORT=ROOT/'build/b-eligibility/result.json'
ROUTES={'LOGIN_B':'/auth/v1/token?grant_type=password','STATUS_B':'/rest/v1/rpc/noctua_academic_status_c','LOGOUT':'/auth/v1/logout?scope=local'}
LIMIT=262144
ERRORS={'ROUTE','TIME_LIMIT','RESPONSE_LIMIT','HTTP_CODE','REDIRECT_REFUSED','HTTP_STATUS','RESPONSE_JSON','REQUEST_NOT_ALLOWED','AUTH_SHAPE','AUTH_TOKEN','AUTH_USER','AUTH_IDENTITY','AUTH_CONFIRMED_REQUIRED','AUTH_CLAIMS','AUTH_SESSION','AUTH_EXPIRED','OWN_SESSION_REQUIRED','STATUS_SHAPE','STATUS_FIELDS_REQUIRED'}
class Blocked(Exception):pass
def need(value,code):
 if not value:raise Blocked(code)
class Credentials:
 def __init__(self,key,account):self.key=key;self.account=account
 def __repr__(self):return 'Credentials(REDACTED)'
def context(env):
 auth.scope(env)
 need(env.get('RUNNER_ENVIRONMENT')=='github-hosted','DISPOSABLE_RUNNER_REQUIRED')
 mode=env.get('NOCTUA_B_ELIGIBILITY_MODE','PREFLIGHT');need(mode in ('PREFLIGHT','HOSTED'),'MODE')
 if mode=='HOSTED':
  need(env.get('NOCTUA_B_ELIGIBILITY_AUTHORIZED')=='YES','B_AUTHORIZATION_REQUIRED')
  need(env.get('GITHUB_RUN_ATTEMPT')=='1','NO_AUTOMATIC_REPLAY')
 return mode
def configuration(env):
 gradle=(ROOT/'app/build.gradle.kts').read_text();models=(ROOT/'app/src/main/java/br/com/diariodeclasse/auth/AuthModels.kt').read_text()
 production=re.search(r'val productionPublishable = "([^"]+)"',gradle).group(1)
 legacy=re.search(r'const val LEGACY_OWNER_ID = "([^"]+)"',models).group(1)
 data=auth.validate(env.get('NOCTUA_HOMOLOGACAO_PUBLISHABLE_KEY',''),env.get('NOCTUA_HOMOLOGACAO_AUTH_TEST_JSON',''),production,legacy,require_zero_probe=False)
 return Credentials(env['NOCTUA_HOMOLOGACAO_PUBLISHABLE_KEY'],data['accounts'][1]) # B only; A never transmitted.
def observation(status):
 need(isinstance(status,dict),'STATUS_SHAPE')
 need(type(status.get('protocol'))is int and type(status.get('academicScope'))is int and type(status.get('testAllowed'))is bool,'STATUS_FIELDS_REQUIRED')
 return dict(protocol={1:'V1',2:'V2'}.get(status['protocol'],'OTHER'),scope={2:'B',3:'C',4:'D',5:'E'}.get(status['academicScope'],'OTHER'),test_allowed=status['testAllowed'],ready=status['protocol']==2 and status['academicScope']==5 and status['testAllowed'])
class Client:
 def __init__(self,credentials):
  self.credentials=credentials;self.calls={k:0 for k in ROUTES};self.http={k:None for k in ROUTES};self.blocked=0;self._access=None;self.authenticated=False
 def _exchange(self,path,body,headers):
  # No configurable origin, proxy, method, redirects or retry. Hostname + certificate verified.
  need(path in ROUTES.values(),'ROUTE')
  ctx=ssl.create_default_context();ctx.minimum_version=ssl.TLSVersion.TLSv1_2
  connection=http.client.HTTPSConnection(HOST,timeout=10,context=ctx)
  deadline=time.monotonic()+15
  try:
   connection.request('POST',path,body=json.dumps(body,separators=(',',':')).encode(),headers=headers)
   response=connection.getresponse();raw=bytearray()
   while True:
    remaining=deadline-time.monotonic();need(remaining>0,'TIME_LIMIT')
    if connection.sock is not None:connection.sock.settimeout(min(10,remaining))
    part=response.read1(8192)
    if not part:break
    need(len(raw)+len(part)<=LIMIT,'RESPONSE_LIMIT');raw.extend(part)
   return response.status,bytes(raw)
  finally:connection.close()
 def _request(self,step,body):
  if step not in ROUTES or self.calls.get(step,1)!=0:
   self.blocked+=1;raise Blocked('REQUEST_NOT_ALLOWED')
  if step=='LOGIN_B':allowed=self._access is None and body=={k:self.credentials.account[k]for k in ('email','password')}
  else:allowed=self._access is not None and body=={} and (step=='LOGOUT' or self.authenticated)
  if not allowed:self.blocked+=1;raise Blocked('REQUEST_NOT_ALLOWED')
  self.calls[step]+=1
  headers={'apikey':self.credentials.key,'Content-Type':'application/json','Accept':'application/json'}
  if step!='LOGIN_B':headers['Authorization']='Bearer '+self._access
  code,raw=self._exchange(ROUTES[step],body,headers)
  need(type(code)is int and 100<=code<=599,'HTTP_CODE');self.http[step]=code
  need(not 300<=code<=399,'REDIRECT_REFUSED');need(200<=code<=299,'HTTP_STATUS')
  need(isinstance(raw,bytes)and len(raw)<=LIMIT,'RESPONSE_LIMIT')
  if not raw:return {}
  try:return json.loads(raw)
  except Exception:raise Blocked('RESPONSE_JSON')from None
 def login(self):
  data=self._request('LOGIN_B',{k:self.credentials.account[k]for k in ('email','password')})
  need(isinstance(data,dict),'AUTH_SHAPE');access=data.get('access_token')
  need(isinstance(access,str)and len(access)<=16384 and bool(re.fullmatch(r'[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+',access)),'AUTH_TOKEN')
  self._access=access # Retain only this freshly created session, even if the identity check fails.
  user=data.get('user');need(isinstance(user,dict),'AUTH_USER')
  need(user.get('id')==self.credentials.account['expectedId'] and str(user.get('email','')).lower()==self.credentials.account['email'].lower(),'AUTH_IDENTITY')
  need(isinstance(user.get('email_confirmed_at'),str)and bool(user['email_confirmed_at'])and user.get('is_anonymous')is False,'AUTH_CONFIRMED_REQUIRED')
  try:
   encoded=access.split('.')[1];claims=json.loads(base64.urlsafe_b64decode(encoded+'='*((-len(encoded))%4)))
   need(isinstance(claims,dict)and claims.get('iss')=='https://'+HOST+'/auth/v1'and claims.get('sub')==user['id']and claims.get('role')=='authenticated','AUTH_CLAIMS')
   need(str(uuid.UUID(claims.get('session_id','')))==claims['session_id'],'AUTH_SESSION')
   need(type(claims.get('exp'))is int and claims['exp']>time.time(),'AUTH_EXPIRED')
  except Blocked:raise
  except Exception:raise Blocked('AUTH_CLAIMS')from None
  # The token comes from authenticated HTTPS login, not from caller input. No claims are published.
  self.authenticated=True
 def status(self):return observation(self._request('STATUS_B',{}))
 def logout(self):
  need(self._access is not None,'OWN_SESSION_REQUIRED');self._request('LOGOUT',{});self._access=None

def diagnose(client):
 start=time.monotonic();stage='LOGIN_B';failure=None;observed=None;times={};logged_out=False;created=False
 try:
  t=time.monotonic();client.login();times['LOGIN_B']=round((time.monotonic()-t)*1000);created=True
  stage='STATUS_B';t=time.monotonic();observed=client.status();times['STATUS_B']=round((time.monotonic()-t)*1000)
 except Exception as e:failure=str(e)if isinstance(e,Blocked)and str(e)in ERRORS else 'TRANSPORT_FAILED'
 finally:
  created=created or client._access is not None
  if client._access is not None:
   try:
    t=time.monotonic();client.logout();times['LOGOUT']=round((time.monotonic()-t)*1000);logged_out=True
   except Exception:
    if failure is None:failure='OWN_LOGOUT_FAILED';stage='LOGOUT'
 if client.blocked and failure is None:failure='REQUEST_NOT_ALLOWED'
 result=dict(overall='BLOCKED'if failure else'DIAGNOSED_ELIGIBLE'if observed['ready']else'DIAGNOSED_NOT_ELIGIBLE',stage=stage if failure else'DONE',blocking_code=failure,
  eligibility=observed,authenticated=client.authenticated,session_created_observed=created,own_session_logout_confirmed=logged_out,
  requests=dict(client.calls),http_status=dict(client.http),blocked_requests=client.blocked,academic_mutation_requests=0,
  total_ms=round((time.monotonic()-start)*1000),timings_ms=times)
 return result

def publish(env,**result):
 value=dict(case='B_ELIGIBILITY',project_ref=auth.TARGET,account='B',mode=env.get('NOCTUA_B_ELIGIBILITY_MODE','PREFLIGHT')if env.get('NOCTUA_B_ELIGIBILITY_MODE','PREFLIGHT')in ('PREFLIGHT','HOSTED')else None,
  source_sha=env.get('GITHUB_SHA')if re.fullmatch('[0-9a-f]{40}',env.get('GITHUB_SHA',''))else None,
  credentials_redacted=True,**result)
 REPORT.parent.mkdir(parents=True,exist_ok=True);REPORT.write_text(json.dumps(value,indent=2)+'\n')
 if env.get('GITHUB_STEP_SUMMARY'):Path(env['GITHUB_STEP_SUMMARY']).write_text('## B_ELIGIBILITY — '+value['overall']+'\n\nOne status query only; no account identifiers or academic payloads.\n')
def main():
 env=os.environ;started=False
 try:
  mode=context(env);credentials=configuration(env)
  if mode=='PREFLIGHT':publish(env,overall='PREREQUISITES_READY_NOT_EXECUTED',hosted_requests=False);return
  marker=auth.private_dir(env).parent/'noctua-b-eligibility-once'
  fd=os.open(marker,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600);os.close(fd) # No private content; prevents a second call in the same job.
  publish(env,overall='ATTEMPT_STARTED',hosted_requests=None)
  started=True;result=diagnose(Client(credentials));publish(env,hosted_requests=True,**result)
  if result['overall']=='BLOCKED':raise SystemExit(1)
 except SystemExit:raise
 except Exception as e:
  code=str(e)if isinstance(e,(Blocked,auth.Blocked))and re.fullmatch('[A-Z0-9_]{1,80}',str(e))else'UNEXPECTED'
  publish(env,overall='BLOCKED',blocking_code=code,hosted_requests=None if started else False)
  print('B_ELIGIBILITY_BLOCKED_'+code);raise SystemExit(1)from None
if __name__=='__main__':main()
