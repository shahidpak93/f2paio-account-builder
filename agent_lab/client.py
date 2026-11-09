"""Local operator client; token stays in its local file, never on a command line."""
import argparse,json,pathlib,urllib.request
parser=argparse.ArgumentParser(); parser.add_argument('--role',choices=['planner','researcher','verifier','developer']); parser.add_argument('--prompt'); parser.add_argument('--reasoning',action='store_true'); parser.add_argument('--tool'); parser.add_argument('--args',default='{}')
a=parser.parse_args()
token=(pathlib.Path.home()/'.runelite/f2paio/agent-lab-token').read_text().strip()
path='/tools/invoke' if a.tool else '/agent/run'; body={'name':a.tool,'arguments':json.loads(a.args)} if a.tool else {'role':a.role or 'planner','prompt':a.prompt or 'Return JSON describing your role.','reasoning':a.reasoning}
request=urllib.request.Request('http://127.0.0.1:8092'+path,data=json.dumps(body).encode(),headers={'Content-Type':'application/json','X-Osrs-Agent-Token':token})
with urllib.request.urlopen(request,timeout=120) as response: print(json.dumps(json.load(response),indent=2,ensure_ascii=False))
