package io.github.enchantcracker1122;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropSearchTest {
    @Test
    public void findsBeyondOneStackAndPreservesExactFourCallSequence() {
        JavaRandom48 base = new JavaRandom48(123456789L);
        JavaRandom48 reference = base.copy();
        reference.advance(100000L * 4);
        int expected = reference.nextInt();
        AtomicLong checked = new AtomicLong();
        Integer found = DropSearch.find(base, () -> false, checked::set,
            (drops, seed) -> drops == 100000L ? seed : null);
        assertEquals(Integer.valueOf(expected), found);
        assertEquals(100001L, checked.get());
        assertEquals(123456789L, base.getInternalSeed());
        assertTrue(DropSearch.PERIOD > Integer.MAX_VALUE);
    }

    @Test
    public void cancellationStopsWithoutReturningAPartialMatch() {
        AtomicLong checked = new AtomicLong();
        Object result = DropSearch.find(new JavaRandom48(123L), () -> checked.get() == 80,
            checked::set, (drops, seed) -> null);
        assertNull(result);
        assertEquals(80L, checked.get());
    }

    @Test
    public void returnsNearestMatchIncludingZeroDrops() {
        Long zero = DropSearch.find(new JavaRandom48(5L), () -> false, n -> { }, (drops, seed) -> drops);
        assertEquals(Long.valueOf(0), zero);
        Long nearest = DropSearch.find(new JavaRandom48(5L), () -> false, n -> { },
            (drops, seed) -> drops >= 65 ? drops : null);
        assertEquals(Long.valueOf(65), nearest);
    }
}
