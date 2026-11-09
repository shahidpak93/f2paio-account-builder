package com.example.f2paio;
/** Shared result handling for every equipment slot and melee/ranged/magic style. */
public final class SetupCoordinator {
    private String status="",failure="";
    public boolean ready(Method m,GameState state,String preset,EquipmentPolicy equipment) {
        failure="";
        if(RunEnergy.interfaceOwnsInput()) {status="Waiting for active interface before setup";return false;}
        if(!equipment.step(m,state)) {status="Verify equipment change";failure=equipment.failure();return false;}
        if(!CombatTraining.step(m,state,preset)) {status="Verify combat style/autocast";failure=CombatTraining.failure();return false;}
        status="Setup observed ready";return true;
    }
    public String status(){return status;}
    public String failure(){return failure;}
}
