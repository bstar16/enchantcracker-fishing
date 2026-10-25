package io.github.enchantcracker1122;

import java.util.List;
import net.minecraft.item.ItemStack;

/** Only the explicitly selected item (including metadata and NBT) may be dropped. */
public final class DisposableStacks {
    private DisposableStacks() { }

    public static boolean matches(ItemStack template, ItemStack stack) {
        return !template.isEmpty() && !stack.isEmpty()
            && ItemStack.areItemsEqual(template, stack)
            && ItemStack.areItemStackTagsEqual(template, stack);
    }

    public static int findHotbarSlot(List<ItemStack> inventory, ItemStack template, int preferred) {
        if (preferred >= 0 && preferred < 9 && preferred < inventory.size()
            && matches(template, inventory.get(preferred))) {
            return preferred;
        }
        for (int slot = 0; slot < Math.min(9, inventory.size()); slot++) {
            if (matches(template, inventory.get(slot))) {
                return slot;
            }
        }
        return -1;
    }
}
