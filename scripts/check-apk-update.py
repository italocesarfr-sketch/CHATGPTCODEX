#!/usr/bin/env python3
"""Somente leitura: verifica pacote/versão/certificado. NUNCA instala ou desinstala."""
import argparse,json,subprocess,sys
from pathlib import Path
from apk_identity import identity
p=argparse.ArgumentParser();p.add_argument('old');p.add_argument('new');p.add_argument('--build-tools',required=True);args=p.parse_args()
bin=Path(args.build_tools)
def metadata(path):
    subprocess.run([str(bin/'apksigner'),'verify',path],check=True,capture_output=True)
    import re
    text=subprocess.run([str(bin/'aapt'),'dump','badging',path],check=True,capture_output=True,text=True).stdout
    package=re.search(r"package: name='([^']+)' versionCode='(\d+)'",text)
    if not package: raise SystemExit('Não foi possível consultar o pacote')
    return dict(identity(path),applicationId=package[1],versionCode=int(package[2]))
a,b=metadata(args.old),metadata(args.new)
result={'old':a,'new':b,'samePackage':a['applicationId']==b['applicationId'],'sameCertificate':a['certificateSha256']==b['certificateSha256'],'higherVersion':b['versionCode']>a['versionCode']}
print(json.dumps(result,indent=2))
if not all(result[x] for x in ('samePackage','sameCertificate','higherVersion')):
    print('Atualização direta NÃO comprovada. Não desinstale a versão existente; preserve/resgate os dados primeiro.',file=sys.stderr)
    sys.exit(2)
print('Identidade compatível. A preservação dos dados ainda exige o teste de atualização da base correspondente.')
