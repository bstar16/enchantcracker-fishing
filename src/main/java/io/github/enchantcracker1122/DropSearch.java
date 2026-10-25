package io.github.enchantcracker1122;

import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/** Searches in ascending drop count without a one-stack or int-sized cap. */
public final class DropSearch {
    // A full-period 48-bit LCG advanced four times per drop repeats after 2^46 drops.
    public static final long PERIOD = 1L << 46;

    private DropSearch() { }

    public static <T> T find(JavaRandom48 base, BooleanSupplier cancelled,
                             LongConsumer progress, BiFunction<Long, Integer, T> check) {
        JavaRandom48 cursor = base.copy();
        for (long drops = 0; drops < PERIOD; drops++) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            T result = check.apply(drops, cursor.copy().nextInt());
            progress.accept(drops + 1);
            if (result != null) {
                return result;
            }
            // Incremental advancement avoids re-simulating all previous drops.
            cursor.advance(4);
        }
        return null;
    }
}
