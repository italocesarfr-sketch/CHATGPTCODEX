#!/usr/bin/env python3
"""Safe hosted negative probes. No passwords/JWTs are accepted or read.
Reads no academic rows; the invalid push cannot write, and HEAD uses limit=0.
This cannot approve authenticated two-account isolation or real synchronization.
"""
import json
import re
import urllib.error
import urllib.request
import uuid
from pathlib import Path

root=Path(__file__).resolve().parents[1]
config=(root/'app/src/main/java/br/com/diariodeclasse/auth/AuthModels.kt').read_text()
origin=re.search(r'const val URL = "([^"]+)"',config)[1]
key=re.search(r'const val KEY = "([^"]+)"',config)[1]
assert origin=='https://mpsgdchpgyxoqowfjoqm.supabase.co' and key.startswith('sb_publishable_')
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):return None
opener=urllib.request.build_opener(NoRedirect())
results=[]
def probe(endpoint,body=None,invalid=False,method='POST'):
    headers={'apikey':key,'Content-Type':'application/json'}
    if invalid:headers['Authorization']='Bearer audit-invalid-not-a-jwt'
    request=urllib.request.Request(origin+'/rest/v1/'+endpoint,
        data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
    code=None
    try:
        with opener.open(request,timeout=20) as response:status=response.status
    except urllib.error.HTTPError as error:
        status=error.code
        if method!='HEAD':
            try:code=json.loads(error.read(4096)).get('code')
            except (ValueError,AttributeError):pass
    results.append(dict(endpoint=endpoint,method=method,auth='invalid-jwt' if invalid else 'public-key-only',
                        http=status,code=code,denied=status in (401,403)))

for name,body in [
    ('noctua_pull_classes',{'p_after':9223372036854775807}),
    ('noctua_sync_principal',{}),
    ('noctua_validate_class',{'p_id':'audit-invalid','p_payload':{}}),
    ('noctua_push_class',{'p_operation':str(uuid.uuid4()),'p_id':'audit-invalid','p_base':-1,'p_deleted':False,'p_payload':None})]:
    probe('rpc/'+name,body);probe('rpc/'+name,body,invalid=True)
for table in ('noctua_class_bundles','noctua_class_versions','noctua_sync_heads','noctua_sync_receipts'):
    probe(table+'?select=owner_id&limit=0',method='HEAD')
print(json.dumps(dict(scope='Hosted negative probes only; no academic rows read or written; no authenticated account tested',
                     results=results,passed=sum(r['denied'] for r in results)),ensure_ascii=False,indent=2))
raise SystemExit(0 if all(r['denied'] for r in results) else 1)
