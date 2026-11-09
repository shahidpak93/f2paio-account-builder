# F2P AIO 0.9.3 — quest stall, chain give-up and inventory leftovers

Three bugs from the 0.9.1 live log, fixed together because they amplified each other.

## 1. Quests stalled under native Quest Helper

**Log:** `DELEGATE: Quest Helper / QuestScript owns execution of Goblin Diplomacy`, then the player
stood still at the bank for two minutes until `SUPERVISOR_INCIDENT: unchanged_inventory_and_idle`,
then Ernest stalled the same way.

**Cause:** Microbot's `QuestScript` stops itself and writes `TurnOn=false` when it hits a condition it
will not resolve (missing items, no route, a broken step). The integration set `TurnOn` *after*
starting the helper and then returned early whenever the owned quest matched, so it never noticed the
helper had switched itself off. Nothing was driving the quest; the supervisor only saw the symptom
and deferred the quest for ten minutes.

**Fix:**
- `TurnOn` is set **before** `startQuestHelper`, not after.
- While delegated, `QuestIntegration` checks the flag; if it was switched off it re-arms (max 3 times
  per two minutes, logged as `QUEST_HELPER_REARM`), then reports `QUEST_HELPER_STUCK`.
- New recovery path `QUEST_RECOVER`: if a dialogue is open it clicks continue, otherwise it re-selects
  the quest and re-arms the helper.
- Engine watchdog on `DELEGATE`: 30 s without a change in position/inventory/quest points/busy state
  triggers recovery, 75 s triggers a **3-minute** defer instead of waiting for the 2-minute supervisor
  and then the old 10-minute cooldown. Microbot pauses are excluded from the watchdog clock.

## 2. Fishing chain "gave up after a few" and went mining

**Log:** shrimp verified once, then many `ACT` heartbeats with no further `CHAIN_VERIFIED`, then
`CHAIN_OUTCOME: chain_shrimp ... failures=1 reason=EFFECT_TIMEOUT: shrimp` and `REPLAN`.

**Cause:** the chain runner only sent the next action when the player was idle. Fishing/mining keeps
the player busy in an animation, so the runner never returned to `VERIFY` to count the catches that
were already happening. Its 30-second window then expired and it declared a failure even though
eight shrimps had been caught. Two such timeouts also pushed the chain toward `QUARANTINED`.

**Fix (ChainRunner):**
- While an action is already running (`state.busy` with a prior submission), the runner steps into
  `VERIFY` instead of waiting for idle.
- `VERIFY` only counts **new** growth since the last verified snapshot, so the same logs/fish cannot
  be counted twice.
- A quiet window after at least one verified effect is a **partial success**: the chain continues to
  settlement (`CHAIN_PARTIAL`) instead of failing and feeding the quarantine counter. Zero-effect
  chains still fail with `EFFECT_TIMEOUT`, so a broken chain cannot loop forever.
- Pausing adjusts the new effect clock, so a manual pause cannot create a false timeout.

## 3. Inventory: mined ore banked, leftover shrimps never

**Cause:** every banking path only deposited the **current goal's** products. Items left over from an
abandoned goal (raw shrimps after the failed fishing chain, etc.) were never deposited. The engine's
BANK phase then reported "inventory full of protected items" and the next goal worked around the junk.
`SupplyService` deposited exactly one unrelated item only when the inventory was already full.

**Fix:**
- New `InventoryCleanup` defines the keep-set once: method tools, food, coins, worn equipment,
  current goal/quest inputs, reserved progression items. Everything else is a leftover.
- `BankTask.depositUnrelated()` deposits the first leftover, using the nearest bank.
- Every path uses it:
  - chain prepare, when the inventory is within 2 slots of full;
  - engine `BANK` phase when full but no products;
  - engine `PREPARE` of the next goal (`cleanupPending` set on every abandon), so a switch clears
    leftovers before supplies are acquired.
- New events: `BANK_DEPOSIT_JUNK`, plus the existing `BANK_DEPOSIT`.

## Verification

- New suite: `PASS 10 inventory/continuous-chain recovery cases`
- All previous suites still pass: run energy 16, bank/curriculum 14, learning 20, chain 97,
  account-builder 124, 10,193 offline checks
- Jar packaging + native loader pass; upstream Microbot `:client:compileJava` passes
- Version **0.9.3**, SHA-256 `D006F3E43F2947776089EF03C97AF439C75DA1FCCD79397D8AE9F33E8719212B`

## Notes on the same log (not bugs)

- `AI_FALLBACK: Primary provider unavailable` is expected: the configured primary provider was not
  reachable and the local `osrs-builder:2b` fallback answered. It adds latency (~8 s for the agent
  review), but it does not block play.
- `Paused in Microbot` was an external pause; the plugin reported it and resumed when cleared.
- `RUN_HOLD` lines are now throttled to one per 20 s per reason instead of one per walk call.

## Check live

1. Restart Microbot, confirm 0.9.3.
2. Delegate a quest: expect `QUEST_HELPER_REARM`/`QUEST_RECOVER` if the helper switches itself off,
   and a 3-minute defer instead of a silent two-minute stall.
3. Fish or mine with a full-ish inventory: expect the chain to count catches continuously and settle
   (`CHAIN_PARTIAL`) instead of failing with `EFFECT_TIMEOUT`.
4. Switch goals: expect `BANK_DEPOSIT_JUNK` for leftovers before the new goal's supplies are fetched.
