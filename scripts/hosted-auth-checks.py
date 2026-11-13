#!/usr/bin/env python3
"""Local preflight/redacted reporting only. This helper never sends network requests."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import stat
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
TARGET = 'sscexzxtqdcsqdlcytum'
BRANCH = 'refs/heads/sync/academic-complete-0220'
REPO = 'italocesarfr-sketch/CHATGPTCODEX'
REPORT = ROOT / 'build/hosted-h01-h04/result.json'
XML = ROOT / 'app/build/test-results/testHomologacaoUnitTest/TEST-br.com.diariodeclasse.HomologationHostedAuthTest.xml'
METHODS = {
 'h01ConfirmedLoginIdentityRefreshAndWrongPassword': 'H01',
 'h02RealOrdinaryAccountCannotReadTheOtherOwnersKnownFixture': 'H02',
 'h03PrivateHelpersAndExplicitlyAuthorizedZeroRowPatchAreDenied': 'H03',
 'h04AnonymousInvalidBearerAndRevokedSessionCannotInvokeAcademicRpc': 'H04',
}
H03_STAGES = ('INPUT', 'LOGIN', 'FIXTURE_BEFORE', 'REVISION', 'HELPER', 'PATCH', 'FIXTURE_AFTER', 'COMPARE')
H03_CODES = ('STARTED', 'HTTP', 'PASSED', 'ASSERTION', 'TIMEOUT', 'RESPONSE_LIMIT', 'NETWORK', 'INTERRUPTED', 'UNEXPECTED')

class Blocked(Exception):
    pass

def require(ok, code):
    if not ok:
        raise Blocked(code)

def scope(env):
    require(env.get('GITHUB_ACTIONS') == 'true' and env.get('GITHUB_EVENT_NAME') == 'workflow_dispatch', 'MANUAL_ACTIONS_ONLY')
    require(env.get('GITHUB_REF') == BRANCH and env.get('GITHUB_REPOSITORY') == REPO, 'WRONG_REPOSITORY_OR_BRANCH')

def private_dir(env):
    base = Path(env.get('RUNNER_TEMP', '')).resolve()
    require(bool(env.get('RUNNER_TEMP')) and base.is_dir() and not base.is_relative_to(ROOT), 'PRIVATE_TEMP_REQUIRED')
    return base / 'noctua-hosted-h01-h04'

def selected_methods(selection):
    require(selection in ('ALL', 'H03'), 'INVALID_TEST_SELECTION')
    return {k:v for k,v in METHODS.items() if selection == 'ALL' or v == selection}

def validate(key, raw, production_key, legacy_owner, require_zero_probe=True):
    require(bool(key), 'PUBLISHABLE_KEY_MISSING')
    require(bool(re.fullmatch(r'sb_publishable_[A-Za-z0-9_-]{16,200}', key)) and key != production_key, 'PUBLISHABLE_KEY_INVALID_OR_PRODUCTION')
    require(bool(raw), 'ACCOUNTS_SECRET_MISSING')
    require(len(raw.encode()) <= 65536, 'ACCOUNTS_SECRET_TOO_LARGE')
    try:
        data = json.loads(raw)
        require(isinstance(data, dict) and set(data) == {'accounts', 'fixtureClassId', 'allowZeroRowPermissionProbe'}, 'ACCOUNTS_FORMAT_INVALID')
        accounts = data['accounts']
        require(isinstance(accounts, list) and len(accounts) == 2, 'EXACTLY_TWO_ACCOUNTS_REQUIRED')
        for a in accounts:
            require(isinstance(a, dict) and set(a) == {'email', 'password', 'expectedId'}, 'ACCOUNT_FORMAT_INVALID')
            require(all(isinstance(a[k], str) for k in a), 'ACCOUNT_FIELD_TYPE_INVALID')
            require(len(a['email']) <= 254 and bool(re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+', a['email'])), 'EMAIL_FORMAT_INVALID')
            require(8 <= len(a['password']) <= 4096, 'PASSWORD_FORMAT_INVALID')
            require(str(uuid.UUID(a['expectedId'])) == a['expectedId'] and a['expectedId'] != legacy_owner, 'AUTH_ID_INVALID_OR_LEGACY_OWNER')
        require(len({a['expectedId'] for a in accounts}) == 2 and len({a['email'].lower() for a in accounts}) == 2, 'ACCOUNTS_MUST_DIFFER')
        require(isinstance(data['fixtureClassId'], str) and 1 <= len(data['fixtureClassId']) <= 128 and not any(ord(c) < 32 for c in data['fixtureClassId']) and '<' not in data['fixtureClassId'], 'FIXTURE_CLASS_ID_INVALID')
        require(isinstance(data['allowZeroRowPermissionProbe'], bool), 'H03_PROBE_FLAG_INVALID')
        if require_zero_probe:
            require(data['allowZeroRowPermissionProbe'] is True, 'H03_ZERO_ROW_PROBE_NOT_CONSENTED')
        return data
    except Blocked:
        raise
    except Exception:
        raise Blocked('ACCOUNTS_FORMAT_INVALID') from None

def base_report(env):
    sha = env.get('GITHUB_SHA', '')
    return dict(project_ref=TARGET, endpoint=f'https://{TARGET}.supabase.co',
                source_sha=sha if re.fullmatch('[0-9a-f]{40}', sha) else None,
                mode=env.get('NOCTUA_HOSTED_AUTH_MODE', 'preflight'),
                selection=env.get('NOCTUA_HOSTED_AUTH_CASE', 'ALL') if env.get('NOCTUA_HOSTED_AUTH_CASE', 'ALL') in ('ALL', 'H03') else None,
                credentials_redacted=True, all_four_passed=False,
                hosted_requests_executed=False,
                cases=[dict(id=v, status='NOT_EXECUTED') for v in METHODS.values()])

def summarize(raw, exit_code, selection='ALL'):
    """Allowlist four IDs/statuses; never copy messages, stack traces, streams or IDs."""
    selected = selected_methods(selection)
    cases = {v: 'NOT_REPORTED' if v in selected.values() else 'NOT_EXECUTED' for v in METHODS.values()}
    try:
        require(len(raw) <= 2*1024*1024, 'REPORT_TOO_LARGE')
        root = ET.fromstring(raw)
        nodes = list(root.iter('testcase'))
        require(int(root.get('tests', '-1')) == len(selected), 'INCONSISTENT_TEST_COUNT')
        require(len(nodes) == len(selected) and {n.get('name') for n in nodes} == set(selected), 'EXACT_SELECTED_RESULTS_REQUIRED')
        for n in nodes:
            require(n.get('classname') == 'br.com.diariodeclasse.HomologationHostedAuthTest', 'UNEXPECTED_TEST_CLASS')
            cases[METHODS[n.get('name')]] = 'SKIPPED' if n.find('skipped') is not None else 'FAILED' if n.find('failure') is not None or n.find('error') is not None else 'PASSED'
        return [dict(id=k, status=v) for k, v in cases.items()], exit_code == 0 and all(cases[v] == 'PASSED' for v in selected.values()) and int(root.get('failures', '0')) == 0 and int(root.get('errors', '0')) == 0 and int(root.get('skipped', '0')) == 0
    except Exception:
        return [dict(id=k, status=v) for k, v in cases.items()], False

def h03_diagnostics(raw):
    """Only a fixed vocabulary and HTTP numbers, never arbitrary XML output/messages."""
    try:
        require(len(raw) <= 2*1024*1024, 'REPORT_TOO_LARGE')
        root = ET.fromstring(raw)
        pattern = re.compile(r'H03_DIAG stage=('+'|'.join(H03_STAGES)+r') status=(NONE|[1-5][0-9]{2}) code=('+'|'.join(H03_CODES)+r')')
        events = []
        for stream in root.iter('system-out'):
            for line in (stream.text or '').splitlines():
                match = pattern.fullmatch(line)
                if match and len(events) < 64:
                    stage, status, code = match.groups()
                    events.append(dict(stage=stage, status=None if status=='NONE' else int(status), code=code))
        return events
    except Exception:
        return []

def write_report(value):
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(value, indent=2)+'\n')
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary, 'w') as out:
            out.write('## H01–H04 — '+value['overall']+'\n\n')
            out.write('Projeto exclusivo: `'+TARGET+'`. Somente resumo redigido.\n\n')
            for case in value['cases']:
                out.write('- '+case['id']+': '+case['status']+'\n')
            for event in value.get('h03_diagnostics', []):
                out.write('- H03 '+event['stage']+': HTTP '+str(event['status'])+' / '+event['code']+'\n')
            if value.get('blocking_code'):
                out.write('\nBloqueio: `'+value['blocking_code']+'`. Nenhum valor privado foi incluído.\n')


def prepare(env):
    scope(env)
    mode = env.get('NOCTUA_HOSTED_AUTH_MODE', 'preflight')
    require(mode in ('preflight', 'run'), 'INVALID_MODE')
    selected_methods(env.get('NOCTUA_HOSTED_AUTH_CASE', 'ALL'))
    if mode == 'run':
        require(env.get('NOCTUA_HOMOLOGACAO_AUTH_TEST_AUTHORIZED') == 'YES', 'EXPLICIT_AUTHORIZATION_REQUIRED')
    gradle = (ROOT/'app/build.gradle.kts').read_text()
    models = (ROOT/'app/src/main/java/br/com/diariodeclasse/auth/AuthModels.kt').read_text()
    production = re.search(r'val productionPublishable = "([^"]+)"', gradle).group(1)
    legacy = re.search(r'const val LEGACY_OWNER_ID = "([^"]+)"', models).group(1)
    data = validate(env.get('NOCTUA_HOMOLOGACAO_PUBLISHABLE_KEY', ''), env.get('NOCTUA_HOMOLOGACAO_AUTH_TEST_JSON', ''), production, legacy)
    if mode == 'run':
        folder = private_dir(env)
        folder.mkdir(mode=0o700)  # Refuse to reuse an existing private directory.
        (folder/'.noctua-private').touch(mode=0o600)
        path = folder/'accounts.json'
        fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with os.fdopen(fd, 'w') as out:
            json.dump(data, out)
        require(stat.S_IMODE(path.stat().st_mode) == 0o600, 'PRIVATE_FILE_PERMISSIONS_INVALID')
        with open(env['GITHUB_ENV'], 'a') as out:
            out.write('NOCTUA_HOMOLOGACAO_AUTH_TEST_FILE='+str(path)+'\n')
        if XML.exists():
            XML.unlink()  # Generated result only; never accept a stale green report.
    return mode


def clean(env):
    scope(env)
    folder = private_dir(env)
    if folder.is_dir() and (folder/'.noctua-private').is_file():
        shutil.rmtree(folder)
    for name in ('test-results', 'reports/tests'):
        generated = ROOT/'app/build'/name/'testHomologacaoUnitTest'
        if generated.is_dir():
            shutil.rmtree(generated)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['prepare', 'start', 'summarize', 'clean'])
    parser.add_argument('--exit-code', type=int, default=1)
    args = parser.parse_args()
    env = os.environ
    report = base_report(env)
    try:
        if args.action == 'clean':
            clean(env); return 0
        if args.action == 'prepare':
            mode = prepare(env)
            report['overall'] = 'PREREQUISITES_READY_TESTS_NOT_EXECUTED' if mode == 'preflight' else 'PREPARED_FOR_AUTHORIZED_RUN'
            write_report(report); return 0
        scope(env)
        require(env.get('NOCTUA_HOSTED_AUTH_MODE') == 'run' and env.get('NOCTUA_HOMOLOGACAO_AUTH_TEST_AUTHORIZED') == 'YES', 'EXPLICIT_AUTHORIZATION_REQUIRED')
        report['hosted_requests_executed'] = None
        if args.action == 'start':
            private = private_dir(env) / 'accounts.json'
            require(env.get('NOCTUA_HOMOLOGACAO_AUTH_TEST_FILE') == str(private) and private.is_file() and stat.S_IMODE(private.stat().st_mode) == 0o600, 'PRIVATE_FILE_NOT_READY')
            selected = selected_methods(env.get('NOCTUA_HOSTED_AUTH_CASE', 'ALL'))
            report.update(overall='BUILD_OR_TEST_ATTEMPT_STARTED', cases=[dict(id=v,status='NOT_REPORTED' if v in selected.values() else 'NOT_EXECUTED') for v in METHODS.values()])
            write_report(report); return 0
        report['process_exit_code'] = args.exit_code
        raw = XML.read_bytes() if XML.is_file() else b''
        report['cases'], approved = summarize(raw, args.exit_code, env.get('NOCTUA_HOSTED_AUTH_CASE', 'ALL'))
        report['h03_diagnostics'] = h03_diagnostics(raw)
        report.update(overall=('PASSED' if report['selection']=='ALL' else 'PASSED_SELECTED_CASES') if approved else 'NOT_APPROVED', all_four_passed=approved and report['selection']=='ALL', selected_cases_passed=approved,
                      hosted_requests_executed=True if any(c['status']=='PASSED' for c in report['cases']) else None)
        write_report(report); return 0 if approved else 1
    except Blocked as e:
        report.update(overall='BLOCKED_TESTS_NOT_APPROVED', blocking_code=str(e))
    except Exception:
        report.update(overall='BLOCKED_TESTS_NOT_APPROVED', blocking_code='LOCAL_PREPARATION_OR_REPORT_ERROR')
    if args.action in ('start','summarize','clean') and report['mode']=='run':
        report['hosted_requests_executed'] = None
    write_report(report)
    print('H01-H04 blocked; inspect the redacted report only.')
    return 1

if __name__ == '__main__':
    raise SystemExit(main())
