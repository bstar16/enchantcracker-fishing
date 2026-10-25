package io.github.enchantcracker1122.fishing;

import java.util.Random;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class FishingRandomTest {
    @Test public void recoversUuidAndPreservesGaussianCache() {
        Random samples=new Random(1620122);
        long[] edges={0,1,-1,Long.MIN_VALUE,Long.MAX_VALUE,0x5DEECE66DL,(1L<<48)-1};
        for(int i=0;i<1031;i++) {
            long external=i<edges.length?edges[i]:samples.nextLong();
            Random original=new Random(external);
            UUID id=new UUID((original.nextLong() & -61441L)|16384L,
                (original.nextLong() & 0x3fffffffffffffffL)|Long.MIN_VALUE);
            FishingRandom predicted=FishingRandom.recover(id);
            assertEquals((external^FishingRandom.MULTIPLIER)&FishingRandom.MASK,predicted.state());
            assertEquals(id,predicted.uuid());
            for(int j=0;j<64;j++) {
                assertEquals(original.nextGaussian(),predicted.nextGaussian(),0);
                assertEquals(original.nextFloat(),predicted.nextFloat(),0);
                assertEquals(original.nextInt(501),predicted.nextInt(501));
                FishingRandom fork=predicted.copy();
                assertEquals(original.nextGaussian(),fork.nextGaussian(),0);
                predicted=fork;
            }
        }
    }
    @Test public void copyingDoesNotConsumePhysicsRandom() {
        FishingRandom random=new FishingRandom(19);random.nextGaussian();
        FishingRandom a=random.copy(),b=random.copy();
        for(int i=0;i<100;i++) {assertEquals(a.nextGaussian(),b.nextGaussian(),0);assertEquals(a.nextLong(),b.nextLong());}
        assertNotEquals(a.state(),random.state());
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsNonJavaUuid() {FishingRandom.recover(new UUID(0,0));}
}
