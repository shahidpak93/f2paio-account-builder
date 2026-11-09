"""Download bounded OSRS video examples with source-file metadata and coarse labels."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
from urllib.parse import quote, urlencode
from collect_osrs_sources import ROOT, CACHE, API, request

WIKI_FILES = {
    "Barbarian Gathering - Fishing.webm": ("world", "fishing", "member activity example"),
    "Barbarian Gathering - Woodcutting.webm": ("world", "woodcutting", "member activity example"),
    "Barbarian Gathering - Mining.webm": ("world", "mining", "member activity example"),
    "Game Jam V - Locking inventory slots in bank.mp4": ("bank", "bank interface", "development demonstration"),
    "Bank Tags, Trouver System Rework & More! (2).mp4": ("bank", "bank tags", "development demonstration"),
    "Felling axe.gif": ("world", "woodcutting", "looping visual reference"),
    "Smelting.gif": ("world", "smelting", "looping visual reference"),
    "Momentum Example.mp4": ("world", "movement", "development demonstration"),
}
LIMIT = 40_000_000


def save(file_name, url, page, label, activity, context, expected=None):
    path = CACHE / file_name
    if not path.exists():
        data = request(url, LIMIT)
        if expected is not None and len(data) != expected:
            raise ValueError(f"Size mismatch for {file_name}")
        path.write_bytes(data)
    data = path.read_bytes()
    if expected is not None and len(data) != expected:
        raise ValueError(f"Cached size mismatch for {file_name}")
    return {"file": file_name, "source": page, "download": url, "bytes": len(data),
            "sha256": hashlib.sha256(data).hexdigest(), "clipLabel": label, "activity": activity,
            "context": context}


def main():
    params = urlencode({"action":"query","format":"json","formatversion":2,"prop":"imageinfo",
                       "iiprop":"url|size|mime","titles":"|".join("File:"+name for name in WIKI_FILES)})
    response = json.loads(request(API+"?"+params))
    tasks = []
    for page in response["query"]["pages"]:
        name = page["title"].split(":", 1)[1]
        info = page["imageinfo"][0]
        label, activity, context = WIKI_FILES[name]
        tasks.append((name, info["url"], info["descriptionurl"], label, activity, context, info["size"]))
    tasks.append(("OSRS Ironman Bank Video.mp4",
                  "https://archive.org/download/youtube-qGSnxFsEE5k/qGSnxFsEE5k.mp4",
                  "https://archive.org/details/youtube-qGSnxFsEE5k", "bank", "Ironman bank tour",
                  "public player footage; clip-level label, not an action trace", 26778365))

    def collect(task):
        try:
            return save(*task)
        except (OSError, ValueError) as error:
            print(f"Skipped {task[0]}: {error}", flush=True)
            return None

    with ThreadPoolExecutor(max_workers=4) as pool:
        rows = [row for row in pool.map(collect, tasks) if row]
    manifest = ROOT / "training/osrs-media-manifest.json"
    manifest.write_text(json.dumps(rows, indent=2) + "\n", encoding="utf-8")
    print(f"Downloaded {len(rows)}/{len(tasks)} visual examples, {sum(row['bytes'] for row in rows):,} bytes", flush=True)


if __name__ == "__main__":
    main()
