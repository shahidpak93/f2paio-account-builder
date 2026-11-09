# F2P AIO 0.10.0

## Research applied

Added110 short attributed facts to the existing200:310 facts now reference93 distinct OSRS Wiki pages. The full acquisition read98 documents covering96 distinct wiki pages,739,416 cached characters. Two attempted pages were unavailable and irrelevant/disambiguation material was excluded from rules. See knowledge/PLAYER-KNOWLEDGE.md, PLAYER-POLICIES.json, sources.json, research-manifest.json and SOURCES-DEEP.md. Raw articles remain in work, outside the package. This is public guide/mechanics knowledge, not human gameplay recordings or a new neural fine-tune.

References cover beginner/progression guides, combat styles, resource chains, transportation, account restrictions, reward eligibility and public AIO frameworks. Adapted ideas include live-condition task branches, explicit prerequisites, persistent milestones, effect-based checks, reusable skills and feedback-driven revision. No closed-source scripts were copied.

## Concrete account-building fixes

- Combat fairness now runs before quest-only milestone filtering. Previously the filter removed combat candidates before their priority bonus could matter. Due combat and skilling can now receive turns even with ready quests.
- Combat styles resolve from live weapon enum/struct metadata. Strength/Defence positions are not assumed identical for fists, daggers and other weapons. The Combat tab is opened before stance clicks. Existing bounded observed-effect verification remains.
- New method unlocks request re-evaluation after the current verified chain, or between combat segments. A higher unlocked tier is reconsidered, not automatically treated as better XP.
- Basic self-cooked foods are recognised. Injured accounts without usable food do not immediately select another combat task; emergency retreat targets the nearest bank rather than one hard-coded distant city.
- One-time Stronghold evidence reads native reward varbits and carried/equipped/banked boots, preserving account-local records. Farmable drops remain repeatable. No Stronghold reward route was added or requested.
- Corrected Rune Mysteries' unlock label to essence-mine access and removed the incorrect X Marks-to-Corsair route label. Ironman state now prevents assuming missing supplies can be bought through the GE. Full Ironman self-supply routing remains incomplete.

## Chat and developer access

Open AI chat in the plugin panel. Chat reads a bounded recent context plus live state, inventory and dialogue when available, retrieves relevant facts and retains account-separated history. Use Auto-fix with Hermes or type /repair followed by the issue. Repair jobs shows actual status, summary and errors.

Hermes is configured with an OSRS-specific home, skills, local models and actual terminal/file/internet access under the Windows user. This is not limited to runtime action suggestions. One developer job runs at a time; repeated automatic repairs have a15-minute cooldown. The regular planner remains the2B model; the developer uses the existing4B model with a2B fallback. Current Hermes requires64K context. Dependency/path problems were repaired using a shorter local SDK environment; details are in HERMES-SETUP.md.

Developer jobs work in a separate source copy, run host-owned Java checks and Python checks when appropriate, reject stale merges, and install tested changes. Failed direct source fixes restore source/artifacts and preserve a concurrent edit. Java JAR changes require Microbot reload. Tested Python changes request a gateway restart with a source rollback helper if startup fails. Runtime action preconditions remain active; a developer's terminal access does not make missing gear or false success acceptable.

The monitor polls every30 seconds and recurring technical failures can enqueue repair jobs. Incident supervision remains separate and defers the failing task immediately. Combat stalls are no longer mislabelled as quest restarts. Full automatic code-repair quality and gateway rollback recovery are not established by the connectivity probe.

## Learning loop repaired

Growth feedback no longer blocks the game loop on a network request. Results are sent asynchronously, retried if unacknowledged, and partial chain successes/failures are reported as well as promotion/quarantine. Evolution reads that previous feedback and prioritises failed/probation evidence. Proposed revisions use content-derived IDs instead of colliding on one fixed ID per goal. Quarantine retries keep historical counts separately and require fresh proof. No chain is marked proven merely because an AI proposed it.

Existing outcome-value learning and the contextual bandit continue learning from actual XP/quest/effect evidence. New declarative skills can enter live probation; new Java executors need a rebuilt/reloaded plugin. This does not train a general OSRS player from scratch.

## Verification

155 account-builder checks plus the existing suites pass:10,193 original checks,13 loot/coin/world,20 learning,97 chain,16 run-energy,14 curriculum,10 inventory recovery,16 account knowledge and7 runtime-action cases.28 Python agent tests pass. Native artifact loading and full upstream compilation pass.

Hermes directly called the terminal and observed49 in an initial probe. The authenticated developer queue then called the terminal and observed323, recording one tool call and no source changes. These prove connectivity, not a successful autonomous gameplay repair. Earlier failed setup/narration probes were retained, not counted as success. Live0.10 gameplay, complete account building and all original-bible features remain unverified.

Automatic source repair also checks running pluginVersion against current source. Unknown/old telemetry requests a reload instead of blindly changing newer code. Java or embedded-knowledge repairs automatically bump the patch version before validation, so that reload requirement is detectable. Source/artifact failures preserve evidence for the developer.

Live chat grounding: an initial small-model reply invented a server diagnosis. Added evidence-ID validation, actual provider reporting and advice-only action checks. Unsupported answers become observed-status/reference diagnostics. The final synthetic chat probe returned NONE; this is not proof that all free-form explanations are correct.

Final chat probe: a grounded account-building explanation returned in11.3s with action NONE. Auto-fix also retains the last explanation after Send clears the input box. Final Python test count28. Stronghold reward collection remains disabled as requested.
