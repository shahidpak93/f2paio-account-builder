# 0.9.11 shared policy fixes

Reviewed the attachment against the actual workspace, which already contained changes through0.9.10. Preserved run-energy handling, native quest item discovery, curriculum, account knowledge, AI chat/monitor/evolution, existing outcome learning and latest recovery changes.

## Shared fixes

ActionSettlement replaces attempt-and-assume-success in every equipment slot and melee/ranged/magic setup. It attempts once, waits five seconds for the real state, retries at most three times and then reports failure. READY requires observation, not a click return. Account/target changes get a separate attempt budget; manual pauses do not consume the wait. This avoids both endless PREPARE and fighting with an unconfirmed style.

SetupCoordinator applies consistent outcome handling across all combat profiles. Failed essential weapon/ammo/style setup defers the goal and notifies the supervisor. Failed optional armour upgrades are skipped for the session while retaining worn gear. Setup waits while a blocking dialogue/bank/shop/GE interface owns input. No monster-specific name or coordinate is used by these rules. Existing weapon widget mappings still require live verification; incorrect mappings now produce a bounded diagnostic rather than silently counting as success.

BankMemory commits only successful snapshots. A failed read cannot turn unknown bank stock into a known-empty bank or erase previously observed items. Reads are limited to once per second, and BANK_SCAN logs only changes in item IDs/quantities. This applies to every bank and activity.

Run-energy changes from0.9.10 are preserved. Carried energy potion use is debounced, excludes notes and respects interface ownership. Corrected the stale statement that energy potions are members-only: they are available in F2P. Source: https://oldschool.runescape.wiki/w/Energy_potion

## Validation

136 account-builder/setup/bank checks,16 run-energy,14 bank/curriculum,10 inventory/chain recovery,16 account-knowledge and7 AI-whitelist cases pass, alongside the existing10193 checks,13 loot/coin/world,20 learning and97 chain checks. Fifteen Python agent tests pass. Native artifact loading and full upstream Microbot compilation pass. Installed JAR hash is checked against the built artifact in STATUS.json.

Restart Microbot and confirm0.9.11. No live0.9.11 gameplay success is claimed. These are shared policy fixes, not proof that every quest, combat weapon, or original-bible feature works. Full automatic restart settlement, complete item-source planning and live regression coverage remain unfinished.
