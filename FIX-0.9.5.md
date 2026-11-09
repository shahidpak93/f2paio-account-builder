# F2P AIO 0.9.5 — quest-suppression ordering fix

Small but important correctness fix on top of 0.9.4.

The 0.9.4 quest cooldown filtered quests **after** `AccountMilestones.focus()`. When any ready quest
existed, `focus()` returned a quest-only candidate list; suppression then removed every candidate,
reset the cooldown, and left `selected == null`. That can null-pointer in selection or silently skip
a cycle.

0.9.5:

- Quest suppression runs **before** the milestone focus, and only removes quests when at least one
  non-quest candidate exists; otherwise the cooldown is released so the bot does not deadlock.
- Empty candidate list and `selected == null` are both handled explicitly (`No eligible goal after
  filters/scoring; re-evaluating`, 5 s retry) instead of proceeding with a null goal.
- Version 0.9.5, SHA-256 `250912F09F71FA72B130A85817ED2B479551D97DC3F1AB42390E884F1C88F478`.
- All offline suites and the upstream Microbot compile pass.

This makes the 0.9.4 promise real: after two quest failures, the next choice actually comes from
skilling or combat rather than an empty quest list.
