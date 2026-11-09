"""Check raw download integrity, train/held-out separation, and saved model artifacts."""
import hashlib
import json
from pathlib import Path
from collect_osrs_sources import ROOT, CACHE

OUTPUT = ROOT/"training/osrs-v014"


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def verify_downloads(manifest, filename_key):
    total=0
    for row in manifest:
        file=CACHE/row[filename_key]
        payload=file.read_bytes()
        if len(payload)!=row["bytes"] or hashlib.sha256(payload).hexdigest()!=row["sha256"]:
            raise ValueError(f"Source integrity failed for {file.name}")
        total+=len(payload)
    return total


def evaluate(run):
    groups=[]
    for filename in ("train","heldout"):
        rows=[json.loads(line) for line in (run/f"{filename}.jsonl").read_text(encoding="utf-8").splitlines()]
        groups.append({row["group"] for row in rows})
    if groups[0] & groups[1]:
        raise ValueError("Training and held-out recipe or fact groups overlap")
    result=read(run/"training-report.json")
    independent=read(run/"independent-decisions.json")
    if not result["weightsChanged"]:
        raise ValueError("Training produced no parameter changes")
    model=run/"adapter/adapter_model.safetensors"
    return {"steps":result["steps"],"trainRows":result["trainRows"],"heldoutRows":result["heldoutRows"],
            "baseExact":result["baseline"]["generated_exact_match"]["correct"],
            "adapterExact":result["tuned"]["generated_exact_match"]["correct"],
            "independentBase":independent["base"]["correct"],"independentAdapter":independent["adapter"]["correct"],
            "independentCases":independent["adapter"]["rows"],"adapterSha256":hashlib.sha256(model.read_bytes()).hexdigest()}


def main():
    sources=read(ROOT/"training/osrs-source-manifest.json")
    media=read(ROOT/"training/osrs-media-manifest.json")
    vision=read(OUTPUT/"vision-report.json")
    summary={
        "wikiRevisions":len(sources),"wikiBytes":verify_downloads(sources,"cacheFile"),
        "videos":len(media),"videoBytes":verify_downloads(media,"file"),
        "textRuns":{"first":evaluate(OUTPUT),"balanced":evaluate(OUTPUT/"balanced")},
        "visionHeldout":vision["heldout_model"],"visionBaseline":vision["heldout_majority_baseline"],
        "liveDeployment":False,"activePlugin":"0.13.0",
    }
    (OUTPUT/"run-summary.json").write_text(json.dumps(summary,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(summary,indent=2))


if __name__=="__main__":
    main()
