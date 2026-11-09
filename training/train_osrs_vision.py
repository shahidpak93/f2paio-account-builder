"""Train a clip-held-out OSRS bank UI detector from manually inspected video intervals."""
import hashlib
import json
from pathlib import Path
import numpy as np
from sample_osrs_frames import ROOT, frames

OUTPUT = ROOT / "training/osrs-v014"
# Conservative intervals from the sampled contact sheets. Exclude changes around the bank closing.
BANK_INTERVALS = {
    "Game Jam V - Locking inventory slots in bank.mp4": [(0, 21)],
    "Bank Tags, Trouver System Rework & More! (2).mp4": [(0, 30)],
    "OSRS Ironman Bank Video.mp4": [(0, 232)],
}
WORLD_INTERVALS = {"OSRS Ironman Bank Video.mp4": [(252, 272)]}
HELD_OUT = {"OSRS Ironman Bank Video.mp4", "Barbarian Gathering - Mining.webm"}


def features(images):
    rgb = images.astype(np.float64) / 255.0
    cells = rgb.reshape((-1, 8, 12, 12, 12, 3)).mean(axis=(2, 4))
    gray = rgb.mean(axis=3)
    vertical = np.abs(gray[:, 1:] - gray[:, :-1]).mean(axis=1)
    horizontal = np.abs(gray[:, :, 1:] - gray[:, :, :-1]).mean(axis=2)
    return np.concatenate((cells.reshape(len(rgb), -1), vertical, horizontal), axis=1)


def labels(filename, frame_count):
    indexes = np.arange(frame_count) * 2
    if filename not in BANK_INTERVALS:
        return np.zeros(frame_count, dtype=int)
    result = np.full(frame_count, -1, dtype=int)
    for label, intervals in ((1, BANK_INTERVALS[filename]), (0, WORLD_INTERVALS.get(filename, []))):
        for first, last in intervals:
            result[(indexes >= first) & (indexes <= last)] = label
    return result


def scores(wanted, predicted):
    positive = wanted == 1
    negative = ~positive
    sensitivity = np.mean(predicted[positive] == 1) if np.any(positive) else None
    specificity = np.mean(predicted[negative] == 0) if np.any(negative) else None
    return {
        "frames": len(wanted), "accuracy": float(np.mean(wanted == predicted)),
        "bank_recall": float(sensitivity) if sensitivity is not None else None,
        "world_recall": float(specificity) if specificity is not None else None,
        "balanced_accuracy": float((sensitivity + specificity)/2)
        if sensitivity is not None and specificity is not None else None,
    }


def main():
    media = json.loads((ROOT / "training/osrs-media-manifest.json").read_text(encoding="utf-8"))
    train, held = [], []
    for clip in media:
        filename = clip["file"]
        images = frames(filename, 2)
        target = labels(filename, len(images))
        mask = target >= 0
        destination = held if filename in HELD_OUT else train
        destination.append((filename, features(images[mask]), target[mask]))
    train_x = np.concatenate([x for _, x, _ in train])
    train_y = np.concatenate([y for _, _, y in train])
    test_x = np.concatenate([x for _, x, _ in held])
    test_y = np.concatenate([y for _, _, y in held])
    if len(held) < 2 or len(set(train_y)) < 2 or len(set(test_y)) < 2:
        raise ValueError("Need both labels in training and in clip-held-out evaluation")
    mean = train_x.mean(axis=0)
    scale = np.maximum(train_x.std(axis=0), 0.035)
    x = (train_x - mean) / scale
    weights = np.zeros(x.shape[1])
    initial = hashlib.sha256(weights.tobytes()).hexdigest()
    bias = 0.0
    balanced = np.where(train_y == 1, 0.5 / np.sum(train_y == 1), 0.5 / np.sum(train_y == 0))
    for _ in range(600):
        probability = 1 / (1 + np.exp(-np.clip(x @ weights + bias, -40, 40)))
        error = balanced * (probability - train_y)
        weights -= 0.03 * (x.T @ error + 0.01 * weights)
        bias -= 0.03 * error.sum()

    def predict(values):
        return (((values - mean) / scale) @ weights + bias >= 0).astype(int)

    results = {
        "task": "Bank interface visible versus world view",
        "scope": "Pilot video-frame classifier. Two clips held out by source, including one player bank tour. Not evaluated on live Microbot screenshots.",
        "train_clips": [{"file": name, "frames": len(y)} for name, _, y in train],
        "heldout_clips": [{"file": name, **scores(y, predict(x))} for name, x, y in held],
        "heldout_model": scores(test_y, predict(test_x)),
        "heldout_majority_baseline": scores(test_y, np.full_like(test_y, int(train_y.mean() >= 0.5))),
        "weights_changed": hashlib.sha256(weights.tobytes()).hexdigest() != initial,
        "bank_intervals_seconds": BANK_INTERVALS,
        "world_intervals_seconds_when_mixed": WORLD_INTERVALS,
    }
    np.savez_compressed(OUTPUT/"bank-vision.npz", mean=mean, scale=scale, weights=weights, bias=bias)
    (OUTPUT/"vision-report.json").write_text(json.dumps(results, indent=2)+"\n", encoding="utf-8")
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
