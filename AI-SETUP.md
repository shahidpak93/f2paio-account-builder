# Local AI setup and training

## Installed here

- Ollama 0.40.15, Python 3.14 and isolated training dependencies in `work/train-env`.
- `qwen3.5:0.8b`, `qwen3.5:2b-q4_K_M`, `osrs-builder:2b` and `osrs-builder-fast:0.8b` are installed in Ollama.
- `osrs-builder:2b` is a domain system-prompt profile of the 2B model; its weights were not fine-tuned.
- `osrs-builder-fast:0.8b` is the merged, quantized result of actual LoRA training on Qwen/Qwen3.5-0.8B.
- Inference was observed on CPU on this machine. No claim that this is the universally fastest/newest/best model.

Use `./agent_lab/StartAgents.ps1`. The authenticated gateway binds only 127.0.0.1:8092. Token lives in `.runelite/f2paio/agent-lab-token`, never in the package. `/health` reports roles and knowledge count. Use `./agent_lab/StopAgents.ps1` to stop it.

Example operator request:

```powershell
python ./agent_lab/client.py --role researcher --prompt "What bank note rules apply to funding sales?"
python ./agent_lab/client.py --role planner --reasoning --prompt "Explain prerequisites for a proposed F2P progression task as JSON."
```

Four roles are profiles, not four independently trained networks. The broker provides knowledge search, live Wiki search/read, scoped source read/staging, read-only game state, fixed Java compilation/tests and typed policy checks. Microbot's local AgentServer must be enabled for game-state reads; absent client returns unavailable.

## Actual training and evaluation

Final run: 106 training examples, 30 distinct held-out inputs, rank8 LoRA, 53 CPU updates. Prompt tokens are masked from loss. Adapter weight hashes changed. Held-out teacher-forced token accuracy increased from 82.28% to 98.03%; this measures constrained next-token prediction, not independent reasoning or game success.

The saved six-question bare generation probe scored base0.8B 3/6, tuned0.8B 2/6, planner2B 4/6. A separate ten-case grounded probe scored tuned0.8B 6/10 and planner2B 2/10. These tiny probes demonstrate remaining mistakes and sensitivity to prompts, not a reliable model ranking. The host returns canonical retrieved factual evidence for researcher answers and overrides verifier answers with typed checks. Neither model can promise no mistakes.

See `training/training-report.json`, `models/benchmark.json`, `models/grounded-benchmark.json` and the phase1 report. The report's qualityGate means training metric improvement only, not permission for autonomous generated-code execution.

## Reproduce

Paths below are relative to the original workspace root, two levels above this project. Original base weights and merged/GGUF exports remain in work; they are intentionally absent from the source ZIP.

1. Create a Python environment and install `training/requirements-lock.txt`. Python3.14 CPU Torch was tested. Download official `Qwen/Qwen3.5-0.8B` to `work/hf-qwen08`.
2. Run `work/train-env/Scripts/python.exe outputs/f2paio/training/train_lora.py --base work/hf-qwen08`. This overwrites adapter/report outputs; archive a checkpoint first if comparing experiments.
3. The script merges to `work/osrs-qwen08-merged`. Convert with official llama.cpp `convert_hf_to_gguf.py`, `--outtype q8_0`, output `work/osrs-trained-q8.gguf`.
4. Update the absolute FROM path in `models/Modelfile.trained` if moved, then run `ollama create osrs-builder-fast:0.8b -f models/Modelfile.trained`. Run `ollama create osrs-builder:2b -f models/Modelfile.planner` for the planner profile.
5. Evaluate held-out free generation and controller integration before adopting a new checkpoint. Training scripts do not create gameplay evidence.

Sources: https://huggingface.co/Qwen/Qwen3.5-0.8B ; https://ollama.com/library/qwen3.5 ; https://github.com/ggml-org/llama.cpp ; https://huggingface.co/docs/peft/


##0.10 update
Current runtime and developer access are documented in HERMES-SETUP.md. Knowledge now contains310 facts. Existing LoRA weights were not retrained. Runtime feedback and Hermes procedures are separate learning mechanisms.
