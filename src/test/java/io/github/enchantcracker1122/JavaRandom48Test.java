package io.github.enchantcracker1122;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Random;
import org.junit.Test;

public class JavaRandom48Test {
    @Test
    public void recoversStateAfterTwoConsecutiveNextInts() {
        long userSeed = 0x1234ABCDEFL;
        Random vanilla = new Random(userSeed);
        int first = vanilla.nextInt();
        int second = vanilla.nextInt();
        int third = vanilla.nextInt();

        JavaRandom48 recovered = JavaRandom48.recoverFromTwoNextInts(first, second);
        assertNotNull(recovered);
        assertEquals(third, recovered.nextInt());
    }

    @Test
    public void recoversManyRandomStates() {
        Random seeds = new Random(0x1122C0DEL);
        for (int i = 0; i < 10000; i++) {
            Random vanilla = new Random(seeds.nextLong());
            int first = vanilla.nextInt();
            int second = vanilla.nextInt();
            int third = vanilla.nextInt();
            JavaRandom48 recovered = JavaRandom48.recoverFromTwoNextInts(first, second);
            assertNotNull("iteration " + i, recovered);
            assertEquals("iteration " + i, third, recovered.nextInt());
        }
    }
}
