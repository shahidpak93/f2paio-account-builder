"""Build balanced action practice from grounded OSRS rules without using the independent cases."""
from collections import defaultdict
import json
from pathlib import Path
import random
from build_osrs_dataset import ROOT, OUTPUT, HELD_FACTS, HELD_RECIPES, FACTS

SYSTEM = ("Choose the next OSRS action from EXECUTE, TRAIN_LEVEL, WITHDRAW_ITEMS, BUY_INPUT, FIND_SOURCE, "
          "GET_ACCESS, DO_QUEST, VERIFY_OUTPUT. Respond with a JSON object containing one action field.")
ACTIONS = ("EXECUTE","TRAIN_LEVEL","WITHDRAW_ITEMS","BUY_INPUT","FIND_SOURCE","GET_ACCESS","DO_QUEST","VERIFY_OUTPUT")


def add(target, topic, prompt, action, source):
    target[action].append({"group": topic, "source": source, "messages": [
        {"role":"system", "content":SYSTEM}, {"role":"user", "content":prompt},
        {"role":"assistant", "content":json.dumps({"action":action}, separators=(",", ":"))},
    ]})


def main():
    source_index = {entry["requested"]:entry for entry in
                    json.loads((ROOT/"training/osrs-source-manifest.json").read_text(encoding="utf-8"))}
    specs = json.loads((ROOT/"knowledge/f2p-production.json").read_text(encoding="utf-8"))
    train, held = defaultdict(list), defaultdict(list)
    for spec in specs:
        topic, url = spec["id"], spec["source"]
        target = held if topic in HELD_RECIPES else train
        material = spec["inputs"][-1]
        item, amount, level, skill = material["name"], material["minimum"], spec["level"], spec["skill"]
        rule = f"{spec['label']} needs {skill} {level} and at least {amount} usable {item}; other requirements are satisfied."
        add(target,topic,f"{rule} The level is sufficient, the facility is in reach and {amount} unnoted {item} is carried.","EXECUTE",url)
        if level>1:
            add(target,topic,f"{rule} Current {skill} level is {level-1}. All supplies are ready.","TRAIN_LEVEL",url)
        add(target,topic,f"{rule} Level is met. The inventory is short on {item}; the bank contains it.","WITHDRAW_ITEMS",url)
        add(target,topic,f"{rule} Level is met. The player carries noted {item} only and can use a bank.","WITHDRAW_ITEMS",url)
        add(target,topic,f"{rule} Level is met. No usable {item} carried or banked. Grand Exchange is accessible and affordable.","BUY_INPUT",url)
        add(target,topic,f"{rule} Level is met. Neither bank nor inventory contains {item}. The account is Ironman and cannot trade.","FIND_SOURCE",url)
        add(target,topic,f"{spec['label']} was clicked, but there is no output or XP yet. What is the next step before declaring success?",
            "VERIFY_OUTPUT",url)
        if spec["operation"]=="altar":
            add(target,topic,f"{spec['label']} requires {spec['accessName']} or its worn tiara. The account has essence and sufficient level, but lacks both access items.",
                "GET_ACCESS",url)
            add(target,topic,f"Essence and the correct tiara are ready for {spec['label']}. A tiara must be worn to enter. The account only carries the tiara and has no matching talisman.",
                "GET_ACCESS",url)
    for topic,evidence,question,action in FACTS:
        if action in ACTIONS:
            target = held if topic in HELD_FACTS else train
            add(target,topic,f"{evidence} {question}",action,source_index[topic]["url"])
    for topic,context in [
        ("Rune Mysteries","The account wants to mine essence, and Rune Mysteries is unfinished."),
        ("Below Ice Mountain","The account wants to train at Camdozaal, and Below Ice Mountain is unfinished."),
        ("Dragon Slayer I","The account can complete Dragon Slayer I and wants to unlock the quest-gated armour."),
        ("The Knight's Sword","The account has Mining 10, the pie and iron bars. It wants the Smithing quest reward, and the quest is unfinished."),
    ]:
        for prefix in ("Account goal and prerequisite: ", "Plan the next step. "):
            add(train,topic,prefix+context,"DO_QUEST",source_index[topic]["url"])
    rng = random.Random(212)
    rows = []
    for action in ACTIONS:
        candidates = train[action][:]
        rng.shuffle(candidates)
        rows.extend(candidates[:12])
    rng.shuffle(rows)
    evaluation = [row for action in ACTIONS for row in held[action]]
    rng.shuffle(evaluation)
    assert not {r["group"] for r in rows} & {r["group"] for r in evaluation}
    directory = OUTPUT/"balanced"
    directory.mkdir(exist_ok=True)
    for filename, samples in (("train",rows),("heldout",evaluation)):
        (directory/f"{filename}.jsonl").write_text("".join(json.dumps(row)+"\n" for row in samples),encoding="utf-8")
        print(filename,len(samples),flush=True)


if __name__=="__main__":
    main()
