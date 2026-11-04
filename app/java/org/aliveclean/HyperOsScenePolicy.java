package org.aliveclean;

/** Maps HyperOS sleep and keyguard events to OpenAlive's three wallpaper scenes. */
final class HyperOsScenePolicy {
    static final class Event {
        final int mode;
        final int phase;
        final boolean animate;

        Event(int mode,int phase,boolean animate){
            this.mode=mode;
            this.phase=phase;
            this.animate=animate;
        }
    }

    // Unknown lets startup report the current ambient state without inventing
    // a wake transition. phase 1 is sleeping, 2 waking and 3 authenticated.
    static Event ambient(Boolean wasAmbient,boolean isAmbient){
        if(isAmbient)return new Event(0,1,wasAmbient==null||!wasAmbient);
        if(Boolean.TRUE.equals(wasAmbient))return new Event(1,2,true);
        return null;
    }

    static Event keyguard(boolean showing,boolean interactive,boolean ambient,boolean goingAway){
        if(ambient)return null;
        if(goingAway)return interactive?new Event(2,3,true):null;
        if(showing)return new Event(1,0,false);
        return interactive?new Event(2,3,true):null;
    }

    static boolean fullAodAnimationEligible(
            boolean owned,boolean aodEnabled,boolean deviceSupported,boolean fullScreen){
        return owned&&aodEnabled&&deviceSupported&&fullScreen;
    }

    private HyperOsScenePolicy(){}
}
