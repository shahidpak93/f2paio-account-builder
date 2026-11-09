# Current release 0.14.0

Read [route comparison, startup and handoff](FIX-0.14.0.md) and [offline training results](training/osrs-v014/README.md). Restart Microbot to load 0.14.0. The sections below cover earlier releases.

# Start F2P AIO 0.9.11

1. Restart Microbot/rescan local plugins to load the installed JAR. Confirm START version0.9.11.
2. Start the gateway with agent_lab/StartAgents.ps1 after reboot. Ollama must be running. The gateway is running now.
3. Log in, enable F2P AIO and press Start. No activity selection is needed. A scheduled break displays its end time; it is respected. Microbot manual-input pause is resumed using its amber play control.
4. Watch current goal, plan, learning and agent status. Skip changes eligible goals. Export debug after a failure.

Setup may wait up to three five-second windows for a weapon, gear slot or combat style. It does not count a click as completion. Essential setup failure defers the task and notifies supervision; optional armour failures retain worn gear. Read SETUP status, EQUIP_DEFERRED, TRAIN_STYLE, REPLAN and AGENT_REVIEW events. A wrong mapping still needs correction using a live observation.

The run-energy policy is retained, with interface ownership respected. Potion use excludes notes and repeated clicks. Bank scans commit only actual successful reads. Failed reads retain previous observations rather than inventing empty stock.

Account milestones, native quests, curriculum and affordable XP tasks drive progression. Funding remains subordinate. The current release does not prove every quest, supply route or gameplay outcome works. Existing GE offers are protected; automatic restart settlement is incomplete.

Machine learning here updates account-local values from observed outcomes and verified chain trials. It does not train a general player from scratch. See AI-SETUP.md and FIX-0.9.0.md for the distinction between the actual LoRA checkpoint, model profiles, online selection and role routing.

Current changes: FIX-0.9.11.md. Previous implementation history: HISTORY-GUIDE.md. Inspect test-v0911.log, gradle-v0911.log and agent_lab/agent-tests.log for checks. Keep token files and runtime state private when sharing reports.
