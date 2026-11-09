# Audit after 0.9.8 — what was wrong, what is fixed, what is still open

Based on the 12:30–12:39 live log, the current source, and the learning state.

## Fixed in 0.9.8 (on top of 0.9.7)

1. **Bank funding could never cover a real quest cost.** `BankSalePlanner` required a *single* stack
   worth `max(250, shortfall/2)`. For Goblin Diplomacy (2,985 gp) every stack in that 30-stack bank
   was too small, so `BANK_SALE_NONE` and no funding. Now the threshold is 250 gp and the bot
   liquidates stacks one after another until the shortfall is covered.
2. **Quest item gaps were invisible to the controller.** Quest Helper reported
   `missing required item: Blue dye x1`, but our `missingTradeables()` only reads the active
   QuestHelper step, which did not expose it — so supply recovery never ran. Added a fallback to our
   own quest item data (`Requirements.questNeeds`) and added Goblin Diplomacy's three dyes.
3. **Skill chains stopped far below a full inventory.** Seed chains did 8 actions; now 24, so a
   gathering session fills more slots before banking.
4. **Agent-proposed chains could be quarantined forever.** Only `origin=synthesized` chains were
   re-probated after 20 minutes; `agent`/`evolved` chains stayed quarantined. The reset now applies to
   every non-manual origin.

## Still open (ranked)

1. **Quest item data is incomplete.** Goblin dyes are fixed, but most F2P quests have no item list in
   `Quests.requirements` (Ernest, Prince Ali, Pirate's Treasure, etc.). Until that data exists the
   same "helper stops, funding never triggers" pattern will repeat. Best fix: ingest the full F2P
   quest item table (wiki) into `Quests`, plus parse Quest Helper's "missing required item" message.
2. **Two stall detectors compete.** Our quest watchdog re-arms and resets its 75-second timer, while
   the supervisor's unchanged-inventory check fires at 2 minutes and applies the old **10-minute**
   defer. The intended 3-minute defer rarely wins. Fix: do not reset the watchdog clock on re-arm, and
   let the quest-specific watchdog own quest deferrals.
3. **Bank sale is one stack per trip.** With the new threshold, many small stacks mean repeated
   bank → GE → bank trips. A multi-stack plan per bank visit is needed.
4. **`depositUnrelated` returns true even when the deposit fails**, and only handles one item per
   call. Add a retry cap and deposit all leftovers while the bank is open.
5. **Coins are kept in the inventory while skilling** (by design for supply payments). Add an optional
   "bank carried coins above N" rule.
6. **Auto-fix is agent-callable but not chat-callable yet.** `/fix/apply` and `dev.apply_fix` work; the
   chat window still only suggests actions, not code patches. Next: a "fix code" mode that routes a
   chat message to the developer agent + `dev.apply_fix`.
7. **`/evolve` often returns `none`.** Sparse learning state + broad prompt. Improve by sending the
   eligible catalog subset, quarantined reasons and a worked example; use the developer role for
   chains.
8. **Not live-verified:** 0.9.6–0.9.8 behaviors (bank funding on a quest, per-activity banking, 24-act
   chains, 60-minute sessions, auto-fix apply/rollback). The offline suite passes but a real session
   is the proof.
9. **Chain BANK step only deposits chain products**, not unrelated leftovers (they are cleared at goal
   switch). Mid-session junk can still sit until the next goal.
10. **Quest Helper's own purchase flow can still outrun our funding** if it gives up before the bank
    sale completes; consider pausing the helper while funding is in progress (we already do it only
    when we detect a missing item).

## Evidence/limits

- Version 0.9.8 installed; SHA-256 `2772AB5031CAA40D6796B598498BC935D0836B30F66A01308E442F2F0A896B58`.
- Offline suites + upstream compile pass.
- Live log used: 12:30–12:39 session (Goblin Diplomacy → Ernest → sardine → shrimp).
