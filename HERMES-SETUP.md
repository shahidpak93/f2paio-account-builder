# Hermes developer setup on this computer

The gateway is launched with agent_lab/StartAgents.ps1. Developer jobs start Hermes on demand; no login service or Codex automation was added. The user authorized autonomous project repair, local terminal, files, dependencies and internet access. No new provider credentials were copied.

Current developer configuration: agent_lab/developer-config.json. Regular planning uses osrs-builder:2b. Hermes uses shahi-smart:4b with an osrs-builder:2b fallback when a turn returns no tool calls. Its local OpenAI-compatible endpoint is Ollama at127.0.0.1:11434/v1. This is the existing4B model profile, not a new fine-tune.

Runtime home: agent_lab/hermes-home. Small redistributable templates are in agent_lab/hermes-profile. Actual sessions, caches and tokens are excluded from the ZIP. The developer home has an OSRS engineer SOUL and procedural skill. It is separate from the user's existing global Hermes profile.

The installed Hermes launcher attempted an excessively deep Windows runtime copy. A direct cached Hermes module is therefore invoked through work/h-env/Scripts/python.exe. That short environment installs a complete OpenAI2.54.0 SDK and reuses the existing cached Hermes dependencies. Paths to the cached source and interpreter are in developer-config.json. They are machine-specific: check them if moving the project or updating Hermes. SDK installation evidence is in work/hermes-dependencies.log. This is not a self-contained Hermes distribution.

Hermes requires at least64K context; the profile sets65536. Developer turns may take minutes on this CPU. The configured job limit is12 minutes, the agent budget10 minutes, and the developer queue is serial. The normal game controller does not wait for that job. Repair results, tool evidence, source changes and reload requirements are recorded under agent_lab/state/jobs. Repair workspaces are under work/repair-<id>.

Auto-fix with Hermes or /repair submits a task with the live diagnostic context. The monitor/incident workflow can submit recurring errors automatically. Source edits are tested in a separate copy before merging; changed production source or installed artifacts cause a stale-merge refusal. Java changes require Microbot reload; Python changes use RestartAfterRepair.ps1. Tested rollback cases cover direct source failures and concurrent edits. The complete external restart rollback has not been live fault-injected.

StopAgents stops the local gateway; it does not stop the game plugin or the independent idle Hermes profile gateway. Stop the plugin in Microbot when you want game execution stopped. Keep state/chat/job folders and token files out of shared source bundles.

See agent_lab/developer-smoke.json for the real terminal-tool result. A zero-tool narration is treated as failure, not an executed repair. No guarantee that a small model can solve every error is implied.
