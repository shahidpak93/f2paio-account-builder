# 0.8.1 purchase-loop fix

The supplied logs show repeated BUY 1 Eye of newt, GE_COLLECT result=true and immediate missing-supply selection, from09:47 through09:58. Inventory occupied slots stayed roughly constant; that does not reveal individual item quantity/form. The player then ran low on coins and walked to the general store to sell shrimp. This is a genuine acquisition loop, not the earlier manual pause.

Root controller defect: TradeService treated the native collection return value as completed acquisition and reset the retry counter, without observing the bought inventory delta. The logs do not establish why the expected usable item was missing: note form, metadata/recognition or collection behavior need a fresh diagnostic if it recurs.

0.8.1 adds an eight-second inventory-delta receipt check after collection. Unverified purchase blocks that item for ten minutes on this TradeService instance and emits GE_RECEIPT_FAILED; no immediate repurchase. Verified gains emit GE_RECEIPT_VERIFIED. Owned noted supplies are deposited and withdrawn as items before buying again. Quest ingredient recognition uses numeric unnoted item IDs rather than relying on display names.

Tests:106 account-builder checks (five new receipt/identity cases), existing10193 checks plus13 loot/coin/world,20 learning and97chain checks pass. Native loader and full upstream client compilation pass. Patch installed; restart Microbot and confirm0.8.1. In-game fix still needs live confirmation. Existing GE offers are preserved; reconcile them before starting another session.

Separate attachment exception: com.flippingcopilot.ui.NpcHighlightOverlay.render raised a NullPointerException through ModelOutlineRenderer. That is the Copilot NPC highlight rendering path, not an AIO stack trace; it has not been patched here.
