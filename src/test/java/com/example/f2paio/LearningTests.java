package com.example.f2paio;
import java.nio.file.*;
import java.util.*;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
public final class LearningTests {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
    private static GameState state(long xp) {
        var levels=new EnumMap<Skill,Integer>(Skill.class); var experience=new EnumMap<Skill,Integer>(Skill.class);
        for(Skill skill:Skill.values()) if(skill!=Skill.OVERALL) { levels.put(skill,1); experience.put(skill,0); }
        experience.put(Skill.MINING,(int)xp);
        GameState s=new GameState("test",new WorldPoint(3286,3363,0),xp,1,false,false,100,false,levels,experience,Map.of(),1000);
        s.items=List.of(new ResourceItem(1265,"Bronze pickaxe",1)); return s;
    }
    static void run() throws Exception {
        var state=state(0);
        Path file=FilesStore.ROOT.resolve("learning/test-bandit.json");
        var bandit=new Bandit(file); bandit.context(state);
        for(int n=0;n<30;n++) { bandit.reward("copper",true,20,0); bandit.penalize("tin"); }
        var restored=new Bandit(file); restored.context(state);
        check(restored.observations("copper")==30,"Evidence survives restart");
        double good=0,bad=0; Random random=new Random(25);
        for(int n=0;n<1000;n++) { good+=restored.score("copper",random); bad+=restored.score("tin",random); }
        check(good>bad+300,"Observed productive method outranks repeated failures after reload");
        String saved=Files.readString(file);
        check(saved.contains("xpReward") && saved.contains("cashReward"),"Separate XP and cash evidence stored");
        GameState poor=state(0); // constructor has immutable coins, make a distinct coarse level context instead
        var highLevels=new EnumMap<Skill,Integer>(poor.levels); highLevels.put(Skill.MINING,30);
        GameState high=new GameState("test",poor.location,0,1,false,false,100,false,highLevels,poor.experience,Map.of(),1000);
        restored.context(high);
        check(restored.observations("copper")==0,"Different level context starts independently");
        var rotation=new TaskRotation(); Method copper=Methods.get("copper"),tin=Methods.get("tin"),trees=Methods.get("trees");
        rotation.excludeSkill(copper,1000,60000);
        check(rotation.excluded(1001).contains("tin"),"Skip mining excludes other mining methods");
        check(!rotation.excluded(1001).contains("trees"),"Skip keeps other skills eligible");
        check(!rotation.excluded(61000).contains("tin"),"Skill cooldown expires");
        rotation.selected(copper,1000); rotation.selected(tin,2000);
        check(rotation.diversify(List.of(copper,tin,trees),3000,900000).equals(List.of(trees)),"Two consecutive skill sessions require another available skill");
        check(rotation.diversify(List.of(copper),3000,900000).equals(List.of(copper)),"Single-skill availability does not deadlock");
        var near=new Method("near","near",Method.Kind.OBJECT,Skill.MINING,1,new String[]{"Copper rocks"},"Mine",state.location,List.of("Copper ore"),List.of("pickaxe"));
        var far=new Method("far","far",Method.Kind.OBJECT,Skill.MINING,1,new String[]{"Copper rocks"},"Mine",new WorldPoint(2980,3230,0),List.of("Copper ore"),List.of("pickaxe"));
        var planner=new AutonomousPlanner(); var choices=List.of(near,far);
        check(Math.abs(planner.score(near,choices,state, "",null,null)-planner.score(far,choices,state,"",null,null))<=15.01,
            "Cross-region option has only bounded travel cost, no regional lock penalty");
        Method proposal=new Method("learned_test_copper","Test copper",Method.Kind.OBJECT,Skill.MINING,1,
            new String[]{"Copper rocks"},"Mine",state.location,List.of("Copper ore"),List.of("pickaxe"));
        check(SkillProbation.valid(proposal),"Executable typed proposal accepted");
        Method missingTool=new Method("learned_bad_tool","Bad",Method.Kind.OBJECT,Skill.MINING,1,
            new String[]{"Copper rocks"},"Mine",state.location,List.of("Copper ore"),List.of());
        check(!SkillProbation.valid(missingTool),"Mining proposal cannot omit pickaxe precondition");
        check(!SkillProbation.valid(copper),"Proposal cannot overwrite builtin method ID");
        Methods.saveLearnedSkill(proposal);
        var monitor=new ActionMonitor(); state.animating=true; monitor.sent(proposal,state,1000);
        check(monitor.observe(state,2000)==ActionMonitor.Result.WAITING,"Animation alone never verifies a learned skill");
        check(monitor.observe(state(20),3000)==ActionMonitor.Result.CONFIRMED,"Relevant XP verifies learned effect");
        var probation=new SkillProbation(FilesStore.ROOT.resolve("test-probation.json"));
        check(probation.status(proposal.id).equals("PROBATION"),"New skill starts on probation");
        probation.observe(proposal.id,true); probation.observe(proposal.id,true);
        check(probation.status(proposal.id).equals("PROBATION"),"Two confirmations do not prematurely promote");
        probation.observe(proposal.id,true);
        check(new SkillProbation(FilesStore.ROOT.resolve("test-probation.json")).status(proposal.id).equals("PROMOTED"),"Three effects promote persistently");
        probation.observe(proposal.id,false); probation.observe(proposal.id,false);
        check(probation.status(proposal.id).equals("QUARANTINED"),"Two rejections quarantine even promoted skill");
        check(TradeService.unitPrice(1351,true)>0,"Price lookup tolerates unavailable live client");
        Files.deleteIfExists(FilesStore.ROOT.resolve("learned-skills.json"));
        System.out.println("PASS "+checks+" persistent learning/rotation/verifier regression cases");
    }
}
