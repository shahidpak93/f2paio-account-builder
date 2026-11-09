"""Real incident orchestration. Generated advice cannot execute or replace controller guards."""
import concurrent.futures,json,re,time

def review_selection(api,body):
 goal=body.get('goal'); method=api.CATALOG.get(goal)
 state=body.get('state',{}); levels=state.get('levels',{})
 allowed=method is not None and state.get('affordable') is True
 if method and method.get('kind') not in {'QUEST','GROUND'}:
  allowed=allowed and type(levels.get(method.get('skill'))) is int and levels[method['skill']]>=method.get('level',1)
 result={'allowed':bool(allowed),'role':'verifier','goal':goal,'reason':'registered_level_and_budget_contract','authority':'typed rules'}
 api.audit('selection_review',**result)
 return result

def supervise(api,body):
 incident=str(body.get('incident',''))
 if not re.fullmatch(r'[A-Za-z0-9-]{1,80}',incident): raise ValueError('Invalid incident identifier')
 cause=str(body.get('cause',''))[:160]
 goal=str(body.get('goal',''))[:80]
 if goal not in api.CATALOG: raise ValueError('Unknown incident goal')
 live={}
 with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
  pending={name:pool.submit(api.game_read,name) for name in ['state','inventory','dialogue','bank','npcs','objects']}
  for name,future in pending.items():
   try: live[name]=future.result(timeout=5)
   except Exception as error: live[name]={'available':False,'reason':type(error).__name__}
 evidence=api.knowledge(goal+' '+cause+' '+str(body.get('status','')),8)
 # Verifier must run even when a model cannot be reached. It confirms the stop/defer contract.
 verifier={'role':'verifier','allowedActions':['REPLAN','DEFER'],'rejectedActions':['BUY_AGAIN','IGNORE_REQUIREMENTS'],'reason':cause}
 api.audit('incident_role',incident=incident,role='verifier',status='typed_stop_contract')
 researcher={'role':'researcher','facts':evidence,'needs_lookup':not bool(evidence)}
 if not evidence:
  try: researcher['webResults']=api.web_search(goal.replace('_',' '))
  except Exception as error: researcher['lookupError']=type(error).__name__
 api.audit('incident_role',incident=incident,role='researcher',rules=[r['id'] for r in evidence])
 compact={k:body.get(k) for k in ['cause','goal','phase','status','xp','coins','position']}
 compact['items']=body.get('items',[])[:28];compact['logs']=body.get('logs',[])[-4:]
 live_summary={name:{'available':value.get('available',True),'summary':json.dumps(value)[:300]} for name,value in live.items() if isinstance(value,dict)}
 context=json.dumps({'incident':compact,'live':live_summary,'verifiedFacts':evidence[:4]},ensure_ascii=False)[:4500]
 planner={};developer={}
 for role,system in [('planner','Diagnose an OSRS account-builder incident. Return JSON with action REPLAN or DEFER and a concise cause. Do not repeat the failing action.'),('developer','Review the same incident as a Microbot developer. Return only JSON with three short string fields: suspected_component, proposed_fix, regression_case. Each value under 60 characters. No code or extra fields.')]:
  try:
   value=json.loads(api.model_call(api.PROFILES[role]['model'],context,system,False,timeout=35,max_tokens=256))
   if not isinstance(value,dict): raise ValueError('Object required')
  except Exception as error: value={'error':type(error).__name__}
  if role=='planner': planner=value
  else: developer=value
  api.audit('incident_role',incident=incident,role=role,status='error' if 'error' in value else 'returned')
 action=planner.get('action') if planner.get('action') in {'REPLAN','DEFER'} else 'DEFER'
 try:
  repair=api.developer_jobs(api).enqueue({'task':'Investigate incident '+cause+' in goal '+goal+'; correct the general cause with tests.','context':{'incident':body,'live':live,'evidence':evidence}},automatic=True)
 except Exception as error: repair={'queued':False,'error':type(error).__name__}
 report={'incident':incident,'action':action,'cause':cause,'goal':goal,'verifier':verifier,'researcher':researcher,'planner':planner,'developer':developer,'snapshot':body,'live':live,'developerJob':repair,'execution':'controller already deferred offending goal; advice only','time':time.time()}
 folder=api.STATE/'incidents';folder.mkdir(exist_ok=True)
 (folder/(incident+'.json')).write_text(json.dumps(report,indent=2),encoding='utf-8')
 return {'incident':incident,'action':action,'roles':['verifier','researcher','planner','developer'],'report':'incidents/'+incident+'.json'}
