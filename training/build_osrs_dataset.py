"""Build grounded decision examples and hold out complete recipes and source topics."""
from hashlib import sha256
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "training/osrs-v014"
OUTPUT.mkdir(exist_ok=True)
SYSTEM = ("You are an OSRS F2P progression assistant. Use the supplied recipe and live state. "
          "Return only a JSON object with the requested fields. Require observed unnoted inputs and respect level gates.")
HELD_RECIPES = {"cook_sardine", "leather_chaps", "craft_body", "smelt_mithril", "cut_ruby", "forge_steel", "make_wine"}

FACTS = [
    ("Bank note", "Bank notes are tradeable inventory forms. Withdraw as items before skilling or equipping.",
     "Do noted ore stacks satisfy furnace inputs?", "WITHDRAW_ITEMS"),
    ("Ironman Mode", "Standard Ironman accounts cannot acquire supplies through the Grand Exchange.",
     "You are an Ironman with a missing and unbanked production input. Can the GE acquire it?", "FIND_SOURCE"),
    ("The Knight's Sword", "The Knight's Sword requires 10 Mining to obtain blurite ore.",
     "Mining is 9. What comes before attempting to mine blurite?", "TRAIN_LEVEL"),
    ("Doric's Quest", "Doric asks for six clay, four copper ore, and two iron ore.",
     "All twelve quest items are present in usable form. What comes next?", "DO_QUEST"),
    ("Rune Mysteries", "The Rune Mysteries quest is required to enter the Rune essence mine.",
     "The quest is uncompleted. What should you do before trying to enter the essence mine?", "DO_QUEST"),
    ("Air Altar", "The Air Altar crafts air runes. Enter with the matching talisman or a worn air tiara.",
     "You have essence and a worn air tiara. You are at the ruins. What comes next?", "ENTER_ALTAR"),
    ("Earth Altar", "An earth talisman or a worn earth tiara grants entry to the Earth Altar.",
     "You only have a water tiara at the earth ruins. What comes before entering?", "GET_ACCESS"),
    ("Dragon Slayer I", "Wearing a rune platebody requires completing Dragon Slayer I.",
     "Dragon Slayer I is uncompleted. May you equip the rune platebody now?", "BLOCKED"),
    ("Steel bar", "A steel bar requires one iron ore and two coal at a standard furnace.",
     "You have iron ore and one coal. Can you smelt a steel bar now?", "GET_INPUT"),
    ("Bronze bar", "At a furnace, one copper ore and one tin ore produce a bronze bar.",
     "You have copper ore and tin ore and are at a furnace. What should you do?", "SMELT"),
    ("Jug of wine", "Wine is made by adding grapes to a jug of water and allowing the mixture to ferment.",
     "The mixture is still unfermented. What comes before recording completion?", "VERIFY_OUTPUT"),
    ("Leather", "Soft leather is produced by tanning cowhide.",
     "A leather recipe needs leather and you have cowhide but no leather. Which step comes first?", "TAN"),
    ("Corsair Cove Resource Area", "The Corsair Cove Resource Area requires completion of Dragon Slayer I.",
     "Dragon Slayer I is incomplete. Can the account use the area now?", "BLOCKED"),
    ("Below Ice Mountain", "Completion of Below Ice Mountain unlocks the Ruins of Camdozaal activities.",
     "The quest is unfinished. What comes before using its activity routes?", "DO_QUEST"),
    ("Fly fishing rod", "Fly fishing requires a fly fishing rod and feathers.",
     "You have the rod but no feathers. What comes before lure fishing?", "GET_INPUT"),
    ("Furnace", "A furnace smelts ores into bars; an anvil works bars into equipment.",
     "You have ores. Should you visit the furnace or anvil first?", "SMELT"),
    ("Hill Giant (Giants' Plateau)", "Giants' Plateau contains Hill Giants, which can be fought in F2P.",
     "The account is injured and lacks food. Should it ignore preparation and start fighting?", "GET_SUPPLIES"),
    ("Big bones", "Burying big bones grants Prayer experience.",
     "You have unnoted big bones and want Prayer XP. What action advances the goal?", "BURY"),
]
HELD_FACTS = {"Earth Altar", "Steel bar", "Jug of wine", "Corsair Cove Resource Area"}


def row(prompt, action, group, url, revision=None):
    return {"group": group, "source": url, "revision": revision,
            "messages": [{"role": "system", "content": SYSTEM},
                         {"role": "user", "content": prompt + ' Return {"action":"CODE"}.'},
                         {"role": "assistant", "content": json.dumps({"action": action}, separators=(",", ":"))}]}


def main():
    train, held = [], []
    specs = json.loads((ROOT / "knowledge/f2p-production.json").read_text(encoding="utf-8"))
    sources = {source["requested"]: source for source in
               json.loads((ROOT / "training/osrs-source-manifest.json").read_text(encoding="utf-8"))}
    for spec in specs:
        dest = held if spec["id"] in HELD_RECIPES else train
        material = spec["inputs"][-1]
        minimum = material["minimum"]
        ingredients = ", ".join(f"{item['minimum']} {item['name']}" for item in spec["inputs"])
        evidence = f"{spec['label']} requires {spec['skill']} {spec['level']} and unnoted {ingredients}."
        if spec["operation"] == "altar":
            evidence += f" Access requires {spec['accessName']} or its worn tiara."
        if spec["level"] > 1:
            dest.append(row(f"{evidence} Live level {spec['level']-1}; all items ready.",
                            "TRAIN_LEVEL", spec["id"], spec["source"]))
        dest.append(row(f"{evidence} Level is sufficient, other prerequisites met; "
                        f"only {minimum-1} usable {material['name']} and the bank has enough.",
                        "WITHDRAW_ITEMS", spec["id"], spec["source"]))
        dest.append(row(f"{evidence} Level and access are satisfied. The needed {material['name']} "
                        "is absent from inventory and bank; this account can trade and afford it.",
                        "BUY_INPUT", spec["id"], spec["source"]))
        dest.append(row(f"{evidence} Correct level, location, access and usable inputs verified.",
                        "EXECUTE", spec["id"], spec["source"]))
        dest.append(row(f"{evidence} Level and access are satisfied. The only required {material['name']} "
                        "is currently in noted form.",
                        "WITHDRAW_ITEMS", spec["id"], spec["source"]))
    for title, evidence, question, action in FACTS:
        source = sources[title]
        dest = held if title in HELD_FACTS else train
        dest.append(row(f"Reference: {evidence} Situation: {question}", action, title,
                        source["url"], source["revision"]))
    assert not {item["group"] for item in train} & {item["group"] for item in held}
    for name, rows in (("train", train), ("heldout", held)):
        data = "".join(json.dumps(item, ensure_ascii=False) + "\n" for item in rows)
        (OUTPUT / f"{name}.jsonl").write_text(data, encoding="utf-8")
        print(name, len(rows), "rows", sha256(data.encode()).hexdigest())


if __name__ == "__main__":
    main()
