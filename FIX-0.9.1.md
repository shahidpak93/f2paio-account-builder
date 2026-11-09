# F2P AIO 0.9.1 — run energy

## Why it never used the run button

Two separate things kept every trip at walking speed:

1. `F2pAioScript.run()` forced `Microbot.enableAutoRunOn = false` when a session started. That switch is what Microbot's inherited `Script.run()` and the walker's `requestAutoRun()` use to turn the run orb on. With it off, the framework stopped managing run for this plugin.
2. The plugin's own walk helper only looked for a carried "Energy potion" and drank it below 20% energy. It never clicked the orb, and F2P has no energy potions, so that branch never ran either.

The result: travel, supply trips, funding trips and native quest routes were all walked, roughly twice as slow as a player doing the same route. This was not a Microbot bug and not the manual-pause bug from earlier logs.

## What 0.9.1 does

- `RunEnergy` is the single owner of the run-orb decision:
  - Long trip (>= 10 tiles) with at least 20% energy and the orb off -> turn run on.
  - Short repositioning -> no click.
  - Energy below 20% -> walk this trip, log why, retry on a later trip.
  - Dialogue, bank, shop or GE open -> no click; that click steals interface focus and is a known way to break quest dialogue and GE offer entry.
  - Once on, run stays on so it resumes as energy regenerates.
  - Clicks are spaced by at least 3 seconds, so a missed click can never become a click loop.
- `Microbot.enableAutoRunOn` is no longer forced off. Microbot's inherited auto-run stays available (it already refuses to click during blocking interfaces), which also covers native quest-script walking during `DELEGATE`. If you disabled auto-run in Microbot's own settings, that setting is still respected; the plugin's long-trip policy works independently.
- `GameState` now snapshots run energy and orb state. The heartbeat logs `run=on/off NN%`, the travel status shows `(run NN% on)`, and the debug log records `RUN_STATE`, `RUN_ENABLE` and `RUN_HOLD`.
- The activity planner now charges a slightly larger travel penalty when the account has no energy, because those tiles are walked. The penalty stays bounded (max 15 points) and only tilts close choices.
- The members-only energy-potion fallback is kept as-is; it is unreachable in F2P.

## What is not claimed

This is offline-verified, not live-verified. The offline suite covers 16 new run-energy policy cases and all previous suites still pass. No live account run has confirmed the orb actually toggles in game or that travel time improved, because that needs a Microbot restart and a real session.

## How to confirm

1. Restart Microbot and confirm the plugin reports version 0.9.1.
2. Start a session with a destination at least 10 tiles away and at least 20% energy.
3. Expect `F2P AIO RUN_ENABLE: energy=NN% tiles=NN trigger=walk observed=true` in the debug log, the run orb on, and the travel status line showing the run percentage.
4. If energy is low, expect `RUN_HOLD` followed by `RUN_ENABLE` on a later trip. If an interface is open, expect `RUN_HOLD` with `reason=blocking interface owns input` and no click.

Unrelated warnings in the same log (for example `c.f.model.AccountStatusManager` item-container reads, or the native exit-portal config message) are not from this plugin and are unchanged.
