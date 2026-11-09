"""Test the saved adapter on manually written decisions outside the generated training templates."""
from argparse import ArgumentParser
from contextlib import nullcontext
import json
from pathlib import Path
import os

os.environ["HF_HUB_OFFLINE"] = "1"
import torch
from peft import PeftModel
from transformers import AutoTokenizer, Qwen3_5ForConditionalGeneration

CASES = [
    ("Smithing 30. A steel bar costs 1 iron ore and 2 coal. You have iron ore and 1 coal. Bank holds 20 coal.", "WITHDRAW_ITEMS"),
    ("Smithing 30. A steel bar costs 1 iron ore and 2 coal. You have iron ore and 2 unnoted coal. Furnace is in reach.", "EXECUTE"),
    ("Smithing 29. A steel bar requires Smithing 30. Inventory and furnace are ready.", "TRAIN_LEVEL"),
    ("Crafting 43. To cut diamond you need chisel and uncut diamond. The only diamonds are noted. Bank available.", "WITHDRAW_ITEMS"),
    ("Crafting 43. To cut diamond you need chisel and uncut diamond. There is no diamond in inventory or bank. GE purchase allowed and affordable.", "BUY_INPUT"),
    ("Crafting 43. To cut diamond you need chisel and uncut diamond. There is no diamond in inventory or bank. Ironman cannot use GE.", "FIND_SOURCE"),
    ("Runecraft 20. Body ruins require a body talisman or a worn body tiara. Only an air tiara is worn. Essence ready.", "GET_ACCESS"),
    ("Runecraft 20. Body ruins require a body talisman or a worn body tiara. The character wears the body tiara and is at the ruins with essence. Choose next overall step.", "EXECUTE"),
    ("Cooking 45. Swordfish recipe needs raw swordfish. The bag has already cooked swordfish. Bank has raw swordfish.", "WITHDRAW_ITEMS"),
    ("A rune essence mine route requires Rune Mysteries. The account has the supplies to do Rune Mysteries and it is unfinished.", "DO_QUEST"),
    ("Wine input was combined but is still unfermented. Finished wine is required to verify the result.", "VERIFY_OUTPUT"),
    ("Anvil route requires an unnoted steel bar. A banked steel bar is available, and carried bars are noted.", "WITHDRAW_ITEMS"),
]


def run(model, tokenizer, adapter):
    manager = nullcontext() if adapter else model.disable_adapter()
    outcomes = []
    with manager, torch.no_grad():
        for offset in range(0, len(CASES), 4):
            block = CASES[offset:offset+4]
            prompts = []
            for case, _ in block:
                messages = [
                    {"role": "system", "content": "Choose the next OSRS action from EXECUTE, TRAIN_LEVEL, WITHDRAW_ITEMS, BUY_INPUT, FIND_SOURCE, GET_ACCESS, DO_QUEST, VERIFY_OUTPUT. Respond with a JSON object containing one action field."},
                    {"role": "user", "content": case},
                ]
                prompts.append(tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True,
                                                               enable_thinking=False))
            encoded = tokenizer(prompts, padding=True, return_tensors="pt", add_special_tokens=False)
            generated = model.generate(**encoded, do_sample=False, use_cache=True, max_new_tokens=28,
                                       pad_token_id=tokenizer.pad_token_id)
            decoded = tokenizer.batch_decode(generated[:,encoded.input_ids.shape[1]:], skip_special_tokens=True)
            for (prompt, expected), output in zip(block, decoded):
                try:
                    parsed = json.loads(output.strip())
                    actual = parsed.get("action") if isinstance(parsed, dict) else None
                except ValueError:
                    actual = None
                outcomes.append({"scenario": prompt, "expected": expected, "actual": actual,
                                 "output": output[:220], "correct": actual == expected})
    return {"correct": sum(row["correct"] for row in outcomes), "rows": len(outcomes), "cases": outcomes}


def main():
    parser = ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--run", type=Path, required=True)
    args = parser.parse_args()
    torch.set_num_threads(6)
    tokenizer = AutoTokenizer.from_pretrained(args.base, local_files_only=True)
    tokenizer.padding_side = "left"
    if tokenizer.pad_token_id is None:
        tokenizer.pad_token = tokenizer.eos_token
    base = Qwen3_5ForConditionalGeneration.from_pretrained(args.base, local_files_only=True,
                                                        dtype=torch.bfloat16, attn_implementation="eager")
    model = PeftModel.from_pretrained(base, args.run/"adapter", local_files_only=True)
    model.eval()
    print("Testing independent decisions on base", flush=True)
    before = run(model, tokenizer, False)
    print("Testing independent decisions on adapter", flush=True)
    after = run(model, tokenizer, True)
    (args.run/"independent-decisions.json").write_text(
        json.dumps({"base": before, "adapter": after}, indent=2) + "\n", encoding="utf-8"
    )
    print(f"Independent exact actions: base {before['correct']}/{before['rows']}; adapter {after['correct']}/{after['rows']}", flush=True)


if __name__ == "__main__":
    main()
