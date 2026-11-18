#!/usr/bin/env python3
"""Run the unchanged 27 phase-D cases after A-E + commercial migration, only in a fresh local DB."""
import importlib.util
import os
from pathlib import Path
import unittest

if (os.environ.get('NOCTUA_COMMERCIAL_DISPOSABLE')!='YES' or os.environ.get('PGHOST')!='127.0.0.1' or
    os.environ.get('PGPORT')!='55431' or os.environ.get('PGDATABASE')!='noctua_phase_d_test'):
    raise SystemExit('Fresh disposable local phase-D database required')
spec=importlib.util.spec_from_file_location('commercial_d_regression',Path(__file__).with_name('test-phase-d-postgres.py'))
d=importlib.util.module_from_spec(spec);spec.loader.exec_module(d)
for name in ('202611030001_phase_e_paging.sql','202611030002_phase_e_decisions.sql',
             '202611030003_phase_e_indexed_graph.sql','202611170001_commercial_bootstrap.sql'):
    d.execute((d.f.ROOT/'supabase/migrations'/name).read_text())
# Extend only reset's FK coverage. Original assertions/table-access checks remain untouched.
original_reset=d.b.reset
def reset():
    original_tables=d.b.TABLES
    d.b.TABLES=['noctua_commercial_accounts']+original_tables
    try:original_reset()
    finally:d.b.TABLES=original_tables
d.b.reset=reset
if __name__=='__main__':unittest.main(module=d,verbosity=2)
