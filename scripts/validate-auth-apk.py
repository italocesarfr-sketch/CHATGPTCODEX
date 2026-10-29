#!/usr/bin/env python3
"""Production APK must not contain any instrumentation admission path or admin key."""
import sys
import zipfile
from pathlib import Path
apk = Path(sys.argv[1])
forbidden = [b'AcademicTestApplication', b'PrincipalTestClient', b'Fixture-Password-2026',
             b'fixture-access-not-real', b'fixture-refresh-not-real', b'DemoEntryScreen',
             b'NoctuaStart', b'sb_secret_']
with zipfile.ZipFile(apk) as z:
    for name in z.namelist():
        if name.endswith('.dex'):
            data = z.read(name)
            for marker in forbidden:
                assert marker not in data, f'Prohibited test/demo/secret marker in {name}'
print('APK acadêmico sem clientes fictícios de teste, entrada demonstrativa ou chave administrativa detectada.')
