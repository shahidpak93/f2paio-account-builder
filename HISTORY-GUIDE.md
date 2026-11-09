# Current release: 0.9.4

Read [FIX-0.9.4.md](FIX-0.9.4.md) for account knowledge, combat/skilling balance and one-time rewards, then older patches. Older sections are historical.

# Latest patch: 0.8.2

Account-builder affordability and funding batch correction. See [FIX-0.8.2.md](FIX-0.8.2.md). Restart Microbot to load0.8.2; prior patch notes below are historical.

# Latest patch: 0.8.1

Purchase receipt verification and repeated-buy protection installed. See [FIX-0.8.1.md](FIX-0.8.1.md). Restart Microbot to load the patch; previous running instances retain0.8.0.

# F2P AIO 0.8.0 guide

## Start

1. Run `agent_lab/StartAgents.ps1` from PowerShell. Ollama must be running. The gateway is already configured on this computer.
2. Restart Microbot or rescan local plugins, then enable F2P AIO. Confirm version 0.8.0.
3. Log into an F2P account and press Start. Activity selection is automatic. The default objective is account progression; PvP looting and optional flipping are disabled.
4. Watch current task, prerequisite, plan, XP, level/quest events and diagnostics. Skip changes the current skill family when another eligible family exists.

The gateway is not installed as a Windows login service. After reboot, run StartAgents again. If it is unavailable the plugin can use direct local Ollama fallback. StopAgents stops only this project's gateway.

## How decisions work

The plugin reads live levels, equipment, carried supplies, quest state and discovered bank stock. Unknown bank stock is not treated as a guaranteed owned item. It selects an eligible progression goal, checks prerequisites, searches bank, buys needed supplies or funds the deficit, equips usable gear and then acts. A click returning true does not prove XP, loot or completion.

Money tasks serve required supplies. Pending quest ingredients are reserved. Gear upgrades check Attack, Defence, Ranged, Magic, relevant tool levels and quest unlocks. A rune platebody/green d'hide body needs Dragon Slayer; a usable alternative is selected before that unlock. The catalogue targets standard budget F2P loadouts, rather than an exhaustive opponent-specific DPS optimizer. A default 1,000 GP reserve and bounded upgrade budget protect progression cash.

For funding sales, noteable bank items are withdrawn as notes and all matching inventory quantities are grouped by canonical item ID. Naturally stackable goods can be sold in bulk. Unnoteable items still consume slots. Items needed for training, quests, tools or equipment are withdrawn as items. Check GE_SUBMIT quantity and actual fills when validating this live.

## AI and learning

Four role profiles provide planning, research, verification and development using two local models. They retrieve the 200-tip dataset and may read current Wiki information. Reasoning mode is available through the agent client; routine requests disable long thinking for speed. Small models remain fallible. Typed controller rules can reject their answers.

Learning has two distinct paths: a completed offline LoRA run produced the specialist's changed weights; live account-local evidence updates method/chain selection. Three verified completed chain trials promote a plan; two failures quarantine it. Mutations are bounded and retain the parent. The plugin does not continuously retrain neural weights or execute arbitrary generated Java.

The developer role can stage drafts, compile them and run the fixed regression suite. Computer access exposes the supported local Microbot read-only state API and scoped project files. Internet tools read approved research domains. Generated code is staged, not automatically hotloaded.

## Debug and recovery

Use Export debug report. Inspect START version, DECISION, prerequisite/supply events, GE_SUBMIT, actual XP/items and failure reasons. Missing tools should trigger acquisition rather than repeated tree clicks. Global/manual input pauses and scheduled blocking events can stop progress; read the exact pause reason before resuming. No region scoring fence is enabled; anchors remain fallback destinations for finding targets.

If agents fail, inspect `agent_lab/state/stderr.log` and audit metadata; never share token files or cloud credentials. Health: `http://127.0.0.1:8092/health`. For game issues capture a fresh debug export showing version, task and account state. Pending GE offer ownership is not durable across process restarts: reconcile existing offers before retrying.

## First live verification

Check missing axe acquisition; bank notes and grouped sale quantity; a level/quest-locked gear upgrade; a quest prerequisite; prayer/firemaking; a fish-to-cook chain; Skip; pause/resume; world transition; chain promotion and reuse after restart. These are still pending live checks, not completed gameplay claims.

