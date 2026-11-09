"""Small supervised LoRA run; keeps source/held-out inputs separate and records real weight updates."""
import argparse,hashlib,json,math,random,time,os
from pathlib import Path
os.environ['HF_HUB_OFFLINE']='1'
os.environ['TOKENIZERS_PARALLELISM']='false'
import torch
from transformers import AutoTokenizer,Qwen3_5ForConditionalGeneration
from peft import LoraConfig,TaskType,get_peft_model
parser=argparse.ArgumentParser(); parser.add_argument('--base',required=True); parser.add_argument('--batch',type=int,default=2)
parser.add_argument('--dataset',type=Path)
parser.add_argument('--output',type=Path)
parser.add_argument('--no-merge',action='store_true')
parser.add_argument('--sample-eval',type=int,default=0)
args=parser.parse_args()
root=args.dataset or Path(__file__).resolve().parent
results=args.output or root
results.mkdir(parents=True,exist_ok=True)
out=results/'adapter'
out.mkdir(exist_ok=True)
torch.set_num_threads(6); torch.manual_seed(42); random.seed(42)
train=[json.loads(x) for x in (root/'train.jsonl').read_text(encoding='utf-8').splitlines()]
held=[json.loads(x) for x in (root/'heldout.jsonl').read_text(encoding='utf-8').splitlines()]
print('Loading base',flush=True)
tokenizer=AutoTokenizer.from_pretrained(args.base,local_files_only=True)
if tokenizer.pad_token_id is None: tokenizer.pad_token=tokenizer.eos_token
model=Qwen3_5ForConditionalGeneration.from_pretrained(args.base,local_files_only=True,dtype=torch.bfloat16,attn_implementation='eager')
model.config.use_cache=False
config=LoraConfig(r=8,lora_alpha=16,lora_dropout=0.0,target_modules=['q_proj','v_proj','o_proj','up_proj','down_proj'],task_type=TaskType.CAUSAL_LM)
model=get_peft_model(model,config); model.print_trainable_parameters()
def encode(row):
 messages=row['messages']; prefix=tokenizer.apply_chat_template(messages[:-1],tokenize=False,add_generation_prompt=True,enable_thinking=False)
 p=tokenizer(prefix,add_special_tokens=False)['input_ids']; a=tokenizer(messages[-1]['content']+tokenizer.eos_token,add_special_tokens=False)['input_ids']
 if len(p)+len(a)>192: p=p[-(192-len(a)):]
 return p+a,[-100]*len(p)+a
def batch(rows):
 encoded=[encode(r) for r in rows]; size=max(len(x[0]) for x in encoded)
 ids=torch.tensor([x+[tokenizer.pad_token_id]*(size-len(x)) for x,y in encoded]); labels=torch.tensor([y+[-100]*(size-len(y)) for x,y in encoded])
 return ids,labels,(ids!=tokenizer.pad_token_id).long()
def loss(rows):
 ids,labels,mask=batch(rows); begin=max(1,int((labels!=-100).nonzero()[:,1].min()))
 keep=torch.arange(begin-1,ids.shape[1]-1)
 output=model(input_ids=ids,attention_mask=mask,logits_to_keep=keep,use_cache=False)
 target=labels[:,begin:]
 value=torch.nn.functional.cross_entropy(output.logits.float().reshape(-1,output.logits.shape[-1]),target.reshape(-1),ignore_index=-100)
 return value,output.logits,target
def evaluate():
 model.eval(); total=0.; count=correct=tokens=0
 with torch.no_grad():
  for offset in range(0,len(held),args.batch):
   rows=held[offset:offset+args.batch]; value,logits,target=loss(rows); total+=value.item()*len(rows); count+=len(rows)
   valid=target!=-100; correct+=((logits.argmax(-1)==target)&valid).sum().item(); tokens+=valid.sum().item()
 model.train(); return {'loss':total/max(1,count),'teacher_forced_token_accuracy':correct/max(1,tokens),'rows':count}
