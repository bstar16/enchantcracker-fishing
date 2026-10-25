package io.github.enchantcracker1122;

import java.util.Arrays;
import java.util.List;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class EnchantmentTargetsTest {
    @BeforeClass
    public static void initializeMinecraft() {
        Bootstrap.register();
    }

    private EnchantmentTargets target(String... args) {
        return new EnchantmentTargets(new ItemStack(Items.DIAMOND_PICKAXE), TargetRequirements.parse(args, 0));
    }

    @Test
    public void matchesNativeEnchantmentListsAsACombination() {
        EnchantmentTargets wanted = target("efficiency", "4", "unbreaking", "3", "fortune", "3");
        assertFalse(wanted.matches(Arrays.asList(new EnchantmentData(Enchantments.EFFICIENCY, 4),
            new EnchantmentData(Enchantments.UNBREAKING, 3))));
        assertTrue(wanted.matches(Arrays.asList(new EnchantmentData(Enchantments.EFFICIENCY, 4),
            new EnchantmentData(Enchantments.UNBREAKING, 3), new EnchantmentData(Enchantments.FORTUNE, 3))));
    }

    @Test
    public void findsActualNativeTripleEnchantmentOutcome() {
        EnchantmentTargets wanted = target("efficiency", "4", "unbreaking", "3", "fortune", "3");
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        for (int seed = 0; seed < 10000; seed++) {
            int cost = EnchantmentSimulator.costs(seed, 15, stack)[2];
            List<EnchantmentData> outcome = EnchantmentSimulator.enchantments(seed, stack, 2, cost);
            if (wanted.matches(outcome)) {
                assertTrue(contains(outcome, Enchantments.EFFICIENCY, 4));
                assertTrue(contains(outcome, Enchantments.UNBREAKING, 3));
                assertTrue(contains(outcome, Enchantments.FORTUNE, 3));
                return;
            }
        }
        fail("No native triple-enchantment outcome found");
    }

    private boolean contains(List<EnchantmentData> list, net.minecraft.enchantment.Enchantment enchantment, int level) {
        for (EnchantmentData data : list) {
            if (data.enchantment == enchantment && data.enchantmentLevel >= level) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void rejectsImpossibleRequests() {
        invalid("silk_touch", "1", "fortune", "3");
        invalid("fortune", "3", "silk_touch", "1");
        invalid("mending", "1");
        invalid("sharpness", "5");
        invalid("unbreaking", "4");
        invalid("unknown", "1");
        invalid("efficiency", "5"); // diamond cannot reach this at a vanilla table
    }

    @Test
    public void woodenSwordCannotReachSharpnessFiveButGoldCan() {
        String[] requirements = {"sharpness", "5", "unbreaking", "3", "looting", "3"};
        try {
            new EnchantmentTargets(new ItemStack(Items.WOODEN_SWORD), TargetRequirements.parse(requirements, 0));
            fail("Wood has insufficient enchantability for Sharpness V at a table");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("beyond this item's"));
        }
        ItemStack gold = new ItemStack(Items.GOLDEN_SWORD);
        EnchantmentTargets wanted = new EnchantmentTargets(gold, TargetRequirements.parse(requirements, 0));
        final long[] checked = {0};
        Long found = DropSearch.find(new JavaRandom48(123456789L), () -> checked[0] >= 100000,
            n -> checked[0] = n, (drops, seed) -> {
                int cost = EnchantmentSimulator.costs(seed, 15, gold)[2];
                return wanted.matches(EnchantmentSimulator.enchantments(seed, gold, 2, cost)) ? drops : null;
            });
        assertNotNull("Native golden sword triple should be found", found);
        assertTrue("Regression must exercise beyond old cap", found > 64);
        System.out.println("Native golden sword Sharpness V + Unbreaking III + Looting III found at " + found + " drops.");
    }

    private void invalid(String... args) {
        try {
            target(args);
            fail("Expected impossible target to be rejected");
        } catch (IllegalArgumentException expected) {
            assertFalse(expected.getMessage().isEmpty());
        }
    }
}
