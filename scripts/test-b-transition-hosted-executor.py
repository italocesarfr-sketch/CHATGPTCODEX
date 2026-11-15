#!/usr/bin/env python3
import importlib.util,sys,unittest
from dataclasses import replace
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('bt_hosted',ROOT/'scripts/b-transition-hosted-executor.py');m=importlib.util.module_from_spec(spec);sys.modules[spec.name]=m;spec.loader.exec_module(m)
B='99999999-9999-4999-8999-999999999999'
class Bridge:
 def __init__(self):
  self.calls=[];self.value=m.Review(B,m.TARGET,2,7,'0'*64,(m.Workspace(B,2,7),m.Workspace(B,2,7)),complete_workspace_inventory=True,commands_and_workers_frozen=True,write_gate_bound=True)
 def review_all_live_workspaces(self):return self.value
 def consent_existing_engines(self,target):
  self.calls.append(target);self.value=replace(self.value,scope=target,enable_calls=len(self.calls),workspaces=tuple(replace(w,scope=target)for w in self.value.workspaces))
class Tests(unittest.TestCase):
 def ready(self):return m.Authorization(c=True,d=True,e=True,workspaces=True)
 def refused(self,bridge,code):
  e=m.PreparedExecutor(bridge,B)
  with self.assertRaisesRegex(m.Blocked,'^'+code+'$'):e.run(self.ready())
  self.assertEqual([],bridge.calls)
 def test_complete_sequence_reuses_each_stage_once(self):
  b=Bridge();r=m.PreparedExecutor(b,B).run(self.ready());self.assertEqual([3,4,5],b.calls);self.assertEqual(0,r['academic_writes']);self.assertEqual(3,r['scope_rpc_calls'])
 def test_missing_binding_never_instantiates_hosted_executor(self):
  with self.assertRaisesRegex(m.Blocked,'WORKSPACE_BINDING_UNAVAILABLE'):m.hosted_binding()
 def test_empty_installation_is_not_complete_workspace_evidence(self):
  b=Bridge();b.value=replace(b.value,complete_workspace_inventory=False);self.refused(b,'WORKSPACE_BINDING_REQUIRED')
 def test_no_workspace_is_blocked(self):
  b=Bridge();b.value=replace(b.value,workspaces=());self.refused(b,'WORKSPACE_LIMIT')
 def test_owner_a_is_never_accepted_as_workspace_b(self):
  b=Bridge();b.value=replace(b.value,workspaces=(replace(b.value.workspaces[0],owner='synthetic-a'),));self.refused(b,'OWNER')
 def test_queue_conflict_restore_ack_receipt_revision_and_unsent_d_block(self):
  for fields in ({'queues':1},{'conflicts':1},{'restore':True},{'acknowledged':False},{'all_15_types_accounted':False},{'references':False},{'receipts':False},{'revisions':False}):
   with self.subTest(fields=fields):
    b=Bridge();b.value=replace(b.value,workspaces=(replace(b.value.workspaces[0],**fields),));self.refused(b,'PENDING_OR_CONFLICT'if any(k in fields for k in ('queues','conflicts','restore'))else'INTEGRITY')
 def test_cursor_or_scope_must_match_every_workspace(self):
  for fields in ({'cursor':6},{'scope':3}):
   b=Bridge();b.value=replace(b.value,workspaces=(replace(b.value.workspaces[0],**fields),));self.refused(b,'REVISION_OR_CURSOR')
 def test_each_authorization_is_independent(self):
  for field,expected in (('c',[]),('d',[3]),('e',[3,4])):
   b=Bridge();e=m.PreparedExecutor(b,B)
   with self.assertRaisesRegex(m.Blocked,'CONSENT_'):e.run(replace(self.ready(),**{field:False}))
   self.assertEqual(expected,b.calls)
 def test_third_revision_stops_without_rollback_or_next_stage(self):
  b=Bridge();advance=b.consent_existing_engines
  def changed(target):advance(target);b.value=replace(b.value,head=8)
  b.consent_existing_engines=changed
  with self.assertRaises(m.Blocked):m.PreparedExecutor(b,B).run(self.ready())
  self.assertEqual([3],b.calls)
 def test_lost_response_does_not_repeat_or_downgrade(self):
  b=Bridge();advance=b.consent_existing_engines
  def lost(target):advance(target);raise OSError('synthetic lost response')
  b.consent_existing_engines=lost;e=m.PreparedExecutor(b,B)
  with self.assertRaises(OSError):e.run(self.ready())
  with self.assertRaisesRegex(m.Blocked,'NO_AUTOMATIC_REPLAY'):e.run(self.ready())
  self.assertEqual([3],b.calls);self.assertEqual(3,b.value.scope)
 def test_production_is_rejected_before_consent(self):
  b=Bridge();b.value=replace(b.value,project='production-not-authorized');self.refused(b,'PROJECT')
 def test_other_account_cannot_label_itself_b(self):
  b=Bridge();a='88888888-8888-4888-8888-888888888888';b.value=replace(b.value,owner_b=a,workspaces=(m.Workspace(a,2,7),));self.refused(b,'OWNER')
 def test_worker_or_write_gate_absence_prevents_any_consent(self):
  for field in ('commands_and_workers_frozen','write_gate_bound'):
   b=Bridge();b.value=replace(b.value,**{field:False});self.refused(b,'WORKSPACE_BINDING_REQUIRED')
 def test_budget_and_data_changes_block_following_stage(self):
  for fields in ({'enable_calls':2},{'data_hash':'1'*64},{'academic_writes':1}):
   b=Bridge();advance=b.consent_existing_engines
   def changed(target,fields=fields):advance(target);b.value=replace(b.value,**fields)
   b.consent_existing_engines=changed
   with self.assertRaises(m.Blocked):m.PreparedExecutor(b,B).run(self.ready())
   self.assertEqual([3],b.calls)
 def test_no_http_or_secret_loader_exposed(self):
  t=(ROOT/'scripts/b-transition-hosted-executor.py').read_text()
  for s in ('import requests','import http','import socket','getenv','os.environ','UPDATE '):self.assertNotIn(s,t)
if __name__=='__main__':unittest.main()
