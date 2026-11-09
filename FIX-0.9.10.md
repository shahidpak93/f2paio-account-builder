# F2P AIO 0.9.10 — universal fixes, not per-case

## The bug you caught

Your log: chickens (then cows) looped forever:
`PRECONDITIONS_OK` → `TRAIN_STYLE Melee target=STRENGTH` → `Setting attack style to Impale` → repeat.
Cause: `CombatTraining.step()` clicked the stance and returned **false until the varp reported the new
style**, so `Engine` never advanced past PREPARE. After two minutes the supervisor deferred combat.

This was not melee-specific: the magic branch returned the autocast result directly, so magic would
loop the same way; equipment had the same "return false until observed" pattern for every slot.

## Universal fixes in 0.9.10

1. **Every combat style** (melee, ranged, magic): the setup click is attempted once, then the engine
   proceeds (travel/fight). Re-clicks are debounced to 8 s. No style can block PREPARE any more.
2. **Every equipment slot**: `EquipmentPolicy` uses the same attempt-once + 6 s debounce, so a slow
   equip cannot freeze preparation.
3. **Every quest**: `QuestIntegration` now reads the QuestHelper's **quest-level item requirements**
   (`getItemRequirements()`) plus the active step for *any* quest, instead of a hand-written list.
   Untradeable items remain the helper's/gathering's job; tradeable deficits become supply goals.
   The Goblin dye map stays only as a fallback.
4. **Any stalled quest**: the quest watchdog no longer resets its idle clock when it re-arms, so the
   75-second quest defer fires before the generic 2-minute supervisor; supervisor incidents on
   quests also use a 3-minute cooldown (not 10).
5. **Any activity**: the AI monitor no longer auto-REPLANs while the planner is still selecting, and
   only auto-banks leftovers when the inventory is ≥ 20 slots (no more spurious actions during
   combat prep).
6. **Any activity**: carried coins are banked unless the current activity needs them; the supply
   withdrawal path already fetches coins when a purchase is required.

Also carried over from the interrupted 0.9.9 build: coins are no longer an unconditional keep item.

## Still not universal (honest)

- Bank liquidation is one stack per GE trip; a multi-stack plan per bank visit is still missing.
- QuestHelper may still stop on a requirement it cannot express as an item (e.g. an NPC step); we
  then defer with the short cooldown rather than solving the step ourselves.
- Session/rotation budgets are time-based; there is no per-skill "until full inventory" rule beyond
  the 24-action chain.

## Verification

- All offline suites pass (10,193 checks), upstream Microbot compile passes.
- Version **0.9.10** installed, SHA-256 `C73AF508AE256D77044088A539C3B6A78CEBE3E3114935CAE2680F60FB5ACCD7`.
- Gateway restarted with the tightened monitor rules.
