package com.example.f2paio;
import java.util.*;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
public final class AccountBuilderTests {
    private static int checks;
    private static void check(boolean ok,String reason) {checks++;if(!ok) throw new AssertionError(reason);}
    private static GameState state(Map<Skill,Integer> overrides,boolean dragon) {
        var levels=new EnumMap<Skill,Integer>(Skill.class); var xp=new EnumMap<Skill,Integer>(Skill.class);
        for(Skill skill:Skill.values()) if(skill!=Skill.OVERALL) {levels.put(skill,1);xp.put(skill,0);}
        levels.putAll(overrides);
        return new GameState("offline",new WorldPoint(3200,3400,0),0,1,false,false,100,false,levels,xp,
            Map.of("Dragon Slayer I",dragon?"FINISHED":"NOT_STARTED","Sheep Shearer","NOT_STARTED"),1000);
    }
    static void run() {
        check(Requirements.FOOD.contains("Shrimps") && Requirements.FOOD.contains("Cooked chicken") && Requirements.FOOD.contains("Cooked meat"),"Newbie combat can use self-cooked food rather than only expensive fish");
        GameState injured=new GameState("injured",new WorldPoint(3200,3400,0),0,0,false,false,40,false,state(Map.of(),false).levels,state(Map.of(),false).experience,Map.of(),0);
        check(new GoalGraph().eligible(injured,new F2pAioConfig(),Set.of()).stream().noneMatch(m->m.kind==Method.Kind.COMBAT),"Injured accounts without food do not immediately start another combat task");
        ChainLibrary retryLibrary=new ChainLibrary(FilesStore.ROOT.resolve("learning/retry-proof.json"));
        ChainPlan retryPlan=ChainPlan.seed(Methods.get("trees"));retryLibrary.propose(retryPlan);
        for(int i=0;i<3;i++) retryLibrary.observe(retryPlan,true,1,1000,25,0,"",new Random(1),1000+i);
        retryLibrary.observe(retryPlan,false,0,1000,0,0,"test failure",new Random(1),2000);
        retryLibrary.observe(retryPlan,false,0,1000,0,0,"test failure",new Random(1),3000);
        retryLibrary.choose(Methods.get("trees"),new Random(1),3000+21*Tuning.MINUTE);
        check(retryLibrary.evidence(retryPlan.id).success==0 && retryLibrary.evidence(retryPlan.id).historicalSuccess==3,"Retrying quarantined plans preserves history but requires fresh proof");
        retryLibrary.observe(retryPlan,true,1,1000,25,0,"",new Random(1),4000+21*Tuning.MINUTE);
        check(retryLibrary.evidence(retryPlan.id).status.equals("PROBATION"),"One successful retry cannot instantly reuse old promotion evidence");
        ChainPlan firstDraft=ChainPlan.seed(Methods.get("trees"));ChainPlan revised=firstDraft.copy();revised.verifySeconds++;
        check(!ChainPlan.contentId(firstDraft).equals(ChainPlan.contentId(revised)),"Revised plans do not collide with a fixed per-goal agent ID");
        check(ChainPlan.contentId(firstDraft).equals(ChainPlan.contentId(firstDraft.copy())),"Identical content is still detected as duplicate");
        var basic = Methods.get("trees");
        var oak = Methods.get("oak");
        check(ProgressionPolicy.hasBetter(basic,List.of(basic,oak),m->0),"Better eligible route requests a replan");
        check(!ProgressionPolicy.hasBetter(oak,List.of(basic,oak),m->0),"No replan when already on the better route");
        var built = state(Map.of(Skill.ATTACK,32,Skill.STRENGTH,30,Skill.DEFENCE,25,Skill.HITPOINTS,30,
            Skill.RANGED,30,Skill.MAGIC,30,Skill.FISHING,50,Skill.MINING,40,Skill.WOODCUTTING,60),false);
        var choices = new GoalGraph().eligible(built,new F2pAioConfig(),Set.of());
        var frontier = ProgressionPolicy.frontier(choices).stream().map(m->m.id).collect(java.util.stream.Collectors.toSet());
        check(frontier.containsAll(Set.of("hill_giants","range_hill_giants","mage_hill_giants","willow","iron","trout")),
            "All combat styles and XP-efficient gathering tiers progress on a developed account");
        check(Collections.disjoint(frontier,Set.of("chickens","range_chickens","mage_chickens","trees","copper","shrimp","yew")),
            "Progression blocks starter methods and slower high-level methods from planner and AI");
        check(!CombatPolicy.ready(Methods.get("hill_giants"),state(Map.of(Skill.ATTACK,40),false)),
            "Attack alone does not qualify an account for higher-risk combat");
        check(ProgressionPolicy.frontier(List.of(Methods.get("chickens"))).get(0).id.equals("chickens"),
            "Starter methods remain fallback when there is no better eligible method");
        var budgeted = state(Map.of(Skill.WOODCUTTING,20,Skill.FIREMAKING,20),false);
        var logs = List.of(new ResourceItem(1351,"Bronze axe",1),new ResourceItem(590,"Tinderbox",1));
        var recipe = RecipeChains.plan(Methods.get("burn_oak"),budgeted,logs,Set.of(),id->10000);
        check(recipe!=null && ChainPlan.valid(recipe) && recipe.steps.get(2).method.equals("oak")
            && recipe.steps.get(6).method.equals("burn_oak"),"A funded gathering dependency enables an otherwise unaffordable recipe");
        check(RecipeChains.plan(Methods.get("burn_oak"),budgeted,logs,Set.of("oak"),id->10000)==null,
            "Recipe synthesis respects skipped or unavailable gatherers");
        var smith = state(Map.of(Skill.MINING,9,Skill.SMITHING,1),false);
        smith.ironman = true;
        var smithSupplies = List.of(new ResourceItem(1265,"Bronze pickaxe",1),new ResourceItem(2347,"Hammer",1));
        var smithPlan = RecipeChains.plan(Methods.get("forge_bronze"),smith,smithSupplies,Set.of(),id->10_000);
        check(smithPlan!=null && smithPlan.steps.size()==17 && smithPlan.steps.get(10).output.equals("Bronze bar")
            && smithPlan.steps.get(14).method.equals("forge_bronze"),
            "Backward chain solves two ore suppliers, smelting and forging with a verified material handoff");
        check(RecipeChains.plan(Methods.get("forge_bronze"),smith,smithSupplies,Methods.all().stream().filter(m->m.products.contains("Copper ore"))
            .map(m->m.id).collect(java.util.stream.Collectors.toSet()),id->10_000)==null,
            "An unavailable prerequisite makes the dependent forge chain ineligible for this Ironman");
        var unlockState = new GameState("offline",smith.location,0,0,false,false,100,false,smith.levels,
            smith.experience,Map.of("The Knight's Sword","NOT_STARTED"),1000);
        var questPath = UnlockPlanner.next(List.of(Methods.get("copper"),basic),unlockState,new F2pAioConfig());
        check(questPath!=null && questPath.quest.id.equals("knights_sword")
            && questPath.steps.equals(List.of(Methods.get("copper"))),"Plan from an unfinished quest back to mining");
        check(UnlockPlanner.next(List.of(Methods.get("copper"),basic),smith,new F2pAioConfig())==null,
            "Missing quest state cannot create a fabricated milestone");
        var learner = new GoalLearning(FilesStore.ROOT.resolve("learning/test-goal-q.json"));
        var gained = new EnumMap<Skill,Integer>(smith.experience);
        gained.put(Skill.WOODCUTTING,960);
        var woodXp = new GameState("offline",smith.location,960,0,false,false,100,false,smith.levels,gained,Map.of(),1000);
        for(int i=0;i<5;i++) {
            learner.begin(basic,smith,true,0);
            learner.finish(woodXp,120_000,false);
            learner.learn(woodXp,List.of(),m->true);
        }
        learner.begin(Methods.get("copper"),smith,true,0);
        learner.finish(smith,120_000,false);
        learner.learn(smith,List.of(basic),m->true);
        check(learner.bonus(Methods.get("copper"),smith,true)>0,
            "Temporal difference credit propagates a later verified reward across a zero-progress action");
        check(learner.bonus(basic,smith,true)>learner.bonus(oak,smith,true)
            && learner.bonus(basic,smith,false)==0,
            "Live ranking term differentiates practiced methods and supply contexts");
        var reloaded = new GoalLearning(FilesStore.ROOT.resolve("learning/test-goal-q.json"));
        check(reloaded.bonus(basic,smith,true)==learner.bonus(basic,smith,true),"Verified policy survives reload");
        var steel = Methods.get("smelt_steel");
        var coalState = state(Map.of(Skill.SMITHING,30),false);
        coalState.items = List.of(new ResourceItem(440,"Iron ore",1),new ResourceItem(453,"Coal",1));
        check(new Requirements().missing(steel,coalState).stream().anyMatch(i -> i.id==453),
            "Downloaded recipe minimum stops smelting steel with just one coal");
        coalState.items = List.of(new ResourceItem(440,"Iron ore",1),new ResourceItem(453,"Coal",2));
        check(new Requirements().missing(steel,coalState).isEmpty(),"Steel route admits two coal and one ore");
        check(Methods.get("craft_body").level==20 && Methods.get("cut_diamond").level==43
            && Methods.get("cook_swordfish").level==45,"New F2P progression methods are executable catalogue entries");
        var rc = state(Map.of(Skill.RUNECRAFT,20),false);
        rc.items=List.of(new ResourceItem(1436,"Rune essence",20));
        var body=F2pProduction.get("craft_body");
        check(new Requirements().missing(Methods.get("craft_body"),rc).stream().anyMatch(i -> i.id==body.accessId),
            "Missing altar access blocks crafting");
        rc.equipment=List.of(new ResourceItem(body.wearableId,"Body tiara",1));
        check(new Requirements().missing(Methods.get("craft_body"),rc).isEmpty(),"Worn body tiara satisfies altar access");
        var leather = state(Map.of(Skill.CRAFTING,18),false);
        var supplies = List.of(new ResourceItem(1733,"Needle",1),new ResourceItem(1734,"Thread",10),new ResourceItem(1739,"Cowhide",10));
        var tailor=RecipeChains.plan(Methods.get("leather_chaps"),leather,supplies,Set.of(),id->10_000);
        check(tailor!=null && tailor.steps.get(2).method.equals("tan_soft")
            && tailor.steps.get(2).output.equals("Leather"),"Manifest crafting route generates a verified tanning prerequisite");
        var local = new GameState("offline",basic.location,0,0,false,false,100,false,smith.levels,
            smith.experience,Map.of(),1000);
        double human=HumanWorkflowPrior.bonus(basic,local,true);
        check(HumanWorkflowPrior.observations()>=100 && human>0 && human<=4,
            "Held-out tested demonstration profile is loaded and affects eligible local ranking");
        check(HumanWorkflowPrior.bonus(basic,local,false)==0 && HumanWorkflowPrior.bonus(basic,local,true)
            >HumanWorkflowPrior.bonus(basic,smith,true),"Demonstration prior favors stocked nearby work only");
        var oakMethod=Methods.get("oak");
        var fireGoal=Methods.get("burn_oak");
        var nearby = new GameState("offline",oakMethod.location,0,2,false,false,100,false,
            budgeted.levels,budgeted.experience,Map.of(),6000);
        nearby.items=List.of(new ResourceItem(1351,"Bronze axe",1),new ResourceItem(590,"Tinderbox",1));
        var fromOak=RecipeChains.variants(fireGoal,nearby,List.of(),Set.of(),id->500);
        check(!fromOak.isEmpty(),"Generated gathering branch is available");
        var decisionBook=new ChainLibrary(FilesStore.ROOT.resolve("learning/test-route-forecast.json"));
        var gather=decisionBook.choose(fireGoal,new Random(2),10_000,fromOak,p->true,
            new RouteForecast(nearby,List.of(),id->500));
        check(gather.steps.get(2).method.equals("oak"),"Production selector chooses a nearby gathering chain over a costly GE detour");
        var stock=List.of(new ResourceItem(1521,"Oak logs",8));
        var draw=decisionBook.choose(fireGoal,new Random(2),10_100,List.of(),
            p->!p.origin.equals("recipe-dependency"),new RouteForecast(nearby,stock,id->500));
        check(draw.id.equals("chain_burn_oak"),"Same production goal chooses direct bank preparation with observed stock");
        var recovery=new ChainRepair();
        check(recovery.alternative(gather,gather.steps.get(3),"MATERIAL_SHORTFALL: Oak logs x8")
            && recovery.excluded(Set.of()).contains("oak") && !recovery.accepts(gather) && recovery.accepts(draw),
            "Recovery blocks the failed producer but keeps an alternate route to the same goal");
        check(!recovery.alternative(draw,draw.steps.get(0),"BACKEND: supply stalled"),
            "Recovery stops after one alternate attempt");
        recovery.clear();
        check(!recovery.alternative(draw,draw.steps.get(3),"EFFECT_TIMEOUT: burn_oak"),
            "Failure in the final method is not disguised as a supply branch failure");
        var storedLogs = new ArrayList<>(logs);
        storedLogs.add(new ResourceItem(1521,"Oak logs",10));
        check(RecipeChains.plan(Methods.get("burn_oak"),budgeted,storedLogs,Set.of(),id->10000)==null,
            "Use banked inputs before sending the account to gather");
        check(!GearUpgrades.worthBuying("neck",100,110,20,1000)
            && !GearUpgrades.worthBuying("tool",300,400,1050,1000)
            && GearUpgrades.worthBuying("weapon",300,400,600,1000),"GE decisions weigh gain and price against reserve");
        check(BuilderBudget.affordable(List.of(new ItemNeed(333,"Trout",8)),built,
            List.of(new ResourceItem(329,"Salmon",8)),id->10000),"Existing bank food qualifies for the combat supply budget");
        check(ProgressionPolicy.frontier(List.of(oak,Methods.get("willow")),
            m -> m==oak?30_000:20_000).contains(oak),"Comparable sustained XP can keep a faster intermediate tier eligible");
        AccountKnowledge bankRewards=new AccountKnowledge(FilesStore.ROOT.resolve("test-bank-rewards.json"));
        bankRewards.refresh(state(Map.of(),false),List.of(new ResourceItem(9006,"Fancier boots",1)));
        check(bankRewards.done("stronghold_of_security"),"Banked reward evidence prevents repeated claims after gear is deposited");
        GameState rewarded=state(Map.of(),false);rewarded.rewardFlags=Map.of("sos_flap",true,"sos_doh",true,"sos_idea",true,"sos_stamp",true);
        AccountKnowledge rewardBook=new AccountKnowledge(FilesStore.ROOT.resolve("test-rewards.json"));rewardBook.refresh(rewarded);
        check(rewardBook.done("stronghold_of_security"),"Claimed reward varbits remain recognised even without carried boots");
        check(rewardBook.done("sos_floor1_coins") && rewardBook.done("sos_floor3_coins"),"Individual one-time coin floors are tracked");
        GameState restricted=state(Map.of(Skill.CRAFTING,10),false);restricted.ironman=true;
        check(!BuilderBudget.affordable(Methods.get("string_gold"),restricted,List.of(),id->1),"Coins cannot make GE-only missing materials available to Ironman");
        check(CombatStyleResolver.choose(new String[]{"ACCURATE","AGGRESSIVE","DEFENSIVE"},Skill.DEFENCE)==2,"Unarmed three-style layouts use the actual defensive index");
        check(CombatStyleResolver.choose(new String[]{"ACCURATE","AGGRESSIVE","CONTROLLED","DEFENSIVE"},Skill.DEFENCE)==3,"Four-style layouts remain supported");
        check(CombatStyleResolver.choose(new String[]{"ACCURATE","CONTROLLED","AGGRESSIVE","DEFENSIVE"},Skill.STRENGTH)==2,"Strength is not assumed to be the second button");
        check(CombatStyleResolver.choose(new String[]{"CONTROLLED"},Skill.STRENGTH)==-1,"Unavailable pure style cannot silently train unwanted skills");
        Curriculum fair=new Curriculum();
        var mixed=List.of(Methods.get("cooks"),Methods.get("chickens"),Methods.get("trees"));
        check(fair.balance(mixed,1000).stream().allMatch(m->m.kind==Method.Kind.COMBAT),"Ready quests cannot filter out due combat before ranking");
        fair.note(Methods.get("chickens"),1000);
        check(fair.balance(mixed,2000).stream().allMatch(m->m.kind==Method.Kind.OBJECT),"Skilling gets a turn after combat");
        fair.note(Methods.get("trees"),2000);
        check(fair.balance(mixed,3000).stream().anyMatch(m->m.kind==Method.Kind.QUEST),"Quest choices return after other families get a turn");
        BankMemory unknownBank=new BankMemory();
        check(!unknownBank.update(null,1000) && !unknownBank.known(),"Failed bank read stays unknown rather than inventing a known empty bank");
        check(unknownBank.update(List.of(new ResourceItem(995,"Coins",200)),2000) && unknownBank.count("Coins")==200,"Successful bank snapshot commits atomically");
        check(!unknownBank.update(null,3000) && unknownBank.count("Coins")==200,"Transient bank read failure retains last observed stock");
        ActionSettlement gate=new ActionSettlement(3,5000);
        check(gate.poll("style",false,1000)==ActionSettlement.Result.ATTEMPT,"Setup attempts first action");
        check(gate.poll("style",false,2000)==ActionSettlement.Result.WAIT,"Dispatch cannot claim completion");
        check(gate.poll("style",false,6000)==ActionSettlement.Result.ATTEMPT,"Setup retries after settlement window");
        check(gate.poll("style",false,11000)==ActionSettlement.Result.ATTEMPT,"Last bounded retry");
        check(gate.poll("style",false,16000)==ActionSettlement.Result.FAILED,"Unchanged setup fails instead of blocking forever or proceeding blindly");
        check(gate.poll("style",true,17000)==ActionSettlement.Result.READY,"Only actual observed state readies setup");
        check(gate.poll("style",false,18000)==ActionSettlement.Result.ATTEMPT,"A previously verified action can be retried if state later changes");
        check(gate.poll("other-account",false,18100)==ActionSettlement.Result.ATTEMPT,"Account/target change gets an independent attempt budget");
        gate.pause(19000);
        check(gate.poll("other-account",false,100000)==ActionSettlement.Result.WAIT,"Pauses do not consume action settlement timeout");
        OfferJournal.account("offline-offers");OfferJournal.submitted("ONE",221,"Eye of newt",1,10,false);
        OfferJournal.state("ONE","RECEIPT_FAILED");
        try {
            String journal=java.nio.file.Files.readString(FilesStore.ROOT.resolve("learning/offline-offers-offers.json"));
            check(journal.contains("RECEIPT_FAILED") && journal.contains("221"),"Offer identity and receipt state persist for restart reconciliation");
        } catch(Exception ex) {throw new AssertionError(ex);}
        var milestoneFile=FilesStore.ROOT.resolve("test-milestone.json");
        AccountMilestones milestone=new AccountMilestones(milestoneFile);
        check(milestone.focus(List.of(Methods.get("trees")),state(Map.of(),false)).get(0).id.equals("trees"),"Milestone uses a supported affordable candidate");
        AccountMilestones again=new AccountMilestones(milestoneFile);again.focus(List.of(Methods.get("trees")),state(Map.of(),false));
        check(again.view().contains("WOODCUTTING -> 10"),"Skill milestone survives restart");
        IncidentDetector detector=new IncidentDetector();
        GameState still=state(Map.of(),false);
        check(detector.observe("trees",still,1000)==null,"New goal starts an incident baseline");
        check(detector.observe("trees",still,122000)!=null,"Idle stalls detected regardless of execution phase");
        detector.reset();detector.observe("trees",still,1000);detector.pause(2000);
        check(detector.observe("trees",still,500000)==null,"Manual pause time cannot cause a false incident");
        detector.reset();detector.observe("trees",still,1000);
        still.moving=true;
        check(detector.observe("trees",still,122000)==null,"Legitimate travel is not idle failure");
        check(detector.observe("trees",still,362000)!=null,"Movement alone cannot hide endless no-progress travel");
        check(OutcomeLearning.reward(100,true,-10,60000)>OutcomeLearning.reward(0,false,-100,60000),"Verified XP/quest progress outranks coin waste");
        OutcomeLearning ml=new OutcomeLearning(FilesStore.ROOT.resolve("test-outcomes.json"));
        ml.begin(Methods.get("trees"),still,1000);ml.finish(still,121000,false);
        check(ml.bonus(Methods.get("trees"),still)<0,"Failed progress updates learned value");
        OutcomeLearning loaded=new OutcomeLearning(FilesStore.ROOT.resolve("test-outcomes.json"));
        check(loaded.bonus(Methods.get("trees"),still)==ml.bonus(Methods.get("trees"),still),"Online values survive restart");
        double beforeSkip=loaded.bonus(Methods.get("trees"),still);loaded.begin(Methods.get("trees"),still,1000);loaded.finish(still,121000,true);
        check(loaded.bonus(Methods.get("trees"),still)==beforeSkip,"User Skip is not negative training evidence");
        GameState poor=state(Map.of(Skill.CRAFTING,10),false);
        check(!BuilderBudget.affordable(Methods.get("string_gold"),poor,List.of(),id -> 100),"Unaffordable production cannot trigger a long money grind");
        check(BuilderBudget.affordable(Methods.get("string_gold"),poor,List.of(new ResourceItem(1673,"Gold amulet (u)",10),new ResourceItem(1759,"Ball of wool",10)),id -> 100),"Owned materials allow the same progression task");
        check(!BuilderBudget.sellBatch(5,100,5,1000),"One low-value drop does not trigger a sale trip");
        check(BuilderBudget.sellBatch(300,100,5,1000),"Useful funding batch can settle");
        check(BuilderBudget.sellBatch(5,100,24,1000),"Full inventory settles without deadlock");
        check(BuilderBudget.sellBatch(5,100,5,60000),"Dry loot spots have a bounded batch wait");
        PurchaseReceipt receipt=new PurchaseReceipt(10,1,1000);
        check(receipt.observe(10,1500)==PurchaseReceipt.State.WAITING,"Dispatch without inventory gain is pending");
        check(receipt.observe(11,1600)==PurchaseReceipt.State.VERIFIED,"Actual purchased quantity verifies receipt");
        check(receipt.observe(10,9000)==PurchaseReceipt.State.FAILED,"Missing receipt fails instead of buying forever");
        check(new PurchaseReceipt(10,5,1000).observe(11,1600)==PurchaseReceipt.State.WAITING,"Partial inventory receipt cannot verify whole fill");
        GameState questState=state(Map.of(),false);
        questState.items=List.of(new ResourceItem(221,"Unknown Item",1));
        check(new Requirements().missing(Methods.get("witch"),questState).stream().noneMatch(n -> n.id==221),"Quest supply recognition uses item ID even with unavailable name");
        check(KnowledgeRules.size()>=310,"Expanded researched rules are packaged in runtime, not just a document");
        check(KnowledgeRules.search("bank notes bulk sale",12).stream().anyMatch(r->r.topic.equals("notes")),"Bank-note evidence can be retrieved");
        for(var purpose:BankWithdrawalPolicy.Purpose.values()) {
            check(BankWithdrawalPolicy.noted(purpose,true)==(purpose==BankWithdrawalPolicy.Purpose.SALE),"Only sale stock uses note mode");
        }
        ResourceItem bankOre=new ResourceItem(438,"Tin ore",5000,false,438,true);
        check(BankWithdrawalPolicy.saleQuantity(bankOre,10000,10,1)==1000,"Noted funding withdrawal is not limited to one free slot");
        ResourceItem feathers=new ResourceItem(314,"Feather",5000,false,314,false,true);
        check(BankWithdrawalPolicy.saleQuantity(feathers,500,1,1)==500,"Naturally stackable stock also avoids slot-limited withdrawals");
        ResourceItem normal=new ResourceItem(100,"Unnoteable test",5000);
        check(BankWithdrawalPolicy.saleQuantity(normal,500,1,2)==2,"Unnoteable unstackable items respect free slots");
        ResourceItem note=new ResourceItem(439,"Tin ore",1000,true,438,true);
        var batches=SaleBatches.group(List.of(note,new ResourceItem(438,"Tin ore",2)),Set.of("Tin ore"),Set.of());
        check(SaleBatches.physicalId(List.of(note),438)==439,"Shop fallback interacts with physical noted ID while GE uses canonical ID");
        check(TradeService.quote(100000,true)==125005 && TradeService.quote(100000,false)==80000,"Market quotes are not silently capped below 10k");
        check(!EquipmentPolicy.strengthIncreasesHit(48,44),"Strength amulet is not chosen when its actual max hit ties power");
        check(EquipmentPolicy.strengthIncreasesHit(48,0),"Strength amulet is chosen when it crosses a real max-hit breakpoint");
        check(batches.size()==1 && batches.get(0).id==438 && batches.get(0).quantity==1002,"GE uses canonical ID and combined noted/unnoted quantity");
        GameState wool=state(Map.of(),false); wool.items=List.of(new ResourceItem(1760,"Ball of wool",20,true,1759,true));
        check(wool.count("Ball of wool")==0 && wool.total("Ball of wool")==20,"Quest checks distinguish usable items from total sale stock");
        check(new Requirements().missing(Methods.get("sheep"),wool).stream().anyMatch(n->n.id==1759),"Noted wool cannot satisfy Sheep Shearer");
        GameState fake=state(Map.of(),false); fake.items=List.of(new ResourceItem(1351,"Bronze axe",1,true,1351,true));
        check(!ToolPolicy.has("axe",fake),"Noted tool metadata never authorizes object clicking");
        Method melee=Methods.get("chickens"), ranged=Methods.get("range_chickens"), magic=Methods.get("mage_chickens");
        GameState rune=state(Map.of(Skill.ATTACK,40,Skill.DEFENCE,40,Skill.RANGED,40),false);
        check(GearCatalog.eligible(melee,rune).stream().noneMatch(e->e.id==1127),"Rune platebody is blocked before Dragon Slayer");
        check(GearCatalog.eligible(melee,rune).stream().anyMatch(e->e.id==1113),"Rune chainbody remains an unlocked alternative");
        check(GearCatalog.eligible(ranged,rune).stream().noneMatch(e->e.id==1135),"Green body has the quest lock too");
        GameState unlocked=state(Map.of(Skill.ATTACK,40,Skill.DEFENCE,40,Skill.RANGED,40),true);
        check(GearCatalog.eligible(melee,unlocked).stream().anyMatch(e->e.id==1127),"Quest completion unlocks rune platebody upgrade");
        check(GearCatalog.eligible(ranged,unlocked).stream().anyMatch(e->e.id==1135),"Green body unlock requires both skills plus quest");
        GameState lowDef=state(Map.of(Skill.RANGED,40,Skill.DEFENCE,1),true);
        check(GearCatalog.eligible(ranged,lowDef).stream().noneMatch(e->e.id==1135),"Ranged level alone cannot unlock green body");
        lowDef.items=List.of(new ResourceItem(841,"Shortbow",1));
        check(GearCatalog.eligible(ranged,lowDef).stream().noneMatch(e->e.id==890),"Shortbow cannot receive incompatible adamant arrows");
        lowDef.items=List.of(new ResourceItem(853,"Maple shortbow",1));
        check(GearCatalog.eligible(ranged,lowDef).stream().anyMatch(e->e.id==890),"Maple shortbow permits adamant arrows");
        GameState fresh=state(Map.of(),false);
        var rangeNeeds=new Requirements().missing(ranged,fresh);
        check(rangeNeeds.stream().anyMatch(n->n.id==841) && rangeNeeds.stream().anyMatch(n->n.id==882),"Ranged requests a bow and ammunition");
        check(rangeNeeds.stream().noneMatch(n->n.id==1321 || n.id==1117),"Ranged does not buy melee starter gear");
        var magicNeeds=new Requirements().missing(magic,fresh);
        check(magicNeeds.stream().anyMatch(n->n.id==1381) && magicNeeds.stream().anyMatch(n->n.id==558),"Magic requests matching staff and runes");
        check(CombatTraining.rangedStyle(3,884)==0 && CombatTraining.rangedStyle(10,884)==1,"Low-level accurate exception and later rapid training");
        check(CombatTraining.rangedStyle(4,882)==0 && CombatTraining.rangedStyle(20,882)==1,"Bronze arrow breakpoint handled");
        check(ProgressionPolicy.meleeTarget(fresh,"MAIN")==Skill.ATTACK,"Initial balanced combat starts Attack");
        check(ProgressionPolicy.meleeTarget(state(Map.of(Skill.ATTACK,10),false),"MAIN")==Skill.STRENGTH,"Main progression changes combat target");
        check(ProgressionPolicy.meleeTarget(fresh,"PURE")==Skill.STRENGTH,"Pure policy does not train Defence");
        check(!ProgressionPolicy.questReady(Methods.get("knights_sword"),fresh),"Knight's Sword waits for Mining ten");
        check(ProgressionPolicy.questReady(Methods.get("knights_sword"),state(Map.of(Skill.MINING,10),false)),"Mining milestone unlocks Knight's Sword");
        fresh.questPoints=15;
        check(!ProgressionPolicy.questReady(Methods.get("below_ice"),fresh),"Below Ice Mountain respects quest points");
        fresh.questPoints=16;
        check(!ProgressionPolicy.questReady(Methods.get("below_ice"),fresh),"Combat readiness is also checked for Guardian");
        GameState guardian=state(Map.of(Skill.ATTACK,15,Skill.STRENGTH,15,Skill.DEFENCE,10),false); guardian.questPoints=16;
        check(ProgressionPolicy.questReady(Methods.get("below_ice"),guardian),"Guardian quest unlocks after points and combat readiness");
        F2pAioConfig config=new F2pAioConfig();
        check(config.accountBuilder && !config.enableFlipping && !config.allowPvp,"Default focus is account progression, not optional income or PvP");
        check(new GoalGraph().eligible(fresh,config,Set.of()).stream().noneMatch(m->m.kind==Method.Kind.GROUND),"Scavenging is a supply subgoal, not a builder's main activity");
        for(Method quest:Methods.all()) if(quest.kind==Method.Kind.QUEST) {
            check(new Quests().quest(quest.id)!=null && new Quests().requirements(quest.id)!=null,"Native quest registration has a state and supply contract");
        }
        for(int level:new int[]{1,5,10,20,30,40,60,99}) {
            GameState s=state(Map.of(Skill.ATTACK,level,Skill.DEFENCE,level,Skill.RANGED,level,Skill.MAGIC,level,Skill.WOODCUTTING,level,Skill.MINING,level),false);
            for(Method method:List.of(melee,ranged,magic,Methods.get("trees"),Methods.get("copper")))
                check(GearCatalog.eligible(method,s).stream().allMatch(e->e.eligible(s)),"No recommended equipment bypasses skill/quest eligibility");
        }
        System.out.println("PASS "+checks+" account-builder/notes/gear/knowledge regression checks");
    }
}


