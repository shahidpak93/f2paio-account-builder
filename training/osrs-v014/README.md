# OSRS data and training run

This package contains training scripts, data splits, checksums, weights and evaluation reports. The active Microbot plugin is 0.13.0.

## Sources and labels

- The source manifest records OSRS Wiki revisions, timestamps and SHA-256 hashes. Raw wiki text is in the local research cache.
- The media manifest records OSRS Wiki videos and a public Ironman bank tour from the Internet Archive. Raw videos are in the same cache. Several wiki clips illustrate development or member gameplay. Their labels describe what is visible and carry no F2P eligibility claim.
- The text runs learn action selection from supplied OSRS rules and example account states. The held-out splits exclude entire recipes and fact topics. A separate hand-written suite checks generalization outside the templates.
- The vision pilot learns whether the bank interface is visible in video frames. Its holdout uses separate recordings. Video pixels do not supply verified action, XP, or inventory labels for training an account-playing policy.

## Reproduce

From the project root, download the source revisions and video files with the following scripts. Set OSRS_TRAINING_CACHE to a different directory if needed.

```powershell
python training/collect_osrs_sources.py
python training/collect_osrs_media.py
python training/build_osrs_dataset.py
python training/build_balanced_osrs_dataset.py
```

Use the Python environment with PyTorch, Transformers and PEFT for the text scripts. Set the base path to the local Qwen3.5-0.8B model directory.

```powershell
python training/train_lora.py --base <base-model-path> --dataset training/osrs-v014/balanced --output training/osrs-v014/balanced --batch 3 --sample-eval 12 --no-merge
python training/evaluate_osrs_adapter.py --base <base-model-path> --run training/osrs-v014/balanced
```

Use a Python environment with NumPy, Pillow and imageio-ffmpeg for the video scripts.

```powershell
python training/sample_osrs_frames.py
python training/train_osrs_vision.py
python training/verify_osrs_training.py
```

Check [the source manifest](../osrs-source-manifest.json), [the media manifest](../osrs-media-manifest.json) and [the run summary](run-summary.json). The text adapters require their original base model. The visual weights are a standalone NumPy archive.

## Results

| Run | Sampled recipe cases, base to trained | Independent exact actions, base to trained |
| --- | ---: | ---: |
| First text adapter | 0/12 to 6/12 | 2/12 to 1/12 |
| Balanced text adapter | 3/12 to 12/12 | 2/12 to 6/12 |

Both text runs changed weights. The vision pilot reached 57.7% balanced accuracy on 130 held-out frames. It recognized the bank, but misclassified the subsequent world view in the held-out player tour. The models remain offline candidates. Live control and account-building improvements still require validation.
