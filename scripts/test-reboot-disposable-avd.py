#!/usr/bin/env python3
"""Offline harness tests; never invokes ADB or connects any device."""
import importlib.util
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('reboot',Path(__file__).with_name('reboot-disposable-avd.py'))
f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f)
OLD='11111111-1111-4111-8111-111111111111'
NEW='22222222-2222-4222-8222-222222222222'
class RebootHarnessTest(unittest.TestCase):
    def run_case(self, change=True, complete=True, request=0, offline=False, env=None, qemu='1', extra=False):
        self.now=0;self.calls=[];self.rebooted=False;self.reconnected=False
        def call(args,seconds=10):
            self.calls.append(args)
            if args==['get-serialno']:return 0,'emulator-5554'
            if args==['devices']:return 0,'List of devices attached\nemulator-5554 device\n'+('physical-device device\n' if extra else '')
            if args==['kill-server']:return 0,''
            if args==['start-server']:self.reconnected=True;return 0,''
            a=args[2:]
            if a==['shell','getprop','ro.kernel.qemu']:return 0,qemu
            if a==['reboot']:self.rebooted=True;return request,''
            if a==['get-state']:return (1,'offline') if offline and not self.reconnected else (0,'device')
            if a==['shell','getprop','sys.boot_completed']:return 0,'1' if complete else '0'
            if a==['shell','cat','/proc/sys/kernel/random/boot_id']:return 0,NEW if self.rebooted and change else OLD
            raise AssertionError('Unexpected command')
        def pause(seconds):self.now+=seconds
        return f.reboot(env if env is not None else dict(GITHUB_ACTIONS='true',CI='true'),call,lambda:self.now,pause)
    def test_new_kernel_and_completed_boot_are_required(self):
        self.assertTrue(self.run_case()['reboot_proven'])
        self.assertEqual(1,sum(c[-1]=='reboot' for c in self.calls))
    def test_lost_request_reply_needs_independent_boot_proof(self):
        self.assertEqual(124,self.run_case(request=124)['request_exit'])
        with self.assertRaises(RuntimeError):self.run_case(change=False,request=124)
    def test_old_kernel_never_passes_even_with_boot_completed(self):
        with self.assertRaises(RuntimeError):self.run_case(change=False)
    def test_incomplete_boot_never_passes(self):
        with self.assertRaises(RuntimeError):self.run_case(complete=False)
    def test_stale_adb_connection_recovers_once_without_second_reboot(self):
        self.assertTrue(self.run_case(offline=True)['adb_server_restarted'])
        self.assertEqual(1,self.calls.count(['kill-server']))
        self.assertEqual(1,sum(c[-1]=='reboot' for c in self.calls))
    def test_outside_ci_and_physical_device_are_blocked(self):
        with self.assertRaises(RuntimeError):self.run_case(env={})
        self.assertEqual([],self.calls)
        with self.assertRaises(RuntimeError):self.run_case(qemu='0')
        self.assertFalse(self.rebooted)
    def test_additional_device_prevents_host_server_restart(self):
        with self.assertRaises(RuntimeError):self.run_case(extra=True)
        self.assertNotIn(['kill-server'],self.calls)
if __name__=='__main__':unittest.main(verbosity=2)
