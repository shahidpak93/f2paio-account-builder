# F2P AIO 0.9.2 — account curriculum + bank-funded GP

## Why

The recorded-session baseline (`labs/eval/BASELINE.md`) showed the bot averaging ~806 XP/hour with
ground-collection goals winning early sessions and account progress only arriving late. Two gaps:

1. **No real curriculum.** `AccountMilestones` only picked the lowest-levelled skill and a fixed
   10/20/40 target. Nothing valued a ready quest, an unlock, or the difference between a quest gate
   and a quest that is one level away.
2. **No bank liquidation.** When coins were short, funding went straight to public ground drops.
   A bank full of ores/logs/fish was ignored even though selling it is faster and safer than
   competitive scavenging. (A narrow supply path withdrew banked *method products*, but only when a
   supply purchase was already in progress and only for the first matching product.)

## What 0.9.2 does

### Curriculum (`Curriculum.java`)

Additive, bounded priority added to the planner score (no behaviour change in `MONEY` mode or when
`accountBuilder` is off):

- ready quest (levels + supplies satisfied) -> **+140**
- quest whose levels are ready but supplies are missing -> **+15** (supply recovery still progresses it)
- quest gated by levels -> **-20**
- activity for the lowest trainable F2P skill -> **+45**
- any other trainable skill below its next milestone -> **+20**
- ground loot: **0** below 150 coins (emergency), **-70** otherwise

Milestones are 10/20/40/60/80/99. The aim is persisted in the plan view and logged as `CURRICULUM <aim>
-> <method>` on every selection.

### Bank liquidation (`BankSalePlanner` + `FundingTask`)

Funding order is now: **carried products -> banked products -> public ground loot.**

- When a goal cannot be funded and no carried products can cover it, the bot chooses the
  highest-value banked stack (allow-listed bot products only; minimum liquidation value 250 GP or
  half the shortfall).
- It travels to the nearest bank, withdraws the stack **noted**, then sells it at the GE. Noted form
  keeps it to one inventory slot.
- Protection: coins, tools from the method catalogue, worn equipment, food, quest/progression
  reserved items, and production inputs are never sold.
- Failures are bounded: a failed withdrawal/sale blocks the next bank attempt for 5 minutes, then
  ground-loot funding still applies.
- New debug events: `BANK_SALE_NONE`, `BANK_SALE_WITHDRAW`, `BANK_SALE_RETRY`, `BANK_SALE_DONE`,
  `BANK_SALE_FAILED`, `BANK_SALE_MISSING`.

`FundingTask` is now constructed with `BankMemory` from both `Engine` and `SupplyService`, so the
bank plan is based on an actual bank scan rather than guessing.

## Verification

- `PASS 14 bank-sale/curriculum cases` (new offline suite `BankCurriculumTests`)
- All previous suites pass: run-energy 16, learning 20, chain 97, account-builder 124, 10,193 checks
- Jar packaging + native artifact loader pass; upstream Microbot `:client:compileJava` passes
- Version 0.9.2 installed, SHA-256 `586F74FDC7DB2BAF5F8A68DBBC2F74C9971A010ADA673780CABC6776B7107928`

## Not claimed

- No live run yet: bank withdrawal/sale and curriculum selection have not run against a real account.
- The curriculum is a rule-based scorer today, not a trained policy. The lab (`RLScape`/`rs-sdk`)
  is the next step for learning one.
- Bank liquidation only sells the bot's own production items by design; it will not empty arbitrary
  bank contents (that would risk quest items and gear).

## How to check live

Restart Microbot, confirm 0.9.2. Then:

1. With coins under the goal cost and a bank holding logs/ores/fish, expect
   `BANK_SALE_WITHDRAW ... noted`, a bank trip, a GE sell offer, and `BANK_SALE_DONE`.
2. Watch `CURRICULUM <aim> -> <method>` on each selection; ready quests should win over skilling,
   and ground loot should stop winning once coins > 150.
