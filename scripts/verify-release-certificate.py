import os,sys
from apk_identity import identity
expected=os.environ['EXPECTED_CERT'].replace(':','').lower()
actual=identity(sys.argv[1])['certificateSha256']
if not expected or actual!=expected: raise SystemExit('Certificado divergente: APK não será publicado.')
print('Certificado público corresponde ao fingerprint fixado.')
