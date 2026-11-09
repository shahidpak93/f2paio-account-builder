"""Download revision-pinned OSRS references for training. Keep original text in a local cache."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import socket
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
CACHE = Path(os.environ.get("OSRS_TRAINING_CACHE", ROOT.parents[1] / "work/research/osrs-training-v014"))
CACHE.mkdir(parents=True, exist_ok=True)
API = "https://oldschool.runescape.wiki/api.php"
UA = "F2PAIO training reference importer (personal study)"
TITLES = """Tutorial Island|New player guide|Bank|Bank note|Grand Exchange|Trade restrictions|Ironman Mode|
The Knight's Sword|Doric's Quest|Cook's Assistant|The Restless Ghost|Sheep Shearer|Rune Mysteries|
Dragon Slayer I|The Corsair Curse|Below Ice Mountain|Corsair Cove Resource Area|Ruins of Camdozaal|
Air Altar|Mind Altar|Water Altar|Earth Altar|Fire Altar|Body Altar|Rune essence mine|
Leather|Leather armour|Tanning|Gem|Gold bar|Smithing|Anvil|Furnace|Bronze bar|Steel bar|
Iron ore|Coal|Fishing spot|Fly fishing rod|Willow tree|Maple tree|Jug of wine|
Hill Giant (Giants' Plateau)|Giant frog|Big bones|Obor|Bryophyta|Free-to-play Ironman guide""".replace("\n", "").split("|")
_lookup = socket.getaddrinfo
socket.getaddrinfo = lambda host, port, family=0, type=0, proto=0, flags=0: _lookup(
    host, port, socket.AF_INET, type, proto, flags
)


def request(url, max_bytes=2_000_000):
    with urlopen(Request(url, headers={"User-Agent": UA}), timeout=20) as response:
        data = response.read(max_bytes + 1)
    if len(data) > max_bytes:
        raise ValueError(f"Exceeded byte limit for {url}")
    return data


def source(title):
    params = urlencode({
        "action": "query", "format": "json", "formatversion": 2, "prop": "revisions",
        "rvprop": "ids|timestamp|content", "rvslots": "main", "titles": title, "redirects": 1,
    })
    page = json.loads(request(API + "?" + params))["query"]["pages"][0]
    rev = page["revisions"][0]
    text = rev["slots"]["main"]["content"].encode("utf-8")
    name = "wiki-" + hashlib.sha256(title.encode()).hexdigest()[:16] + ".wiki"
    (CACHE / name).write_bytes(text)
    return {"requested": title, "title": page["title"], "revision": rev["revid"], "timestamp": rev["timestamp"],
            "url": "https://oldschool.runescape.wiki/w/" + quote(page["title"].replace(" ", "_")),
            "cacheFile": name, "bytes": len(text), "sha256": hashlib.sha256(text).hexdigest()}


def main():
    def collect(title):
        try:
            return source(title)
        except (KeyError, ValueError, OSError) as error:
            print(f"Skipped {title}: {error}", flush=True)
            return None
    with ThreadPoolExecutor(max_workers=5) as pool:
        rows = [row for row in pool.map(collect, TITLES) if row]
    (ROOT / "training/osrs-source-manifest.json").write_text(json.dumps(rows, indent=2) + "\n", encoding="utf-8")
    print(f"Downloaded {len(rows)}/{len(TITLES)} revisions, {sum(row['bytes'] for row in rows):,} bytes", flush=True)


if __name__ == "__main__":
    main()
