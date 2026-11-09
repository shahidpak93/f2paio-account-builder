# F2P AIO 0.9.4 — account knowledge, combat/skilling balance, one-time rewards

## Why it looked like "quests or money"

Three separate causes, all in the selection layer:

1. **Quest priority was overwhelming.** The 0.9.2 curriculum gave any ready quest **+140**, while an
   ordinary skilling activity got +20/+45. Once quests existed, nothing else could win — even after a
   quest stalled and was deferred, the next quest replaced it.
2. **Combat was effectively unfundable.** `Requirements` demanded a weapon, body, legs and ten trout
   for *every* combat method. On a fresh account that cost more than the balance, so the affordable
   filter removed combat entirely. It never trained Attack, even on chickens where fists are enough.
3. **No knowledge of one-time opportunities or unlocks.** The bot had no concept of "this reward is
   once per account" or "this area/skill is now unlocked", so it could neither value the Stronghold's
   10,000 coins nor guarantee it would not repeat a one-time claim.

## What 0.9.4 does

### Account knowledge (`AccountKnowledge`)

- A per-account persisted one-time ledger (`learning/<account>-unlocks.json`). A reward is claimed by
  name once and is never offered again, across restarts.
- Known one-time opportunity: **Stronghold of Security — 10,000 coins + Fancy/Fighting boots**
  (registered as *known*, `executable=false`; see the note below).
- Owning the reward item (e.g. Fancy boots) also marks it done, so a missing ledger cannot cause a
  repeat claim.
- Quest unlocks are recognised: Rune Mysteries → Runecrafting, The Corsair Curse → Corsair Cove,
  Below Ice Mountain → Camdozaal, X Marks the Spot → boat route, Dragon Slayer I → rune platebody.
- Readiness flags for combat and skilling, plus a per-session `ACCOUNT_SCAN` log line and a plan-view
  line: one-time done/total, quests done, unlocks, combat ready, skilling ready.
- **Farmable vs one-time:** quests and registered rewards are exclusive; every skilling/gathering
  method (logs, ores, fish, drops) stays repeatable by design, which is what the test asserts.

### Combat training is finally reachable

- New `CombatPolicy`: safe melee training (`chickens`, `cows`) requires **no food, weapon or armour**
  — fists are enough. Ranged/magic training still needs its bow/staff + ammo/runes, and higher-risk
  combat keeps the food requirement.
- New category rule: if combat has not been chosen in the last 10 minutes it gets **+60**; otherwise
  +10. Skilling gets a **+15** boost when it has been ignored. Quests keep +140, but they can no longer
  monopolise: after **two quest failures** (stall, supply failure, chain failure) quests are suppressed
  for **10 minutes** (`QUEST_COOLDOWN`), forcing skilling/combat; the counter resets on a completed
  quest.

### "Already done? Never again."

- Finished quests were already filtered by `FINISHED`.
- One-time rewards are now filtered by the persisted ledger *and* by owning the reward item.
- Farmable resources are intentionally not filtered.

## The Stronghold note

Per your instruction ("don't actually go — it's for reference"), the route is **not implemented**. The
bot now *knows* the opportunity, shows it in the account scan, values it as one-time, and will never
repeat it. Executing the four-level route (doors, ladders, chest, boots choice) is a separate job and
needs live verification; the registry is already in place for it.

## Verification

- New suite: `PASS 16 account-knowledge/one-time/combat cases`
- All previous suites pass: run energy 16, bank/curriculum 14, runtime recovery 10, learning 20,
  chain 97, account-builder 124, 10,193 offline checks
- Upstream Microbot `:client:compileJava`: BUILD SUCCESSFUL
- Version **0.9.4**, SHA-256 `988A896524CF300295197159CBBE484B1C06C595D56B87EF547B0F928AD503BF`

## Check live

1. Restart Microbot, confirm 0.9.4.
2. At session start expect `ACCOUNT_SCAN: One-time: 0/1 done | quests done N | unlocks ... | combat ready | skilling ready`.
3. With no GP and no gear, chickens/cows should now be selectable (CURRICULUM Combat training) and
   should not demand trout or a sword.
4. After two quest failures expect `QUEST_COOLDOWN` and a skilling/combat goal next.
5. The plan view shows the one-time ledger; it must never claim Stronghold twice.
