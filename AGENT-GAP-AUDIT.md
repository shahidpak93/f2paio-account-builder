# Agent integration audit and research — 2026-11-08

## What actually happened

The gateway audit records several real planner calls during the user's session, including6.805s and2.727s responses. Thus AI communication happened. It does not establish correct use or successful gameplay. A ValueError also occurred; the log does not retain its precise cause. Earlier verifier requests were setup probes, not evidence of continuous runtime supervision.

AgentBus sends normal requests through /api/generate. Server.py routes that endpoint to run_agent('planner'). The four profiles exist, but normal plugin execution does not orchestrate researcher/verifier/developer as an automatic team. The typed Java controller does enforce many preconditions separately.

RecoverySupervisor is invoked by Engine.triggerSelfHeal in the ordinary no-target path. Chain execution, gear preparation and quest supply recovery return earlier. A loop in those paths does not necessarily trigger an AI diagnosis. Recovery receives only a short recent-log excerpt plus position/goal/slot count, not a complete inventory/offer/quest-step account snapshot. It proposes only a small recovery vocabulary; LOCAL_SKILL/EXPEDITION_GE currently collapse to replan rather than dedicated executors.

Calls share one in-flight slot and limits10/hour,40/day. Java requests time out at15s while the broker can make up to three model/tool rounds with a90s per-model timeout. That mismatch can abandon a request before its multi-step workflow finishes. Increasing every timeout alone would not repair missing supervision.

The developer role stages/compiles drafts on request; it does not continuously watch the plugin or automatically deploy repairs. Research tools exist, but the plugin does not automatically route every unknown requirement to the researcher.

## Research and applicable gaps

1. OSRS task preconditions and effect waits: DreamBot's official Sleep API provides bounded condition/tick waits. Apply the pattern to actual inventory/quest/offer effects, not click-return success. 0.8.1 added purchase receipts; bank, shop and dialogue paths still need a systematic audit.
2. Live observations: Microbot's own debugging notes recommend querying live Agent Server state/cache after repeated failed hypotheses. Our read-only proxy is incomplete for full diagnosis: add verified installed-version endpoints for dialogue, widgets, NPCs, objects, bank and GE offers with item IDs/forms and timestamps.
3. Supervisor routing: LangChain's supervisor pattern explicitly dispatches workers and returns their results. Installed role profiles alone do not provide this. Route typed incidents to verifier/researcher/developer, correlate their replies, then let the controller accept only executable bounded plans.
4. Evaluate and act on feedback: Anthropic describes evaluator-optimizer loops and environment feedback/stopping conditions. Add expected effects and timeout budgets to every phase, including supplies/chains/native quests; on repeated unchanged state, stop the failing action before requesting diagnosis.

## Implementation order and acceptance checks

P0 — A phase-independent incident supervisor: detect repeated equivalent subgoals, no XP/quest progress, coin loss without received supplies, route oscillation and an unchanged native quest step. Distinguish legitimate travel, animation and manual pauses. Emit one incident ID; freeze the offending operation; capture a complete snapshot. Replaying the newt log must create an incident before another purchase.

P0 — Wire the verifier into real decisions: supply purchase/sale, gear changes and planner revisions should have explicit typed preflight checks plus post-effect checks. A model cannot override hard levels/budgets. One verifier event must be traceable to each tested decision; a profile merely existing is insufficient.

P1 — Persistent account milestone graph: choose a concrete unlock/quest/stat milestone, resolve dependencies, acquire materials by supported gathering/shop/bank routes and retain the parent goal. Current scoring plus64 capabilities is not a complete account plan. 0.8.2 defers unaffordable tasks; it does not yet construct every prerequisite route.

P1 — Shared snapshot and durable ownership: account, world, tick/time, canonical/physical IDs, quantities, bank freshness, native quest step and owned GE offer lifecycle. Reconcile on restart; stale/unknown is not zero or success.

P1 — Useful agent routing: verifier checks facts, researcher handles an unresolved mechanic using sources, planner proposes only registered capabilities, developer creates a staged reproducible patch when capability is missing. Revalidate the current goal/state when replies arrive. Old-goal responses must not affect a new task after Skip.

P1 — Request lifecycle: separate planner/advisory work from urgent incidents, reserved incident budget, cancellation, bounded retries, explicit provider errors and UI timestamps. Align plugin and broker deadlines. Do not queue unrestricted work on this CPU.

P2 — Replay-based improvement: save incident snapshots and verified before/after effects; add regression cases from real failures. Promote only demonstrated successful skills. Treat neural training metrics and offline checks separately from live account progression.

P2 — Dashboard: last agent contacted, purpose, response status/time, incident, accepted/rejected proposal and actual outcome. No ambiguous “AI enabled” status implying active supervision.

## Primary sources consulted

- Microbot debugging notes: https://github.com/chsami/Microbot-Hub/blob/main/docs/PLUGIN_DEBUGGING_NOTES.md
- Microbot Agent Server: https://github.com/chsami/Microbot/blob/main/docs/AGENT_SERVER.md (upstream capabilities; installed client must be inspected)
- DreamBot Sleep API: https://dreambot.org/javadocs/org/dreambot/api/utilities/Sleep.html
- LangChain multi-agent workflows: https://www.langchain.com/blog/langgraph-multi-agent-workflows
- Anthropic Building effective agents: https://www.anthropic.com/research/building-effective-agents

This is a source/runtime audit and researched implementation plan, not an installed new supervisor. Installed plugin remains0.8.2. No bot marketing or anti-ban claim establishes reliable automation.
