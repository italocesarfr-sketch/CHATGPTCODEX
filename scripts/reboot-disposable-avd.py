#!/usr/bin/env python3
"""Bounded, verified reboot ONLY of the sole disposable CI AVD. Never wipes data."""
import json
import os
import re
import signal
import subprocess
import time
from pathlib import Path


def adb_call(args, seconds=10):
    child = subprocess.Popen(['adb', *args], stdout=subprocess.PIPE,
                             stderr=subprocess.STDOUT, text=True, start_new_session=True)
    try:
        output, _ = child.communicate(timeout=seconds)
        return child.returncode, output
    except subprocess.TimeoutExpired:
        os.killpg(child.pid, signal.SIGKILL)
        child.communicate(timeout=5)
        return 124, ''


def reboot(env, call=adb_call, clock=time.monotonic, pause=time.sleep, record=lambda value: None):
    if env.get('GITHUB_ACTIONS') != 'true' or env.get('CI') != 'true':
        raise RuntimeError('Disposable CI only')
    code, serial = call(['get-serialno']); serial = serial.strip()
    if code or not re.fullmatch(r'emulator-\d+', serial):
        raise RuntimeError('Disposable AVD required')
    code, devices = call(['devices'])
    attached = [line.split()[0] for line in devices.splitlines()[1:] if line.strip()]
    if code or attached != [serial]:
        raise RuntimeError('Requires exactly one disposable CI AVD before network tests')
    def query(*args):
        return call(['-s', serial, *args])
    code, qemu = query('shell', 'getprop', 'ro.kernel.qemu')
    if code or qemu.strip() != '1':
        raise RuntimeError('Physical devices prohibited')
    code, before = query('shell', 'cat', '/proc/sys/kernel/random/boot_id')
    before = before.strip()
    if code or not re.fullmatch(r'[0-9a-f-]{36}', before):
        raise RuntimeError('Cannot establish reboot baseline')
    evidence = dict(environment='sole-disposable-ci-avd', reboot_proven=False,
                    adb_server_restarted=False, userdata_wiped=False)
    record(dict(evidence))
    # A lost ADB reply is not a reboot proof. Only a NEW kernel boot ID plus a
    # completed boot permits the protected-session/data read test to proceed.
    result, _ = call(['-s', serial, 'reboot'], 20)
    evidence['request_exit'] = result
    record(dict(evidence))
    started = clock()
    while clock() - started < 180:
        code, state = query('get-state')
        if code == 0 and state.strip() == 'device':
            code, current = query('shell', 'cat', '/proc/sys/kernel/random/boot_id')
            boot_code, boot = query('shell', 'getprop', 'sys.boot_completed')
            current = current.strip()
            if code == 0 and boot_code == 0 and current != before and re.fullmatch(r'[0-9a-f-]{36}', current) and boot.strip() == '1':
                evidence.update(reboot_proven=True, kernel_boot_id_changed=True,
                                elapsed_seconds=round(clock()-started, 3))
                record(dict(evidence)); return evidence
        # Recover the local host connection once, not the academic data. No second
        # reboot, reinstall, clear, logout or database operation is performed.
        if clock()-started >= 20 and not evidence['adb_server_restarted']:
            call(['kill-server']); call(['start-server'])
            evidence['adb_server_restarted'] = True; record(dict(evidence))
        pause(1)
    record(dict(evidence, failure='new kernel boot and readiness not proven'))
    raise RuntimeError('Disposable AVD reboot not proven within the readiness budget')


if __name__ == '__main__':
    target = Path('build/auth-reboot.json')
    def save(value):
        target.parent.mkdir(exist_ok=True)
        target.write_text(json.dumps(value, indent=2)+'\n')
    print(json.dumps(reboot(os.environ, record=save), indent=2))
