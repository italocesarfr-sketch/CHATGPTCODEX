#!/usr/bin/env bash
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true && "${PGHOST:-}" == 127.0.0.1 && "${PGDATABASE:-}" == noctua_phase_b_test ]] || exit 1
mkdir -p build/phaseb-postgrest
python3 - <<'PY'
import urllib.request,hashlib,tarfile
from pathlib import Path
p=Path('build/phaseb-postgrest/postgrest.tar.xz')
with urllib.request.urlopen('https://github.com/PostgREST/postgrest/releases/download/v12.2.3/postgrest-v12.2.3-linux-static-x64.tar.xz') as r:p.write_bytes(r.read())
assert hashlib.sha256(p.read_bytes()).hexdigest()=='9f71269e61ac3a940281e93ff415760f5957e430e475ba4c3889f3ede7d5527c'
with tarfile.open(p) as archive:
    for member in archive.getmembers():
        assert member.name.lstrip('./')=='postgrest' and member.isfile()
        output=p.parent/'postgrest';output.write_bytes(archive.extractfile(member).read());output.chmod(0o755)
PY
python3 scripts/phase-b-http-fixture.py
PGRST_DB_URI=postgresql://postgrest_fixture:local-fixture-only@127.0.0.1:5432/noctua_phase_b_test \
PGRST_DB_SCHEMAS=public PGRST_DB_ANON_ROLE=anon PGRST_LOG_LEVEL=error \
PGRST_JWT_SECRET=noctua-phase-b-disposable-jwt-secret-2026-only PGRST_SERVER_PORT=3000 \
nohup build/phaseb-postgrest/postgrest > build/phaseb-postgrest/server.log 2>&1 </dev/null &
echo "$!" > build/phaseb-postgrest/server.pid
python3 - <<'PY'
import urllib.request,urllib.error,time
for _ in range(30):
    try:
        urllib.request.urlopen('http://127.0.0.1:3000',timeout=1);break
    except urllib.error.HTTPError as error:
        if error.code!=503:raise
        time.sleep(1) # PostgREST schema cache is not ready; this is not a successful health check.
    except OSError:time.sleep(1)
else:raise SystemExit('Disposable PostgREST failed to start')
PY
python3 scripts/test-phase-b-http.py | tee build/phaseb-http.txt
