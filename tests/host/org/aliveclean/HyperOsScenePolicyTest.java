package org.aliveclean;

import static org.aliveclean.HyperOsScenePolicy.*;

public final class HyperOsScenePolicyTest {
    private static int checks;

    public static void main(String[] args){
        event(ambient(null,true),0,1,true,"initial AOD");
        event(ambient(false,true),0,1,true,"enter AOD");
        event(ambient(true,true),0,1,false,"stay in AOD");
        event(ambient(true,false),1,2,true,"leave AOD");
        none(ambient(false,false),"ordinary awake");

        event(keyguard(true,true,false,false),1,0,false,"show keyguard");
        event(keyguard(false,true,false,true),2,3,true,"authenticated unlock");
        event(keyguard(false,true,false,false),2,3,true,"already unlocked");
        none(keyguard(true,true,true,false),"AOD owns visible scene");
        none(keyguard(false,false,false,true),"display is not interactive");

        check(fullAodAnimationEligible(true,true,true,true),"native full AOD enabled");
        check(!fullAodAnimationEligible(false,true,true,true),"other wallpaper untouched");
        check(!fullAodAnimationEligible(true,false,true,true),"user AOD setting respected");
        check(!fullAodAnimationEligible(true,true,false,true),"unsupported hardware respected");
        check(!fullAodAnimationEligible(true,true,true,false),"full-screen setting respected");

        System.out.println("HYPEROS_SCENE_POLICY_OK checks="+checks);
    }

    private static void event(Event event,int mode,int phase,boolean animate,String name){
        if(event==null)throw new AssertionError(name+": missing event");
        if(event.mode!=mode||event.phase!=phase||event.animate!=animate)
            throw new AssertionError(name+": got "+event.mode+"/"+event.phase+"/"+event.animate);
        checks++;
    }

    private static void none(Event event,String name){
        if(event!=null)throw new AssertionError(name+": unexpected event");
        checks++;
    }

    private static void check(boolean value,String name){
        if(!value)throw new AssertionError(name);
        checks++;
    }

    private HyperOsScenePolicyTest(){}
}
