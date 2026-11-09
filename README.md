# Current release 0.14.0

Read [route comparison, startup and handoff](FIX-0.14.0.md) and [offline training results](training/osrs-v014/README.md). Restart Microbot to load 0.14.0. The sections below cover earlier releases.

# F2P AIO 0.9.11

An autonomous F2P account builder for Microbot. Quests, skills, combat stats and unlocks are the objective; supplies and money support them. The dashboard reports actions and progress, with Skip and debug export.

Current shared-policy changes: observed setup effects with bounded retries for all gear/styles; reliable bank snapshots; lower bank scan/log overhead; retained run-energy handling. See [FIX-0.9.11.md](FIX-0.9.11.md).

[Startup guide](GUIDE.md) · [AI setup and training](AI-SETUP.md) · [AI handoff](HANDOFF.md) · [200 sourced tips](knowledge/TIPS-200.md)

Installed locally. Restart Microbot and confirm0.9.11. After reboot run agent_lab/StartAgents.ps1 for the local gateway. Build.ps1, Test.ps1 and Install.ps1 are the build/test/install entrypoints.

Local models, verified chain learning, online outcome ranking and incident role routing are included. Role profiles are not independently trained models. Small-model errors remain possible. Offline checks and upstream compilation passed; live0.9.11 gameplay remains unverified. The original project bible is not complete.
