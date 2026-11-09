# AI chat, active monitor and growth loop (0.9.6)

## What was added

### 1. Chat window (`AI chat` button on the dashboard)

- Opens a dialog connected to the local agent team (`127.0.0.1:8092/chat`).
- Every message is sent with a **live snapshot**: status, plan, learning, goal, phase, position,
  coins, inventory count, levels, finished quests, items, equipment and the recent debug events.
- The agent answers with a diagnosis plus one whitelisted runtime action.
- `Apply suggested action` executes that action through the controller (next cycle), never from the
  model thread.

Whitelisted actions: `NONE`, `RESUME`, `SKIP_GOAL`, `CLOSE_INTERFACE`, `BANK_LEFTOVERS`,
`RESTART_QUEST`, `REPLAN`. Nothing else is accepted, and `AiBridgeTests` enforces the list.

### 2. Active monitoring (not just incidents)

- Every 120 s while a session is running, the engine posts its snapshot to `/monitor`.
- The gateway runs deterministic rules (error burst, stalled quest, low coins with carried items,
  no selected goal) and returns `severity`, `action`, `note`; it does not let a model auto-resume a
  user pause.
- Whitelisted actions are auto-queued and applied by the controller. Logs: `AI_MONITOR`,
  `AI_ACTION_AUTO`, `AI_ACTION`.
- This is in addition to the existing `AgentSupervisor` incident channel (2-minute stalls), so the
  cheap deterministic checks run continuously and the expensive agent workflow only on incidents.

### 3. Growth loop — AI proposes, bot uses, bot reports back

Every 30 minutes the engine sends the learning state to `/evolve`:

- chain library summary and every chain's details (probation, success/failure, quarantine reason,
  verified count),
- learned skills and their probation status,
- levels and finished quest count.

The agent returns ONE proposed capability:

- a declarative **skill** (real F2P content: id `learned_*`, skill, level, object/NPC names by
  action, products, tools, world location), or
- a typed **chain** (goal + PREPARE/TRAVEL/ACT/VERIFY/BANK steps), or `none`.

Java validates with the same gates the controller already trusts (`SkillProbation.valid`,
`ChainPlan.valid`), stores learned skills in probation / chains in `ChainLibrary`, then the normal
engine runs them. `GrowthLoop` polls the outcome (`PROMOTED`/`QUARANTINED`, chain status) and posts
`/evolve/feedback`, which the gateway records for the next proposal round. Logs: `GROWTH_SKILL`,
`GROWTH_CHAIN`, `GROWTH_REJECTED`, `GROWTH_FEEDBACK`.

The "0 proven chains, 5 on probation, 1 quarantined" line is exactly the input this loop now gets;
the agent is told to fix probation/quarantined goals first.

### 4. Hermes agents

`labs/repos/hermes-agent` and `labs/repos/hermes-agent-self-evolution` are cloned (Hermes agent
runtime and its skill/prompt evolution harness). They are available as an external agent runtime to
study/wire next; the growth loop above is native to this plugin's gateway today, not yet delegated to
Hermes.

## What "full access" means here, honestly

The agents can already:

- read the live game state and the debug event stream,
- research the OSRS wiki / approved domains over HTTP,
- read and write **staged** drafts under `agent_lab/drafts`,
- compile staged Java and run the offline test suite (`dev.compile`, `dev.test`),
- propose skills/chains and get verified outcome feedback.

They cannot silently do arbitrary shell commands, arbitrary internet calls, or hot-replace the
running plugin jar. That boundary is deliberate: unlimited self-modifying code with live account
state can brick the plugin, corrupt the project or leak credentials, and it makes debugging
impossible. The next step if you want "self-fix code" is a trusted mode with: stage → backup →
compile → tests → apply → automatic rollback on failure, plus an explicit approval step. I can add
that as the next build; the staging/compile/test tools already exist in the gateway.

## Gateway endpoints

| Endpoint | Purpose |
|---|---|
| `POST /chat` | chat reply + whitelisted action |
| `POST /monitor` | deterministic severity/action from a snapshot |
| `POST /evolve` | one typed skill/chain proposal from learning state |
| `POST /evolve/feedback` | verified outcome for a proposal |
| `GET /evolve/status` | recent proposals and feedback |

Verified live: chat returned `RESUME` (confidence 0.95) for a paused-status snapshot; monitor
returned `info/NONE` for an idle snapshot; evolve returned `none` for a sparse snapshot (the model
chose none rather than invent content). All endpoints are token-authenticated and audited.
