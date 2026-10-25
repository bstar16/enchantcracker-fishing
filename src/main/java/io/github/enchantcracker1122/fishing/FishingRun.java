package io.github.enchantcracker1122.fishing;

/** Testable automation lifecycle. A server-confirmed removal gates every recast. */
final class FishingRun {
    enum State { OFF, WAIT_CAST, WAIT_SPAWN, PREDICTING, WAIT_BITE, TIMED_REEL,
        REJECTING, WAIT_REMOVAL, VERIFYING, WAIT_PICKUP, RECAST, COMPLETE, PAUSED }
    enum Action { NONE, REEL, CAST }
    static final long SECOND=1_000_000_000L;
    enum Speed {
        NORMAL(500,1000,500), FAST(200,200,100);
        final long rejectDelay,recastDelay,ackDelay;
        Speed(long rejectMs,long recastMs,long ackMs){
            rejectDelay=rejectMs*1_000_000L;recastDelay=recastMs*1_000_000L;ackDelay=ackMs*1_000_000L;
        }
    }
    private Speed speed=Speed.FAST;
    private State state=State.OFF;
    private long changedAt,lastUse;
    private boolean hookGone=true,reelSent;
    private String detail="Set a target to start automatic fishing.";
    private int attempts,rejected,misses,consecutiveFailures;
    State state(){return state;}
    String detail(){return detail;}
    int attempts(){return attempts;}
    int rejected(){return rejected;}
    int misses(){return misses;}
    Speed speed(){return speed;}
    void speed(Speed speed){this.speed=speed;}
    boolean running(){return state!=State.OFF && state!=State.COMPLETE && state!=State.PAUSED;}
    private void set(State next,String text,long now){state=next;detail=text;changedAt=now;}
    void start(long now){
        attempts=rejected=misses=consecutiveFailures=0;hookGone=true;reelSent=false;lastUse=0;
        set(State.WAIT_CAST,"Aim at a clear pool and cast once. I will take over after your cast.",now);
    }
    void castSent(long now){
        if(state!=State.WAIT_CAST && state!=State.RECAST)throw new IllegalStateException("A cast is already in progress");
        ++attempts;hookGone=false;reelSent=false;lastUse=now;
        set(State.WAIT_SPAWN,"Cast sent; waiting for the server's bobber packet.",now);
    }
    boolean hookArrived(long now){
        if(state!=State.WAIT_SPAWN)return false;
        set(State.PREDICTING,"Bobber received; checking RNG, path and catches.",now);return true;
    }
    void predicted(boolean matching,long now){
        if(state!=State.PREDICTING)return;
        if(matching)set(State.WAIT_BITE,"A target catch is possible. Waiting for the bite.",now);
        else {++rejected;set(State.REJECTING,"No target in this cast. Reeling in and trying again.",now);}
    }
    void scheduled(long now){if(state==State.WAIT_BITE)set(State.TIMED_REEL,"Target tick selected; preparing timed retrieval.",now);}
    void reelSent(boolean forCatch,long now){
        if(state!=State.TIMED_REEL && state!=State.REJECTING)throw new IllegalStateException("Unexpected reel request");
        reelSent=true;lastUse=now;
        set(forCatch?State.VERIFYING:State.WAIT_REMOVAL,forCatch?
            "Reeled in. Checking the actual catch and inventory.":"Reel sent; waiting for the server to remove the hook.",now);
    }
    void hookRemoved(long now){
        hookGone=true;
        if(state==State.WAIT_REMOVAL)set(State.RECAST,"Hook removed. Recasting shortly.",now);
    }
    void retry(String reason,long now){
        if(!running())return;
        if(reelSent)set(hookGone?State.RECAST:State.WAIT_REMOVAL,reason,now);
        else set(State.REJECTING,reason,now);
    }
    void missed(String reason,long now){
        if(!running())return;
        ++misses;
        if(++consecutiveFailures>=4)pause(reason+" Four catches could not be confirmed; check timing/settings, then resume.",now);
        else retry(reason+" Trying again.",now);
    }
    void targetObserved(long now){
        if(state==State.VERIFYING){consecutiveFailures=0;set(State.WAIT_PICKUP,"Target landed! Waiting for it to reach your inventory.",now);}
    }
    boolean confirmPickup(boolean collectedByYou,boolean inventoryIncreased,long now){
        if(state!=State.WAIT_PICKUP || !collectedByYou || !inventoryIncreased)return false;
        set(State.COMPLETE,"Target collected into your inventory. Automatic fishing finished.",now);return true;
    }
    void pause(String reason,long now){set(State.PAUSED,reason,now);}
    void stop(String reason,long now){set(State.OFF,reason,now);}
    Action due(long now){
        if(state==State.REJECTING && now-lastUse>=speed.rejectDelay)return Action.REEL;
        if(state==State.RECAST && hookGone && now-lastUse>=speed.recastDelay && now-changedAt>=speed.ackDelay)return Action.CAST;
        return Action.NONE;
    }
    void checkTimeout(long now){
        if((state==State.WAIT_SPAWN || state==State.WAIT_REMOVAL) && now-changedAt>5*SECOND)
            pause(state==State.WAIT_SPAWN?"No bobber packet arrived. Check that the rod cast and connection are working.":
                "The server did not remove the hook. No extra rod-use packet was sent.",now);
        else if(state==State.VERIFYING && now-changedAt>6*SECOND)
            missed("No catch could be verified.",now);
    }
}
