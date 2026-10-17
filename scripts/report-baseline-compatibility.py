import json,sys
from pathlib import Path
from apk_identity import identity
baseline=json.loads(Path('docs/baseline/versao-0.1.0.json').read_text())
current=identity(sys.argv[1])
print(json.dumps({'baselineCertificateSha256':baseline['certificateSha256'],'candidate':current,'sameCertificate':baseline['certificateSha256']==current['certificateSha256'],'scope':'Compara o APK de referência versionado, não identifica o APK instalado no celular. Debug é somente para teste; diferenças bloqueiam atualização direta.'},ensure_ascii=False,indent=2))
