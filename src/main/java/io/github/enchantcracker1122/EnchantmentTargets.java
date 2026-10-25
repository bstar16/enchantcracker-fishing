package io.github.enchantcracker1122;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/** Resolves and validates the requested combination before an asynchronous search. */
public final class EnchantmentTargets {
    private final Map<Enchantment, Integer> requirements;

    public EnchantmentTargets(ItemStack stack, Map<String, Integer> requested) {
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("Specify at least one enchantment.");
        }
        Map<Enchantment, Integer> resolved = new LinkedHashMap<Enchantment, Integer>();
        // Upper bound at 15 shelves: base cost 30, two enchantability rolls,
        // then the largest possible +15% adjustment (rounded by vanilla).
        int enchantability = stack.getItem().getItemEnchantability(stack);
        int maxPower = Math.round((30 + 1 + 2 * (enchantability / 4)) * 1.15F);
        Map<Enchantment, Integer> obtainable = new LinkedHashMap<Enchantment, Integer>();
        for (int power = 1; power <= maxPower; power++) {
            for (EnchantmentData data : EnchantmentHelper.getEnchantmentDatas(power, stack, false)) {
                Integer previous = obtainable.get(data.enchantment);
                obtainable.put(data.enchantment, previous == null ? data.enchantmentLevel
                    : Math.max(previous, data.enchantmentLevel));
            }
        }
        for (Map.Entry<String, Integer> entry : requested.entrySet()) {
            Enchantment enchantment = Enchantment.REGISTRY.getObject(new ResourceLocation(entry.getKey()));
            if (enchantment == null) {
                throw new IllegalArgumentException("Unknown enchantment: " + entry.getKey());
            }
            int level = entry.getValue();
            if (level < enchantment.getMinLevel() || level > enchantment.getMaxLevel()) {
                throw new IllegalArgumentException(entry.getKey() + " requires a level between "
                    + enchantment.getMinLevel() + " and " + enchantment.getMaxLevel() + ".");
            }
            if (enchantment.isTreasureEnchantment()) {
                throw new IllegalArgumentException(entry.getKey() + " cannot be obtained at an enchanting table.");
            }
            boolean allowed = stack.getItem() == Items.BOOK ? enchantment.isAllowedOnBooks()
                : enchantment.canApplyAtEnchantingTable(stack);
            if (!allowed) {
                throw new IllegalArgumentException(entry.getKey() + " cannot be obtained on that item at a table.");
            }
            Integer availableLevel = obtainable.get(enchantment);
            if (availableLevel == null || availableLevel < level) {
                throw new IllegalArgumentException(enchantment.getTranslatedName(level)
                    + " is beyond this item's enchanting-table power in vanilla 1.12.2 (0-15 shelves)."
                    + " More drops cannot fix that; use a lower level, different material, or an anvil.");
            }
            for (Enchantment previous : resolved.keySet()) {
                if (!enchantment.isCompatibleWith(previous) || !previous.isCompatibleWith(enchantment)) {
                    throw new IllegalArgumentException(enchantment.getTranslatedName(level) + " and "
                        + previous.getTranslatedName(resolved.get(previous)) + " cannot occur together.");
                }
            }
            resolved.put(enchantment, level);
        }
        requirements = Collections.unmodifiableMap(resolved);
    }

    public boolean matches(List<EnchantmentData> enchantments) {
        Map<Enchantment, Integer> outcome = new LinkedHashMap<Enchantment, Integer>();
        for (EnchantmentData data : enchantments) {
            outcome.put(data.enchantment, data.enchantmentLevel);
        }
        return TargetRequirements.matchesAll(requirements, outcome);
    }

    public String describe() {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<Enchantment, Integer> entry : requirements.entrySet()) {
            if (result.length() > 0) {
                result.append(" + ");
            }
            result.append(entry.getKey().getTranslatedName(entry.getValue()));
        }
        return result.toString();
    }
}
