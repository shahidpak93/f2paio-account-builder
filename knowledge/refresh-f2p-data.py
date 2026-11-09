"""Refresh public F2P guide indexes, the tradeable item mapping and bounded demonstration samples."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import math
from collections import Counter
from pathlib import Path
import socket
from urllib.parse import urlencode
from urllib.request import Request, urlopen

ROOT=Path(__file__).resolve().parents[1]
CACHE=ROOT.parents[1]/"work/research/f2p-and-demonstrations"
CACHE.mkdir(parents=True,exist_ok=True)
UA="F2PAIO reference importer (personal project; F2P method and demonstration research)"
address_lookup=socket.getaddrinfo
socket.getaddrinfo=lambda host, port, family=0, type=0, proto=0, flags=0: address_lookup(host,port,socket.AF_INET,type,proto,flags)
GUIDES=["Melee","Ranged","Magic","Prayer","Mining","Smithing","Fishing","Cooking","Woodcutting",
        "Firemaking","Crafting","Runecraft"]
TITLES=[f"Free-to-play {skill} training" for skill in GUIDES]
TITLES+=["Quests/Free-to-play","Money making guide/Free-to-play","Free-to-play transportation",
         "Free-to-play account builds","New player guide","Skills"]
VPT="https://openaipublic.blob.core.windows.net/minecraft-rl/"


def download(url, filename, limit):
    path=CACHE/filename
    if path.exists():
        return path.read_bytes()
    with urlopen(Request(url,headers={"User-Agent":UA}),timeout=18) as source:
        data=source.read(limit+1)
        if len(data)>limit:
            raise ValueError(f"Size limit exceeded for {filename}")
    path.write_bytes(data)
    return data


def write(path, data):
    path.write_text(json.dumps(data,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")


def guide(title):
    args=urlencode({"action":"parse","format":"json","page":title,"prop":"sections|revid","redirects":1})
    name="guide-"+hashlib.sha256(title.encode()).hexdigest()[:16]+".json"
    data=json.loads(download("https://oldschool.runescape.wiki/api.php?"+args,name,750_000))["parse"]
    return {"title":data["title"],"revision":data.get("revid"),"source":"https://oldschool.runescape.wiki/w/"+data["title"].replace(" ","_"),
            "sections":[{"label":s["line"],"anchor":s["anchor"]} for s in data.get("sections",[])]}


def item_mapping():
    url="https://prices.runescape.wiki/api/v1/osrs/mapping"
    raw=download(url,"wiki-item-mapping.json",4_000_000)
    return {"source":url,"sha256":hashlib.sha256(raw).hexdigest(),
            "items":[{"id":i["id"],"name":i["name"]} for i in json.loads(raw) if i.get("members") is False]}


def demonstration(relative):
    data=download(VPT+relative,Path(relative).name,16_000_000)
    # Abstract observed phases and drop account names, text, camera and raw controls from the policy.
    previous=None
    changes=checks=work_windows=continued=0
    windows=0
    phase_transitions=Counter()
    for index,line in enumerate(data.splitlines()):
        if index%20:
            continue
        row=json.loads(line)
        keys=(row.get("keyboard") or {}).get("keys") or []
        buttons=(row.get("mouse") or {}).get("buttons") or []
        phase=("inspect" if row.get("isGuiInventory") else "interface" if row.get("isGuiOpen")
               else "work" if buttons else "travel" if any(k.rsplit(".",1)[-1] in {"w","a","s","d"} for k in keys)
               else "idle")
        if phase=="work":
            work_windows+=1
        if previous in {"work","travel","inspect"}:
            changes+=1
            continued+=phase==previous
            checks+=phase=="inspect" and previous!="inspect"
            phase_transitions[previous+":"+phase]+=1
        windows+=1
        previous=phase
    return {"source":VPT+relative,"sha256":hashlib.sha256(data).hexdigest(),"windows":windows,"phaseTransitions":dict(phase_transitions),
            "workWindows":work_windows,"transitions":changes,"continuations":continued,"inventoryChecks":checks}


def collect(call, values):
    def safe(value):
        try:
            return call(value)
        except Exception as error:
            print(f"Skipped {value}: {type(error).__name__}",flush=True)
            return None
    with ThreadPoolExecutor(max_workers=5) as pool:
        return [result for result in pool.map(safe,values) if result]


def score_prior(recordings):
    train=recordings[:-2]
    heldout=recordings[-2:]
    counts=Counter()
    for trial in train:
        counts.update(trial["phaseTransitions"])
    phases=("inspect","interface","work","travel","idle")
    marginal=Counter()
    for edge,n in counts.items():
        marginal[edge.split(":")[1]]+=n
    total=sum(marginal.values())
    markov_loss=baseline_loss=n_test=0
    for trial in heldout:
        for edge,n in trial["phaseTransitions"].items():
            before,after=edge.split(":")
            subtotal=sum(counts[before+":"+phase] for phase in phases)
            markov_loss-=n*math.log((counts[edge]+1)/(subtotal+len(phases)))
            baseline_loss-=n*math.log((marginal[after]+1)/(total+len(phases)))
            n_test+=n
    work=sum(counts["work:"+phase] for phase in phases)
    return {"trainRecordings":len(train),"heldOutRecordings":len(heldout),"heldOutTransitions":n_test,
            "markovLoss":markov_loss/max(1,n_test),"baselineLoss":baseline_loss/max(1,n_test),
            "validated":n_test>0 and markov_loss<baseline_loss,"workContinuationRate":(counts["work:work"]+1)/(work+len(phases))}


def main():
    guides=collect(guide,TITLES)
    write(ROOT/"knowledge/f2p-guide-index.json",guides)
    items=item_mapping()
    write(ROOT/"knowledge/f2p-items.json",items)
    index=json.loads(download(VPT+"snapshots/all_7xx_Apr_6.json","vpt-index.json",2_000_000))
    selected=[]
    prefixes=set()
    for video in index["relpaths"]:
        if not video.endswith(".mp4"):
            continue
        name=Path(video).name
        prefix="-".join(name.split("-")[:3])
        if prefix not in prefixes:
            selected.append(video[:-4]+".jsonl")
            prefixes.add(prefix)
        if len(selected)==12:
            break
    trials=collect(demonstration,selected)
    if len(trials)<3:
        raise RuntimeError("Insufficient demonstration recordings")
    transitions=sum(t["transitions"] for t in trials)
    profile={"dataset":"OpenAI VPT contractor play, Minecraft","datasetSource":"https://github.com/openai/Video-Pre-Training#contractor-demonstrations",
             "recordings":trials,"transitions":transitions,
             "continuationRate":sum(t["continuations"] for t in trials)/max(1,transitions),
             "inventoryCheckRate":sum(t["inventoryChecks"] for t in trials)/max(1,transitions),
             "evaluation":score_prior(trials),
             "scope":"Cross-domain, bounded tie-break for workflow continuity. No raw input or timing replay."}
    write(ROOT/"knowledge/human-workflow-prior.json",profile)
    print("Guide indexes",len(guides),"F2P tradeable items",len(items["items"]),"human recordings",len(trials),
          "transition observations",transitions,flush=True)

if __name__=="__main__":
    main()
