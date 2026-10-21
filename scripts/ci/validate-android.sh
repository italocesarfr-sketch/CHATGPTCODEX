#!/usr/bin/env bash
# Testes/capturas apenas no emulador descartável do Actions.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${CI:-}" == true ]] || { echo 'Somente no CI.' >&2; exit 1; }
[[ "$(adb get-serialno)" == emulator-* ]] || { echo 'Somente em emulador descartável.' >&2; exit 1; }
status=0
mkdir -p build
# Falhar cedo se o novo controle compacto não couber. A regressão completa continua obrigatória.
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
timeout --signal=INT --kill-after=10s 2m adb shell am instrument -w -e timeout_msec 90000 -e class br.com.diariodeclasse.FinalUxLayoutInstrumentedTest#reportActionsHaveEqualBoundsOutlineAndAccessibleUntruncatedLabels br.com.diariodeclasse.test/androidx.test.runner.AndroidJUnitRunner > build/ux14-layout-smoke.txt || status=$?
cat build/ux14-layout-smoke.txt
python3 - <<'PYSMOKE' || status=1
from pathlib import Path
text=Path('build/ux14-layout-smoke.txt').read_text()
assert 'OK (1 test)' in text and 'FAILURES' not in text and 'INSTRUMENTATION_FAILED' not in text
PYSMOKE
if (( status == 0 )); then
    timeout --signal=INT --kill-after=10s 4m adb shell am instrument -w -e timeout_msec 90000 -e class br.com.diariodeclasse.RelocationConversionInstrumentedTest,br.com.diariodeclasse.FunctionalFifteenInstrumentedTest,br.com.diariodeclasse.FunctionalFifteenUiInstrumentedTest,br.com.diariodeclasse.ExportLayoutInstrumentedTest#migrationFromDistributedV3PreservesEverySchoolTableAndMovement,br.com.diariodeclasse.HistoryDeletionInstrumentedTest#distributedSchemaSixMigratesAdditivelyWithoutChangingAnyOfItsThirteenTables,br.com.diariodeclasse.RelocationUiInstrumentedTest#confirmedMoveHidesSourceAndRefreshesDestinationReportsAcrossRecreation,br.com.diariodeclasse.RecompositionUiInstrumentedTest#reportsRefreshWithoutAddingRecompositionToActivitiesOrFourthTerm br.com.diariodeclasse.test/androidx.test.runner.AndroidJUnitRunner > build/functional15-focused.txt || status=$?
    cat build/functional15-focused.txt
    python3 - <<'PYFOCUSED' || status=1
from pathlib import Path
text=Path('build/functional15-focused.txt').read_text()
assert 'OK (35 tests)' in text and 'FAILURES' not in text and 'INSTRUMENTATION_FAILED' not in text
PYFOCUSED
fi
if (( status == 0 )); then
    timeout --signal=INT --kill-after=20s 9m ./gradlew --no-daemon --stacktrace -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true -Pandroid.testInstrumentationRunnerArguments.timeout_msec=90000 connectedDebugAndroidTest || status=$?
fi
# Somente fixture no emulador descartável; diagnóstico de automação sem tocar no banco.
adb logcat -d -s TestRunner:V AndroidRuntime:E '*:S' > build/android-runner-diagnostic.txt || true
if adb shell run-as br.com.diariodeclasse test -d files/ux14-screenshots; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/ux14-screenshots > build/ux14-telas.tar
fi
if adb shell run-as br.com.diariodeclasse test -d files/ux12-screenshots; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/ux12-screenshots > build/ux12-telas.tar
fi
if adb shell run-as br.com.diariodeclasse test -d files/prompt2-screenshots; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/prompt2-screenshots > build/prompt2-telas.tar
fi
if adb shell run-as br.com.diariodeclasse test -d files/brand-screenshots; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/brand-screenshots > build/noctua-marca-telas.tar
fi
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
if adb shell run-as br.com.diariodeclasse test -d files/etapa6-screenshots; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/etapa6-screenshots > build/etapa6-telas.tar
fi
if adb shell run-as br.com.diariodeclasse test -d files/etapa53-screenshots; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/etapa53-screenshots > build/etapa53-telas.tar
fi
if adb shell run-as br.com.diariodeclasse test -d files/etapa52-planilhas; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/etapa52-planilhas > build/etapa52-planilhas.tar
fi
if adb shell run-as br.com.diariodeclasse test -d files/functional15-screenshots; then
    adb exec-out run-as br.com.diariodeclasse tar -cf - files/functional15-screenshots > build/functional15-telas.tar
fi
if (( status != 0 )); then
    python3 - <<'PYFAILURES'
from pathlib import Path
import xml.etree.ElementTree as E
for path in Path('app/build/outputs/androidTest-results').rglob('TEST-*.xml'):
    for case in E.parse(path).getroot().findall('testcase'):
        failure=case.find('failure')
        if failure is not None:
            print('FAILURE_CASE:',case.get('classname'),case.get('name'))
            print(failure.text)
PYFAILURES
    exit "$status"
fi
python3 - <<'PY'
import tarfile
with tarfile.open('build/ux14-telas.tar') as images:
    assert len([x for x in images.getmembers() if x.name.endswith('.png')]) >= 6
with tarfile.open('build/ux12-telas.tar') as images:
    assert len([x for x in images.getmembers() if x.name.endswith('.png')]) >= 13
with tarfile.open('build/prompt2-telas.tar') as images:
    assert len([x for x in images.getmembers() if x.name.endswith('.png')]) >= 4
with tarfile.open('build/noctua-marca-telas.tar') as images:
    assert len([x for x in images.getmembers() if x.name.endswith('.png')]) >= 5
print('Marca oficial: capturas de máscaras, launcher, tela inicial, configurações e cabeçalho coletadas.')
with tarfile.open('build/etapa2-telas.tar') as images:
    assert len([x for x in images.getmembers() if x.name.endswith('.png')]) >= 4
print('Capturas sintéticas das quatro páginas coletadas.')
with tarfile.open('build/etapa4a-telas.tar') as images:
    assert len([x for x in images.getmembers() if x.name.endswith('.png')]) >= 3
print('Capturas da lista, exclusão e recálculo coletadas.')
with tarfile.open('build/etapa51-telas.tar') as images:
    assert len([x for x in images.getmembers() if x.name.endswith('.png')]) >= 3
print('Capturas da ocultação, disposição e remanejamento coletadas.')
with tarfile.open('build/etapa6-telas.tar') as images:
    assert len([x for x in images.getmembers() if x.name.endswith('.png')]) >= 6
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
