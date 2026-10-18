#!/usr/bin/env bash
# Testes/capturas apenas no emulador descartável do Actions.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true ]] || { echo 'Somente no CI.' >&2; exit 1; }
[[ "$(adb get-serialno)" == emulator-* ]] || { echo 'Somente em emulador descartável.' >&2; exit 1; }
status=0
./gradlew --no-daemon --stacktrace -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true connectedDebugAndroidTest || status=$?
mkdir -p build
if adb shell run-as br.com.diariodeclasse test -d files/etapa2-screenshots; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/etapa2-screenshots > build/etapa2-telas.tar
fi
if adb shell run-as br.com.diariodeclasse test -d files/etapa3-screenshots; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/etapa3-screenshots > build/etapa3-telas.tar
fi
if adb shell run-as br.com.diariodeclasse test -d files/etapa4a-screenshots; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/etapa4a-screenshots > build/etapa4a-telas.tar
fi
if adb shell run-as br.com.diariodeclasse test -d files/etapa4b1-screenshots; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/etapa4b1-screenshots > build/etapa4b1-telas.tar
fi
if adb shell run-as br.com.diariodeclasse test -d files/etapa4b2-screenshots; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/etapa4b2-screenshots > build/etapa4b2-telas.tar
fi
if adb shell run-as br.com.diariodeclasse test -d files/etapa4b3-screenshots; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/etapa4b3-screenshots > build/etapa4b3-telas.tar
fi
if adb shell run-as br.com.diariodeclasse test -d files/etapa4c-screenshots; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/etapa4c-screenshots > build/etapa4c-telas.tar
fi
if adb shell run-as br.com.diariodeclasse test -d files/etapa51-screenshots; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/etapa51-screenshots > build/etapa51-telas.tar
fi
if adb shell run-as br.com.diariodeclasse test -d files/etapa52-screenshots; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/etapa52-screenshots > build/etapa52-telas.tar
fi
if adb shell run-as br.com.diariodeclasse test -d files/etapa53-screenshots; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/etapa53-screenshots > build/etapa53-telas.tar
fi
if adb shell run-as br.com.diariodeclasse test -d files/etapa52-planilhas; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/etapa52-planilhas > build/etapa52-planilhas.tar
fi
if (( status != 0 )); then exit "$status"; fi
python3 - <<'PY'
import tarfile
with tarfile.open('build/etapa2-telas.tar') as images:
    assert len([x for x in images.getmembers() if x.name.endswith('.png')]) >= 4
print('Capturas sintéticas das quatro páginas coletadas.')
with tarfile.open('build/etapa4a-telas.tar') as images:
    assert len([x for x in images.getmembers() if x.name.endswith('.png')]) >= 3
print('Capturas da lista, exclusão e recálculo coletadas.')
with tarfile.open('build/etapa51-telas.tar') as images:
    assert len([x for x in images.getmembers() if x.name.endswith('.png')]) >= 3
print('Capturas da ocultação, disposição e remanejamento coletadas.')
with tarfile.open('build/etapa53-telas.tar') as images:
    assert len([x for x in images.getmembers() if x.name.endswith('.png')]) >= 3
with tarfile.open('build/etapa52-telas.tar') as images:
    assert len([x for x in images.getmembers() if x.name.endswith('.png')]) >= 4
from pathlib import Path
with tarfile.open('build/etapa52-planilhas.tar') as files:
    for entry in files.getmembers():
        if not entry.isfile(): continue
        assert not Path(entry.name).is_absolute() and '..' not in Path(entry.name).parts
        target=Path('build/android-export-fixtures')/entry.name
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(files.extractfile(entry).read())
print('Planilhas produzidas pelo Android e quatro capturas coletadas.')
PY
python3 scripts/validate-spreadsheets.py build/android-export-fixtures/files/etapa52-planilhas
for phase in write read; do
    if [[ "$phase" == read ]]; then adb shell am force-stop br.com.diariodeclasse; fi
    adb shell am instrument -w -e frequencyProbePhase "$phase" -e class br.com.diariodeclasse.FrequencyProcessProbeTest br.com.diariodeclasse.test/androidx.test.runner.AndroidJUnitRunner > "build/frequency-process-$phase.txt"
    python3 - "$phase" <<'PYPROBE'
import sys
from pathlib import Path
text=Path('build/frequency-process-'+sys.argv[1]+'.txt').read_text()
assert 'OK (1 test)' in text and 'FAILURES' not in text and 'INSTRUMENTATION_FAILED' not in text
print('Sonda de frequência/reinício aprovada:',sys.argv[1])
PYPROBE
done
./scripts/ci/test-upgrade.sh
# A atualização release agora reutiliza a chave existente no job stable-update.
# Não executar aqui o ensaio histórico que gerava novas chaves descartáveis.
