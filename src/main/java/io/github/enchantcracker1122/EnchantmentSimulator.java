package io.github.enchantcracker1122;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Exact call order used by vanilla/Forge 1.12.2 ContainerEnchantment. */
public final class EnchantmentSimulator {
    private EnchantmentSimulator() {
    }

    public static int[] costs(int xpSeed, int power, ItemStack stack) {
        Random random = new Random(xpSeed);
        int[] costs = new int[3];
        for (int slot = 0; slot < 3; slot++) {
            int cost = EnchantmentHelper.calcItemStackEnchantability(random, slot, power, stack);
            costs[slot] = cost < slot + 1 ? 0 : cost;
        }
        return costs;
    }

    public static List<EnchantmentData> enchantments(int xpSeed, ItemStack stack, int slot, int level) {
        if (level <= 0) {
            return Collections.emptyList();
        }
        Random random = new Random((long) xpSeed + slot);
        List<EnchantmentData> result = new ArrayList<EnchantmentData>(
            EnchantmentHelper.buildEnchantmentList(random, stack, level, false));
        if (stack.getItem() == Items.BOOK && result.size() > 1) {
            result.remove(random.nextInt(result.size()));
        }
        return result;
    }

    public static EnchantmentData clue(int xpSeed, ItemStack stack, int slot, int level) {
        if (level <= 0) {
            return null;
        }
        Random random = new Random((long) xpSeed + slot);
        List<EnchantmentData> result = new ArrayList<EnchantmentData>(
            EnchantmentHelper.buildEnchantmentList(random, stack, level, false));
        if (stack.getItem() == Items.BOOK && result.size() > 1) {
            result.remove(random.nextInt(result.size()));
        }
        return result.isEmpty() ? null : result.get(random.nextInt(result.size()));
    }
}
