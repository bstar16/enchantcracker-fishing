package io.github.enchantcracker1122.fishing;

import java.util.Random;
import java.util.UUID;

/** Java's LCG and Gaussian cache, both explicitly copyable. Seeds are internal. */
public final class FishingRandom extends Random {
    private static final long serialVersionUID = 1L;
    public static final long MULTIPLIER = 0x5DEECE66DL;
    public static final long MASK = (1L << 48) - 1;
    private long state;
    private boolean cached;
    private double gaussian;

    public FishingRandom(long internalSeed) { super(0); state = internalSeed & MASK; }
    @Override public synchronized void setSeed(long externalSeed) {
        state = (externalSeed ^ MULTIPLIER) & MASK;
        cached = false;
    }
    @Override protected int next(int bits) {
        state = (state * MULTIPLIER + 11) & MASK;
        return (int) (state >>> (48 - bits));
    }
    @Override public synchronized double nextGaussian() {
        if (cached) { cached = false; return gaussian; }
        double a, b, s;
        do { a = 2 * nextDouble() - 1; b = 2 * nextDouble() - 1; s = a*a + b*b; }
        while (s >= 1 || s == 0);
        double scale = StrictMath.sqrt(-2 * StrictMath.log(s) / s);
        gaussian = b * scale;
        cached = true;
        return a * scale;
    }
    public long state() { return state; }
    public FishingRandom copy() {
        FishingRandom result = new FishingRandom(state);
        result.cached = cached; result.gaussian = gaussian;
        return result;
    }
    public UUID uuid() {
        return new UUID((nextLong() & -61441L) | 16384L,
                        (nextLong() & 0x3fffffffffffffffL) | Long.MIN_VALUE);
    }
    private static long back(long state) { return ((state - 11) * 0xDFE05BCB1365L) & MASK; }

    /** Recovers the pre-UUID state, verifying all visible bits of both longs. */
    public static FishingRandom recover(UUID uuid) {
        long visible = uuid.getLeastSignificantBits() & 0x3fffffffffffffffL;
        for (long hidden = 0; hidden < 4; hidden++) {
            long value = visible | hidden << 62;
            int second = (int) value;
            long first = (value - (long) second) >>> 32; // nextLong's signed low int
            for (int low = 0; low < 65536; low++) {
                long s3 = (first << 16) | low;
                long s4 = (s3 * MULTIPLIER + 11) & MASK;
                if ((int) (s4 >>> 16) != second) continue;
                long initial = back(back(back(s3)));
                if (new FishingRandom(initial).uuid().equals(uuid)) return new FishingRandom(initial);
            }
        }
        throw new IllegalArgumentException("Hook UUID does not match Java Random. Use the patched server.");
    }
}
