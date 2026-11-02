#!/usr/bin/env python3
"""Actual disposable PostgREST HTTP; signed synthetic JWTs, NOT hosted Supabase Auth."""
import json,time,hmac,hashlib,base64,urllib.request,urllib.error,uuid
BASE='http://127.0.0.1:3000'
A='11111111-1111-4111-8111-111111111111';B='22222222-2222-4222-8222-222222222222'
SECRET='noctua-phase-b-disposable-jwt-secret-2026-only'
def token(owner,sid):
    b64=lambda v:base64.urlsafe_b64encode(v).decode().rstrip('=')
    body=b64(b'{"alg":"HS256","typ":"JWT"}')+'.'+b64(json.dumps(dict(sub=owner,role='authenticated',session_id=sid,exp=int(time.time())+3600)).encode())
    return body+'.'+b64(hmac.new(SECRET.encode(),body.encode(),hashlib.sha256).digest())
TOKENS=[token(A,'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'),token(B,'cccccccc-cccc-4ccc-8ccc-cccccccccccc')]
def request(path,body=None,jwt=None,method='POST',expected=200):
    headers={'Content-Type':'application/json'}
    if jwt:headers['Authorization']='Bearer '+jwt
    r=urllib.request.Request(BASE+path,data=json.dumps(body or {}).encode() if method=='POST' else None,headers=headers,method=method)
    try:
        with urllib.request.urlopen(r,timeout=20) as response:code=response.status;raw=response.read()
    except urllib.error.HTTPError as e:code=e.code;raw=e.read()
    assert code==expected,(path,code,expected) # Never print JWTs or academic bodies.
    return json.loads(raw) if raw else None
def run():
    for t in TOKENS:
        status=request('/rpc/noctua_academic_status',jwt=t);assert status==dict(protocol=1,v1Sequence=0,v1=[],testAllowed=True)
    request('/rpc/noctua_academic_status',expected=401)
    request('/rpc/noctua_academic_status',jwt='invalid-not-a-jwt',expected=401)
    # Correctly signed but absent/revoked Auth session is rejected by the SQL guard.
    request('/rpc/noctua_academic_status',jwt=token(A,str(uuid.uuid4())),expected=403)
    for table in ('noctua_academic_records','noctua_academic_versions','noctua_sync_protocols','noctua_academic_test_accounts'):
        request('/'+table,jwt=TOKENS[0],method='DELETE',expected=403)
    print('9 disposable HTTP/security checks passed; fixture JWTs only; no hosted account validated.')
if __name__=='__main__':run()
