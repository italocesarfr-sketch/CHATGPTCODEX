#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true && "${PGHOST:-}" == 127.0.0.1 && "${PGPORT:-}" == 55431 && "${PGDATABASE:-}" == noctua_phase_e_test && "${NOCTUA_COMMERCIAL_DISPOSABLE:-}" == YES ]] || exit 1
mkdir -p build/commercial-tests
python3 - <<'PY'
import urllib.request,hashlib,tarfile
from pathlib import Path
p=Path('build/commercial-tests/postgrest.tar.xz')
with urllib.request.urlopen('https://github.com/PostgREST/postgrest/releases/download/v12.2.3/postgrest-v12.2.3-linux-static-x64.tar.xz',timeout=60) as r:p.write_bytes(r.read())
assert hashlib.sha256(p.read_bytes()).hexdigest()=='9f71269e61ac3a940281e93ff415760f5957e430e475ba4c3889f3ede7d5527c'
with tarfile.open(p) as a:
    for m in a.getmembers():
        assert m.name.lstrip('./')=='postgrest' and m.isfile()
        out=p.parent/'postgrest';out.write_bytes(a.extractfile(m).read());out.chmod(0o755)
PY
python3 scripts/test-commercial-http.py --prepare
PGRST_DB_URI=postgresql://postgrest_fixture:local-fixture-only@127.0.0.1:55431/noctua_phase_e_test \
 PGRST_DB_SCHEMAS=public PGRST_DB_ANON_ROLE=anon PGRST_LOG_LEVEL=error \
 PGRST_JWT_SECRET=noctua-phase-b-disposable-jwt-secret-2026-only PGRST_SERVER_HOST=127.0.0.1 PGRST_SERVER_PORT=3000 \
 nohup build/commercial-tests/postgrest > build/commercial-tests/postgrest.log 2>&1 </dev/null &
printf '%s\n' "$!" > build/commercial-tests/postgrest.pid
python3 - <<'PY'
import urllib.request,urllib.error,time
for _ in range(50):
    try:urllib.request.urlopen('http://127.0.0.1:3000',timeout=1);break
    except OSError:time.sleep(.2)
else:raise SystemExit('Disposable PostgREST did not become ready')
PY
NOCTUA_COMMERCIAL_HTTP_PORT=3000 python3 scripts/test-commercial-http.py > build/commercial-tests/http-results.json
