import os,pathlib,tempfile,importlib.util,unittest,json,threading,urllib.request,urllib.error
os.environ['OSRS_DISABLE_REPAIR_JOBS']='1'
TEMP=tempfile.TemporaryDirectory(); os.environ['OSRS_AGENT_STATE_DIR']=str(pathlib.Path(TEMP.name)/'state'); os.environ['OSRS_AGENT_TOKEN_FILE']=str(pathlib.Path(TEMP.name)/'token')
path=pathlib.Path(__file__).with_name('server.py'); spec=importlib.util.spec_from_file_location('osrs_agent_server',path); module=importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
class AgentTests(unittest.TestCase):
 def test_downloaded_f2p_references(self):
  guide=module.invoke('knowledge.guides',{'query':'runecraft training'})
  self.assertTrue(guide and 'Runecraft' in guide[0]['title'])
  lookup=module.invoke('items.lookup',{'query':'Rune essence'})
  self.assertEqual(lookup['items'],[{'id':1436,'name':'Rune essence'}])
 def test_single_registered_choice(self):
  result=module.run_agent('planner','Choose one activity from [trees]. Reason: eligible tree task.')
  self.assertEqual(json.loads(result['response']),{'method':'trees'})
  self.assertEqual(result['model'],'controller-single-choice')
 def test_multiple_choices_use_model_with_exact_schema(self):
  original=module.model_call
  def reply(*args,**kwargs):
   self.assertEqual(kwargs['schema']['properties']['method']['enum'],['oak','willow'])
   return '{"method":"willow"}'
  module.model_call=reply
  try:
   result=module.run_agent('planner','Choose one activity from [oak, willow]. Reason: train XP.')
   self.assertEqual(json.loads(result['response']),{'method':'willow'})
   module.model_call=lambda *args,**kwargs:'{"method":"chickens"}'
   with self.assertRaises(ValueError): module.run_agent('planner','Choose one activity from [oak, willow].')
  finally: module.model_call=original
 def test_chat_requires_real_evidence(self):
  facts=module.knowledge('new player guide progression',3)
  answer=module.chat_grounding({'reply':'Server is down','action':'REPLAN','evidenceIds':['invented']},facts,'Explain account building. Do not use game input.',{'status':'WORK','goal':'chickens','goalKind':'COMBAT','recent':[]})
  self.assertEqual(answer['action'],'NONE');self.assertEqual(answer['model'],'grounded-reference-fallback');self.assertNotIn('Server is down',answer['reply'])
 def test_chat_cannot_invent_an_unasked_failure(self):
  facts=module.knowledge('new player guide',1)
  value={'reply':'A temporary server outage requires recovery.','action':'REPLAN','evidenceIds':[facts[0]['id']]}
  answer=module.chat_grounding(value,facts,'Explain account building',{'status':'WORK','goal':'chickens','recent':[]})
  self.assertEqual(answer['model'],'grounded-reference-fallback');self.assertNotIn('outage',answer['reply'])
 def test_chat_grounded_advice_does_not_invent_actions(self):
  facts=module.knowledge('bank notes',1)
  answer=module.chat_grounding({'reply':facts[0]['tip'],'action':'REPLAN','evidenceIds':[facts[0]['id']]},facts,'Explain notes',{'status':'WORK','goal':'trees','recent':[]})
  self.assertEqual(answer['action'],'NONE');self.assertIn('Sources:',answer['reply'])
 def test_java_repairs_receive_reload_detectable_versions(self):
  import repair_jobs
  with tempfile.TemporaryDirectory() as folder:
   root=pathlib.Path(folder);relative=pathlib.Path('src/main/java/com/example/f2paio/Tuning.java');target=root/relative;target.parent.mkdir(parents=True);target.write_text('VERSION = "0.10.0";')
   before={relative:target.read_bytes()}
   repair_jobs.bump_java_version(root,before,{pathlib.Path('src/main/java/Changed.java'):b'new'})
   self.assertIn('0.10.1',target.read_text())
 def test_old_runtime_cannot_trigger_new_source_auto_repairs(self):
  import repair_jobs
  with tempfile.TemporaryDirectory() as folder:
   root=pathlib.Path(folder);tuning=root/'src/main/java/com/example/f2paio/Tuning.java';tuning.parent.mkdir(parents=True);tuning.write_text('VERSION = "0.10.0";')
   self.assertFalse(repair_jobs.deployment_review(root,{'pluginVersion':'0.9.11'})[0])
   self.assertFalse(repair_jobs.deployment_review(root,{})[0])
   self.assertTrue(repair_jobs.deployment_review(root,{'incident':{'pluginVersion':'0.10.0'}})[0])
 def test_evolution_reads_previous_feedback(self):
  module.evolve_feedback(module,{'proposalId':'deadbeef','status':'REJECTED','evidence':'prior unsupported target'})
  original=module.model_call;prompts=[]
  def reply(*args,**kwargs):prompts.append(args[1]);return '{"type":"none"}'
  module.model_call=reply
  try:module.evolve(module,{'learning':{'chainSummary':'0 proven chains'}})
  finally:module.model_call=original
  self.assertIn('prior unsupported target',prompts[0])
 def test_monitor_does_not_restart_quests_for_combat_stalls(self):
  action,_=module.infer_action({'context':{'goal':'chickens','goalKind':'COMBAT','recent':['no observed progress']}})
  self.assertEqual(action,'REPLAN')
  action,_=module.infer_action({'context':{'goal':'cooks','goalKind':'QUEST','recent':['no observed progress']}})
  self.assertEqual(action,'RESTART_QUEST')
 def test_model_flag_preserves_python_module(self):
  import repair_jobs
  args=['python','-m','hermes_cli.main','chat','-m','old-model']
  changed=repair_jobs.model_arguments(args,'new-model')
  self.assertEqual(changed[2],'hermes_cli.main');self.assertEqual(changed[-1],'new-model');self.assertEqual(args[-1],'old-model')
 def test_repair_transaction_rollback(self):
  import types,repair_jobs
  with tempfile.TemporaryDirectory() as folder:
   root=pathlib.Path(folder);source=root/'src/main/java/Probe.java';source.parent.mkdir(parents=True);source.write_text('original')
   (root/'dist').mkdir();artifact=root/'dist/plugin.jar';artifact.write_bytes(b'oldjar')
   def fail(): artifact.write_bytes(b'badjar');return {'passed':False,'output':'deliberate regression failure'}
   api=types.SimpleNamespace(ROOT=root,STATE=root/'state',workspace_file=lambda path:root/path,regression=fail,audit=lambda *a,**k:None)
   result=repair_jobs.fix_transaction(api,{'path':'src/main/java/Probe.java','content':'bad','install':False})
   self.assertTrue(result['rolledBack']);self.assertEqual(source.read_text(),'original');self.assertEqual(artifact.read_bytes(),b'oldjar')
 def test_repair_transaction_exception(self):
  import types,repair_jobs
  with tempfile.TemporaryDirectory() as folder:
   root=pathlib.Path(folder);source=root/'src/main/java/Probe.java';source.parent.mkdir(parents=True);source.write_text('original');(root/'dist').mkdir()
   def fail():raise TimeoutError('test timeout')
   api=types.SimpleNamespace(ROOT=root,STATE=root/'state',workspace_file=lambda path:root/path,regression=fail,audit=lambda *a,**k:None)
   result=repair_jobs.fix_transaction(api,{'path':'src/main/java/Probe.java','content':'bad','install':False})
   self.assertTrue(result['rolledBack']);self.assertEqual(source.read_text(),'original')
   with self.assertRaises(ValueError):repair_jobs.fix_transaction(api,{'path':'src/main/java/Probe.java','content':'new','expectedSha256':'stale','install':False})
 def test_repair_concurrent_edit_preserved(self):
  import types,repair_jobs
  with tempfile.TemporaryDirectory() as folder:
   root=pathlib.Path(folder);source=root/'src/main/java/Probe.java';source.parent.mkdir(parents=True);source.write_text('original');(root/'dist').mkdir()
   def fail():source.write_text('new human edit');return {'passed':False}
   api=types.SimpleNamespace(ROOT=root,STATE=root/'state',workspace_file=lambda path:root/path,regression=fail,audit=lambda *a,**k:None)
   result=repair_jobs.fix_transaction(api,{'path':'src/main/java/Probe.java','content':'bad','install':False})
   self.assertTrue(result['concurrentEditPreserved']);self.assertEqual(source.read_text(),'new human edit')
 def test_chat_retains_account_separation(self):
  module.chat_record(module,'test-account-a','axe is missing',{'reply':'inspect the bank'})
  self.assertEqual(module.chat_history(module,'test-account-a')[-1]['user'],'axe is missing')
  self.assertFalse(module.chat_history(module,'test-account-b'))
 def test_developer_jobs_disabled_in_tests(self):
  queue=module.developer_jobs(module)
  self.assertFalse(queue.enqueue({'task':'Do not execute this test task'})['queued'])
 def test_selection_verifier(self):
  import team_supervisor
  self.assertTrue(team_supervisor.review_selection(module,{'goal':'trees','state':{'levels':{'WOODCUTTING':1},'affordable':True}})['allowed'])
  self.assertFalse(team_supervisor.review_selection(module,{'goal':'willow','state':{'levels':{'WOODCUTTING':1},'affordable':True}})['allowed'])
  self.assertFalse(team_supervisor.review_selection(module,{'goal':'trees','state':{'levels':{'WOODCUTTING':1},'affordable':'true'}})['allowed'])
 def test_incident_team_fallback(self):
  import team_supervisor
  original=module.model_call; original_game=module.game_read
  calls=[]
  def fail(*args,**kwargs): calls.append(args[0]);raise TimeoutError()
  module.model_call=fail;module.game_read=lambda endpoint:{'available':False}
  try:
   result=team_supervisor.supervise(module,{'incident':'test-replay','goal':'trees','cause':'unchanged_inventory_and_idle_for_two_minutes','status':'missing axe'})
   self.assertEqual(result['action'],'DEFER');self.assertEqual(len(result['roles']),4)
   saved=json.loads((module.STATE/'incidents/test-replay.json').read_text())
   self.assertEqual(len(calls),2);self.assertIn('TimeoutError',json.dumps(saved))
   self.assertTrue(saved['researcher']['facts'])
  finally: module.model_call=original;module.game_read=original_game
 def test_research_rules(self):
  self.assertGreaterEqual(len(module.RULES),310); self.assertEqual(len({r['id'] for r in module.RULES}),len(module.RULES))
  self.assertGreaterEqual(len({r['source'] for r in module.RULES}),90)
  self.assertTrue(any(r['topic']=='notes' for r in module.knowledge('bank notes bulk sale',12)))
 def test_web_boundaries(self):
  self.assertTrue(module.allowed_url('https://oldschool.runescape.wiki/api.php?action=query'))
  for url in ['http://127.0.0.1/','https://oldschool.runescape.wiki.evil.test/','https://github.com@evil.test/','file:///etc/passwd','https://github.com:8081/']:
   self.assertFalse(module.allowed_url(url))
 def test_workspace_boundaries(self):
  self.assertTrue(module.workspace_file('src/main/java/com/example/f2paio/Engine.java').is_file())
  for path in ['../../outside.json','agent_lab/state/audit.jsonl','agent_lab/agents.json']:
   with self.assertRaises(ValueError): module.workspace_file(path)
 def test_skills(self):
  self.assertTrue(module.validate_draft({'goal':'cook_shrimp','methods':['shrimp','cook_shrimp'],'batch':4}))
  for plan in [{'goal':'unknown','methods':['unknown'],'batch':4},{'goal':'shrimp','methods':['shrimp'],'batch':True},{'goal':'shrimp','methods':['shrimp'],'batch':99},{'goal':'shrimp','methods':['copper'],'batch':4}]:
   self.assertFalse(module.validate_draft(plan))
 def test_policy_bank(self):
  for purpose in ['sell_surplus','quest_turn_in','wear_equipment','train_skill']:
   for noteable in [True,False]:
    expected='WITHDRAW_NOTES' if purpose=='sell_surplus' and noteable else 'WITHDRAW_ITEMS'
    result=module.policy_review({'state':{'purpose':purpose,'noteable':noteable},'action':expected})
    self.assertTrue(result['allowed'])
 def test_policy_quest_locks(self):
  self.assertFalse(module.policy_review({'state':{'defence':40,'dragonSlayerComplete':False},'action':'RUNE_PLATEBODY'})['allowed'])
  self.assertTrue(module.policy_review({'state':{'defence':40,'dragonSlayerComplete':True},'action':'RUNE_PLATEBODY'})['allowed'])
  self.assertFalse(module.policy_review({'state':{'defence':40,'dragonSlayerComplete':'false'},'action':'RUNE_PLATEBODY'})['allowed'])
 def test_policy_progress(self):
  state={'purpose':'account_builder','questReady':True,'questCompleted':False,'missingRequirement':False}
  self.assertFalse(module.policy_review({'state':state,'action':'FLIP_FOR_PROFIT'})['allowed'])
  self.assertTrue(module.policy_review({'state':state,'action':'PROGRESS_QUEST'})['allowed'])
 def test_agent_tool_loop(self):
  original=module.model_call
  answers=iter([json.dumps({'tool':'knowledge.search','arguments':{'query':'bank notes'}}),json.dumps({'goal':'cook_shrimp','methods':['shrimp','cook_shrimp'],'batch':4})])
  module.model_call=lambda *args,**kwargs:next(answers)
  try: self.assertEqual(json.loads(module.run_agent('planner','Draft a supported chain')['response'])['goal'],'cook_shrimp')
  finally: module.model_call=original
 def test_role_scope(self):
  original=module.model_call; module.model_call=lambda *args,**kwargs:json.dumps({'tool':'workspace.stage','arguments':{'name':'No.java','content':'class No{}'}})
  try:
   with self.assertRaises(ValueError): module.run_agent('planner','Read references')
  finally: module.model_call=original
 def test_verifier_uses_rules_without_model(self):
  original=module.model_call; module.model_call=lambda *args,**kwargs:self.fail('Deterministic verifier should never load a model')
  try:
   checked=module.run_agent('verifier','Check RUNE_PLATEBODY state {"purpose":"melee_body","defence":40,"dragonSlayerComplete":false}')
   answer=json.loads(checked['response']); self.assertIs(answer['allowed'],False); self.assertEqual(answer['correctedAction'],'RUNE_CHAINBODY')
  finally: module.model_call=original
 def test_researcher_returns_canonical_evidence(self):
  original=module.model_call; module.model_call=lambda *args,**kwargs:self.fail('Canonical research should never load a model')
  try:
   answer=json.loads(module.run_agent('researcher','Rune platebody quest')['response'])
   self.assertTrue(answer['facts']); self.assertNotIn('Ignore quest locks',json.dumps(answer))
  finally: module.model_call=original
 def test_http_auth(self):
  server=module.ThreadingHTTPServer(('127.0.0.1',0),module.Handler); thread=threading.Thread(target=server.serve_forever,daemon=True); thread.start(); base='http://127.0.0.1:'+str(server.server_port)
  try:
   self.assertTrue(json.load(urllib.request.urlopen(base+'/health'))['ready'])
   with self.assertRaises(urllib.error.HTTPError) as caught: urllib.request.urlopen(base+'/tools')
   self.assertEqual(caught.exception.code,401)
   request=urllib.request.Request(base+'/tools',headers={'X-Osrs-Agent-Token':module.TOKEN})
   self.assertIn('dev.compile',json.load(urllib.request.urlopen(request))['tools'])
  finally: server.shutdown(); server.server_close()
if __name__=='__main__': unittest.main(verbosity=2)