def fingerprint():
 h=hashlib.sha256()
 for name,p in model.named_parameters():
  if p.requires_grad: h.update(name.encode()); h.update(p.detach().float().numpy().tobytes())
 return h.hexdigest()
def exact_match():
 if not args.sample_eval:
  return None
 stride=max(1,len(held)//args.sample_eval)
 rows=held[::stride][:args.sample_eval]
 model.eval()
 old_padding=tokenizer.padding_side
 tokenizer.padding_side='left'
 outcomes=[]
 try:
  for offset in range(0,len(rows),4):
   block=rows[offset:offset+4]
   prompts=[tokenizer.apply_chat_template(row['messages'][:-1],tokenize=False,add_generation_prompt=True,enable_thinking=False) for row in block]
   encoded=tokenizer(prompts,return_tensors='pt',padding=True,add_special_tokens=False)
   with torch.no_grad():
    generated=model.generate(**encoded,max_new_tokens=28,do_sample=False,use_cache=True,pad_token_id=tokenizer.pad_token_id)
   answers=tokenizer.batch_decode(generated[:,encoded.input_ids.shape[1]:],skip_special_tokens=True)
   for row,answer in zip(block,answers):
    wanted=json.loads(row['messages'][-1]['content'])
    try:
     actual=json.loads(answer.strip())
    except (ValueError,TypeError):
     actual=None
    outcomes.append({'group':row.get('group'),'expected':wanted,'actual':actual,'correct':actual==wanted})
 finally:
  tokenizer.padding_side=old_padding
  model.train()
 return {'rows':len(outcomes),'correct':sum(row['correct'] for row in outcomes),'cases':outcomes}
print('Evaluating held-out baseline',flush=True)
before=evaluate(); before['generated_exact_match']=exact_match()
print('BASELINE',json.dumps(before),flush=True)
start_hash=fingerprint(); optimizer=torch.optim.AdamW([p for p in model.parameters() if p.requires_grad],lr=2e-4,weight_decay=.01)
started=time.time(); losses=[]; model.train()
for step,offset in enumerate(range(0,len(train),args.batch),1):
 optimizer.zero_grad(set_to_none=True); value,_,_=loss(train[offset:offset+args.batch]); value.backward(); torch.nn.utils.clip_grad_norm_(model.parameters(),1.0); optimizer.step(); losses.append(value.item())
 if step==1 or step%5==0:
  status={'step':step,'steps':math.ceil(len(train)/args.batch),'loss':value.item(),'elapsedSeconds':time.time()-started}
  (results/'training-status.json').write_text(json.dumps(status),encoding='utf-8'); print('TRAIN',json.dumps(status),flush=True)
print('Evaluating tuned model',flush=True); after=evaluate(); after['generated_exact_match']=exact_match(); end_hash=fingerprint()
model.save_pretrained(out); tokenizer.save_pretrained(out)
report={'base':'Qwen/Qwen3.5-0.8B','device':'cpu','dtype':'bfloat16','trainRows':len(train),'heldoutRows':len(held),'epochs':1,'steps':len(losses),'elapsedSeconds':time.time()-started,'baseline':before,'tuned':after,'trainableHashBefore':start_hash,'trainableHashAfter':end_hash,'weightsChanged':start_hash!=end_hash,'qualityGate':after['loss']<=before['loss'],'meanTrainLoss':sum(losses)/len(losses),'scope':'OSRS grounded-answer formatting and controller decisions; not full-game mastery'}
(results/'training-report.json').write_text(json.dumps(report,indent=2),encoding='utf-8'); print('REPORT',json.dumps(report),flush=True)
if not args.no_merge:
 print('Merging export',flush=True); merged=model.merge_and_unload(); export=Path(args.base).resolve().parent/'osrs-qwen08-merged'; merged.save_pretrained(export,safe_serialization=True); tokenizer.save_pretrained(export)
 print('MERGED',export,flush=True)
