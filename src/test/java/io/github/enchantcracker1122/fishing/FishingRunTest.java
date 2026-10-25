package io.github.enchantcracker1122.fishing;

import org.junit.Test;
import static org.junit.Assert.*;

public class FishingRunTest {
    private static final long S=FishingRun.SECOND;
    private FishingRun predicted(boolean match){
        FishingRun run=new FishingRun();run.start(0);run.castSent(S);assertTrue(run.hookArrived(2*S));run.predicted(match,2*S);return run;
    }
    @Test public void targetWaitsForOneManualCast(){
        FishingRun r=new FishingRun();r.start(0);
        assertEquals(FishingRun.State.WAIT_CAST,r.state());assertEquals(FishingRun.Action.NONE,r.due(100*S));
        assertFalse(r.hookArrived(S));assertEquals(0,r.attempts());
    }
    @Test public void rejectionWaitsForRemovalThenRecastsOnce(){
        FishingRun r=predicted(false);assertEquals(1,r.rejected());assertEquals(FishingRun.Action.REEL,r.due(2*S));
        r.reelSent(false,2*S);assertEquals(FishingRun.Action.NONE,r.due(3*S));
        r.hookRemoved(3*S);assertEquals(FishingRun.Action.NONE,r.due(3*S));
        assertEquals(FishingRun.Action.CAST,r.due(4*S));r.castSent(4*S);
        assertEquals(2,r.attempts());assertEquals(FishingRun.Action.NONE,r.due(5*S));
    }
    @Test public void matchingDropAloneIsNotSuccess(){
        FishingRun r=predicted(true);r.scheduled(3*S);r.reelSent(true,4*S);r.hookRemoved(5*S);
        assertEquals(FishingRun.State.VERIFYING,r.state());r.targetObserved(5*S);
        assertFalse(r.confirmPickup(false,true,6*S));assertFalse(r.confirmPickup(true,false,6*S));
        assertTrue(r.confirmPickup(true,true,7*S));assertEquals(FishingRun.State.COMPLETE,r.state());
        assertEquals(FishingRun.Action.NONE,r.due(100*S));
    }
    @Test public void targetPickupBeforeRemovalDoesNotCauseRecast(){
        FishingRun r=predicted(true);r.scheduled(3*S);r.reelSent(true,4*S);r.targetObserved(4*S);
        assertTrue(r.confirmPickup(true,true,5*S));r.hookRemoved(6*S);
        assertEquals(FishingRun.State.COMPLETE,r.state());
    }
    @Test public void lostTargetRetriesInsteadOfReportingSuccess(){
        FishingRun r=predicted(true);r.scheduled(3*S);r.reelSent(true,4*S);r.hookRemoved(5*S);
        r.targetObserved(5*S);r.missed("Target disappeared",6*S);
        assertEquals(FishingRun.State.RECAST,r.state());assertFalse(r.confirmPickup(true,true,7*S));
        assertEquals(FishingRun.Action.CAST,r.due(7*S));
    }
    @Test public void wrongCatchRetriesWithoutDoubleReeling(){
        FishingRun r=predicted(true);r.scheduled(3*S);r.reelSent(true,4*S);r.missed("Wrong catch",5*S);
        assertEquals(FishingRun.State.WAIT_REMOVAL,r.state());assertEquals(FishingRun.Action.NONE,r.due(6*S));
        r.hookRemoved(6*S);assertEquals(FishingRun.Action.CAST,r.due(7*S));
    }
    @Test public void cancellationAndPauseBlockQueuedActions(){
        FishingRun r=predicted(false);r.stop("Cancelled",3*S);r.hookRemoved(4*S);
        assertEquals(FishingRun.Action.NONE,r.due(100*S));assertFalse(r.hookArrived(5*S));
        r.start(6*S);r.castSent(7*S);r.pause("Rod changed",8*S);assertEquals(FishingRun.Action.NONE,r.due(100*S));
    }
    @Test public void missingSpawnOrRemovalStopsWithoutBlindRetries(){
        FishingRun r=new FishingRun();r.start(0);r.castSent(S);r.checkTimeout(7*S);
        assertEquals(FishingRun.State.PAUSED,r.state());
        r=predicted(false);r.reelSent(false,3*S);r.checkTimeout(9*S);
        assertEquals(FishingRun.State.PAUSED,r.state());assertEquals(FishingRun.Action.NONE,r.due(10*S));
    }
    @Test public void ordinaryRejectionsHaveNoArbitraryAttemptLimit(){
        FishingRun r=new FishingRun();r.start(0);
        for(int i=0;i<120;i++){
            long t=(i*4+1)*S;r.castSent(t);r.hookArrived(t+S/10);r.predicted(false,t+S/10);
            assertEquals(FishingRun.Action.NONE,r.due(t+S/10));
            r.reelSent(false,t+S);r.hookRemoved(t+2*S);assertEquals(FishingRun.Action.CAST,r.due(t+3*S));
        }
        assertEquals(120,r.attempts());assertEquals(0,r.misses());
    }
    @Test public void fastCycleIsBoundedAndStillWaitsForServer(){
        FishingRun r=new FishingRun();assertEquals(FishingRun.Speed.FAST,r.speed());
        r.start(0);r.castSent(0);r.hookArrived(S/20);r.predicted(false,S/10);
        assertEquals(FishingRun.Action.NONE,r.due(199_000_000L));
        assertEquals(FishingRun.Action.REEL,r.due(200_000_000L));r.reelSent(false,200_000_000L);
        assertEquals(FishingRun.Action.NONE,r.due(450_000_000L)); // Even beyond the timer, no removal acknowledgement.
        r.hookRemoved(500_000_000L);
        assertEquals(FishingRun.Action.NONE,r.due(599_000_000L));
        assertEquals(FishingRun.Action.CAST,r.due(600_000_000L));r.castSent(600_000_000L);
        assertEquals(FishingRun.Action.NONE,r.due(S));
    }
    @Test public void fastMinimumCycleIsFourHundredMilliseconds(){
        FishingRun r=new FishingRun();r.start(0);r.castSent(0);r.hookArrived(50_000_000L);r.predicted(false,100_000_000L);
        r.reelSent(false,200_000_000L);r.hookRemoved(250_000_000L);
        assertEquals(FishingRun.Action.NONE,r.due(399_000_000L));assertEquals(FishingRun.Action.CAST,r.due(400_000_000L));
    }
    @Test public void normalSpeedRestoresOriginalPacing(){
        FishingRun r=new FishingRun();r.speed(FishingRun.Speed.NORMAL);r.start(0);r.castSent(0);r.hookArrived(S/10);r.predicted(false,S/10);
        assertEquals(FishingRun.Action.NONE,r.due(499_000_000L));assertEquals(FishingRun.Action.REEL,r.due(S/2));
        r.reelSent(false,S/2);r.hookRemoved(600_000_000L);
        assertEquals(FishingRun.Action.NONE,r.due(1_499_000_000L));assertEquals(FishingRun.Action.CAST,r.due(1_500_000_000L));
    }
    @Test public void speedSwitchCannotSkipAcknowledgementOrChangeCatchState(){
        FishingRun r=predicted(false);r.speed(FishingRun.Speed.NORMAL);r.reelSent(false,3*S);r.speed(FishingRun.Speed.FAST);
        assertEquals(FishingRun.Action.NONE,r.due(4*S));r.stop("Cancel",4*S);r.speed(FishingRun.Speed.NORMAL);
        assertEquals(FishingRun.Action.NONE,r.due(100*S));
        r=predicted(true);r.speed(FishingRun.Speed.FAST);assertEquals(FishingRun.State.WAIT_BITE,r.state());
        assertEquals(FishingRun.Action.NONE,r.due(100*S));r.scheduled(3*S);r.speed(FishingRun.Speed.NORMAL);
        assertEquals(FishingRun.State.TIMED_REEL,r.state());assertEquals(FishingRun.Action.NONE,r.due(100*S));
    }
    @Test public void repeatedUnverifiedCatchesPauseWithReason(){
        FishingRun r=new FishingRun();r.start(0);
        for(int i=0;i<4;i++){
            long t=(i*10+1)*S;r.castSent(t);r.hookArrived(t+S);r.predicted(true,t+S);
            r.scheduled(t+2*S);r.reelSent(true,t+3*S);r.hookRemoved(t+4*S);r.checkTimeout(t+10*S);
        }
        assertEquals(FishingRun.State.PAUSED,r.state());assertEquals(4,r.misses());assertTrue(r.detail().contains("Four catches"));
    }
}
