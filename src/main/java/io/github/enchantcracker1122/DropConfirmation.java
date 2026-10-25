package io.github.enchantcracker1122;

import net.minecraft.item.ItemStack;

/** EntityPlayerSP.dropItem only sends a packet; inventory changes arrive later. */
public final class DropConfirmation {
    public static final long TIMEOUT_NANOS = 15_000_000_000L;
    public enum Result { WAITING, CONFIRMED, INVENTORY_CHANGED, TIMED_OUT }
    private final ItemStack before;
    private final long startedAt;

    public DropConfirmation(ItemStack before, long startedAt) {
        if (before.isEmpty()) {
            throw new IllegalArgumentException("Cannot drop an empty stack");
        }
        this.before = before.copy();
        this.startedAt = startedAt;
    }

    public Result observe(ItemStack current, long now) {
        int expected = before.getCount() - 1;
        if ((expected == 0 && current.isEmpty())
            || (DisposableStacks.matches(before, current) && current.getCount() == expected)) {
            return Result.CONFIRMED;
        }
        if (!DisposableStacks.matches(before, current) || current.getCount() != before.getCount()) {
            return Result.INVENTORY_CHANGED;
        }
        return now - startedAt >= TIMEOUT_NANOS ? Result.TIMED_OUT : Result.WAITING;
    }
}
