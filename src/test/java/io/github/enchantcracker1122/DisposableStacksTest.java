package io.github.enchantcracker1122;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class DisposableStacksTest {
    @BeforeClass
    public static void bootstrap() { Bootstrap.register(); }

    @Test
    public void consumesMultipleMatchingHotbarStacksAndStopsForRefill() {
        List<ItemStack> inventory = new ArrayList<ItemStack>(Collections.nCopies(36, ItemStack.EMPTY));
        ItemStack disposable = new ItemStack(Items.STICK);
        inventory.set(0, new ItemStack(Items.DIAMOND_SWORD));
        inventory.set(1, new ItemStack(Items.STICK, 64));
        inventory.set(4, new ItemStack(Items.STICK, 64));
        inventory.set(8, new ItemStack(Items.STICK, 12));
        inventory.set(9, new ItemStack(Items.STICK, 64)); // outside hotbar: never auto-touched
        int preferred = 1;
        for (int dropped = 0; dropped < 140; dropped++) {
            int slot = DisposableStacks.findHotbarSlot(inventory, disposable, preferred);
            assertTrue(slot == 1 || slot == 4 || slot == 8);
            inventory.get(slot).shrink(1);
            preferred = slot;
        }
        assertEquals(-1, DisposableStacks.findHotbarSlot(inventory, disposable, preferred));
        assertEquals(1, inventory.get(0).getCount());
        assertEquals(64, inventory.get(9).getCount());
        inventory.set(2, new ItemStack(Items.STICK, 32));
        assertEquals(2, DisposableStacks.findHotbarSlot(inventory, disposable, preferred));
    }

    @Test
    public void rejectsDifferentMetadataAndNamedItems() {
        ItemStack template = new ItemStack(Items.COAL, 64, 0);
        assertFalse(DisposableStacks.matches(template, new ItemStack(Items.COAL, 64, 1)));
        ItemStack named = template.copy();
        named.setStackDisplayName("Keep me");
        assertFalse(DisposableStacks.matches(template, named));
        assertFalse(DisposableStacks.matches(template, ItemStack.EMPTY));
    }
}
