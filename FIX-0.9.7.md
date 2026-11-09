# F2P AIO 0.9.7 — funding, inventory, session length, auto-fix pipeline

## 1. Bank items now fund supplies and quests

**Log evidence:** quest helper said `Not enough gp to buy missing quest items (need 2,985 gp, have 7 gp)`,
bank had 30 stacks, but the bot never sold any of it.

**Cause:** when a quest/supply needed coins, `SupplyService` hit the account-builder rule
“defer unfunded goal; train an affordable skill instead” *before* `FundingTask` ever ran. Bank
liquidation (0.9.2) existed, but that path was unreachable for quest supplies.

**Fix:** removed the early defer. The supply flow now runs `FundingTask` (carried products → **banked
products → ground loot**) for any missing supply, with a 3-minute funding budget
(`FUNDING_TIMEOUT`) before deferring. With the bank full of loot, it withdraws the highest-value stack
noted and sells it at the GE (`BANK_SALE_WITHDRAW` → `BANK_SALE_DONE`), then buys the quest items.

## 2. Inventory keeps only the *current activity's* gear

**Cause:** the keep-set protected every tool name in the catalogue, so a fishing rod/net stayed in the
inventory during mining, and unrelated leftovers were never cleared.

**Fix:** `InventoryCleanup.keepFor(current, ...)` now protects only the current method's tools, its
production inputs/quest items, food, coins and worn equipment. Other activities' tools are deposited
like any leftover (`BANK_DEPOSIT_JUNK`) and re-acquired by the supply service when that activity is
selected. So it re-gears per activity instead of carrying everything forever.

## 3. Skilling sessions are long, not 5-minute bounces

**Cause:** every verified chain ended with `abandon("Completed verified chain")` + a 60-second
cooldown, then the planner picked another activity. Fishing → bank → shrimp → mining → repeat.
Seed chains also only did 8 actions, far from a full inventory.

**Fix:**
- Seed chains use 12 ACT steps.
- When a chain completes and the goal is skilling/combat, the engine **continues the same goal**
  (`SESSION_CONTINUE`) until the session budget ends, reusing the chain and banking between loads.
- Session budgets: quests keep the configured `alternateMinutes`; skilling/combat get at least
  **60 minutes** (`Math.max(60, alternateMinutes)`), so it trains a skill instead of switching every
  few minutes. The rotation checks use this session deadline.

Coins in the inventory are intentional: they are the payment for supplies/GE while training. If you
want zero carried coins outside funding, that is a separate config switch.

## 4. Auto-fix pipeline (no approval)

The gateway now has **`POST /fix/apply`** and the developer tool **`dev.apply_fix`**:

1. Agent supplies `path` (must be `src/main/java/...` or `src/test/java/...`) and the full corrected
   Java content.
2. Server writes a backup, applies the content, and runs the full offline suite (`Test.ps1`).
3. On failure it **automatically restores the backup** and returns the test output.
4. On success it optionally runs `Install.ps1` (default yes), so the fixed jar is installed.
5. Everything is audited (`auto_fix_attempt`, `auto_fix_applied`, `auto_fix_rolled_back`).

No approval prompt is involved. The gate is the test suite + automatic rollback, not a human. An
agent (Hermes/developer role) can call the tool; the chat/growth agents can be pointed at it next.

## 5. How real players/bots structure this (research notes)

- **Session-based training:** players pick one skill and train for tens of minutes to hours, banking
  only when the inventory is full or supplies run out; constant switching loses XP/hour to travel.
- **Keep-set per activity:** carry exactly the tool/food/inputs for the current task; everything else
  lives in the bank. Re-gear once per activity, not per action.
- **Bank as funding:** surplus loot is the account’s wallet; sell it when a quest/supply actually needs
  GP, not as a separate "money mode".
- **Task queue + prerequisites:** account builders keep a queue of quests/skills/unlocks, resolve
  prerequisites first, and mark one-time rewards as done so they are never repeated.
These are the patterns 0.9.7 moves toward (and the reason for the bank-sale, per-activity keep-set and
long-session changes above).

## Verification

- All offline suites pass (10,193 checks) including inventory/continuous-chain recovery and
  account-knowledge cases; upstream Microbot compile passes.
- Version **0.9.7** installed; SHA-256 recorded in `STATUS.json`.
- Gateway `/tools` now lists 11 tools including `dev.apply_fix`; invalid fix paths are rejected.
