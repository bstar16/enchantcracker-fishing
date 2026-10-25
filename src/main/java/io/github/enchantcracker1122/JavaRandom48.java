package io.github.enchantcracker1122;

/** A small explicit model of java.util.Random's 48-bit internal state. */
public final class JavaRandom48 {
    public static final long MULTIPLIER = 0x5DEECE66DL;
    public static final long ADDEND = 0xBL;
    public static final long MASK = (1L << 48) - 1;

    private long seed;

    public JavaRandom48(long internalSeed) {
        this.seed = internalSeed & MASK;
    }

    public JavaRandom48 copy() {
        return new JavaRandom48(seed);
    }

    public long getInternalSeed() {
        return seed;
    }

    public int next(int bits) {
        seed = (seed * MULTIPLIER + ADDEND) & MASK;
        return (int) (seed >>> (48 - bits));
    }

    public int nextInt() {
        return next(32);
    }

    public void advance(long calls) {
        for (long i = 0; i < calls; i++) {
            next(1);
        }
    }

    /**
     * Recovers the state immediately after the second of two consecutive
     * unbounded nextInt calls. Formula adapted from Earthcomputer/clientcommands.
     */
    public static JavaRandom48 recoverFromTwoNextInts(int first, int second) {
        long max1 = Integer.toUnsignedLong(first) + 1L;
        long min1 = Integer.toUnsignedLong(first);
        long max2 = Integer.toUnsignedLong(second) + 1L;
        long a = (24667315L * max1 + 18218081L * max2) >> 32;
        long b = (-4824621L * min1 + 7847617L * max2) >> 32;
        long state = (7847617L * a - 18218081L * b) & MASK;
        if ((int) (state >>> 16) != first) {
            return null;
        }
        state = (state * MULTIPLIER + ADDEND) & MASK;
        if ((int) (state >>> 16) != second) {
            return null;
        }
        return new JavaRandom48(state);
    }
}
