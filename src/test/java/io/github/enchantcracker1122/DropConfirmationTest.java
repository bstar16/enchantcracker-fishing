package io.github.enchantcracker1122;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropConfirmationTest {
    @BeforeClass
    public static void bootstrap() { Bootstrap.register(); }

    @Test
    public void unchangedStackWaitsForDelayedServerUpdate() {
        ItemStack stack = new ItemStack(Items.STICK, 64);
        DropConfirmation pending = new DropConfirmation(stack, 0);
        assertEquals(DropConfirmation.Result.WAITING, pending.observe(stack, 0));
        assertEquals(DropConfirmation.Result.WAITING, pending.observe(stack, 2_000_000_000L));
        stack.shrink(1);
        assertEquals(DropConfirmation.Result.CONFIRMED, pending.observe(stack, 2_100_000_000L));
    }

    @Test
    public void confirmsLastItemOnlyWhenSlotBecomesEmpty() {
        ItemStack stack = new ItemStack(Items.STICK);
        DropConfirmation pending = new DropConfirmation(stack, 0);
        assertEquals(DropConfirmation.Result.WAITING, pending.observe(stack, 1));
        assertEquals(DropConfirmation.Result.CONFIRMED, pending.observe(ItemStack.EMPTY, 2));
    }

    @Test
    public void timesOutUnconfirmedDropWithoutAssumingSuccess() {
        ItemStack stack = new ItemStack(Items.STICK, 64);
        DropConfirmation pending = new DropConfirmation(stack, 100);
        assertEquals(DropConfirmation.Result.WAITING,
            pending.observe(stack, 100 + DropConfirmation.TIMEOUT_NANOS - 1));
        assertEquals(DropConfirmation.Result.TIMED_OUT,
            pending.observe(stack, 100 + DropConfirmation.TIMEOUT_NANOS));
    }

    @Test
    public void rejectsUnrelatedInventoryChanges() {
        DropConfirmation pending = new DropConfirmation(new ItemStack(Items.STICK, 64), 0);
        assertEquals(DropConfirmation.Result.INVENTORY_CHANGED, pending.observe(new ItemStack(Items.STICK, 62), 1));
        assertEquals(DropConfirmation.Result.INVENTORY_CHANGED, pending.observe(new ItemStack(Items.DIAMOND, 63), 1));
        assertEquals(DropConfirmation.Result.INVENTORY_CHANGED, pending.observe(ItemStack.EMPTY, 1));
        ItemStack renamed = new ItemStack(Items.STICK, 63);
        renamed.setStackDisplayName("Different stack");
        assertEquals(DropConfirmation.Result.INVENTORY_CHANGED, pending.observe(renamed, 1));
    }
}
