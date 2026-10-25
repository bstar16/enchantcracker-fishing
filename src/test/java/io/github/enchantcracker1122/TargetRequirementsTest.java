package io.github.enchantcracker1122;

import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class TargetRequirementsTest {
    private Map<String, Integer> parse(String... args) {
        return TargetRequirements.parse(args, 0);
    }

    @Test
    public void requiresEveryEnchantmentAtMinimumLevel() {
        Map<String, Integer> wanted = parse("efficiency", "4", "unbreaking", "3", "fortune", "3");
        assertFalse(TargetRequirements.matchesAll(wanted, parse("efficiency", "4")));
        assertFalse(TargetRequirements.matchesAll(wanted, parse("efficiency", "4", "unbreaking", "3")));
        assertFalse(TargetRequirements.matchesAll(wanted, parse("efficiency", "4", "unbreaking", "3", "fortune", "2")));
        assertTrue(TargetRequirements.matchesAll(wanted, parse("fortune", "3", "unbreaking", "3", "efficiency", "4")));
        assertTrue(TargetRequirements.matchesAll(wanted, parse("fortune", "3", "unbreaking", "3", "efficiency", "5", "other", "1")));
        assertFalse(TargetRequirements.matchesAll(Collections.<String, Integer>emptyMap(), wanted));
    }

    @Test
    public void preservesSingleTargetAndNormalizesDuplicates() {
        assertEquals(Integer.valueOf(1), parse("silk_touch").get("minecraft:silk_touch"));
        Map<String, Integer> wanted = parse("efficiency", "4", "minecraft:efficiency", "5", "efficiency", "2");
        assertEquals(1, wanted.size());
        assertEquals(Integer.valueOf(5), wanted.get("minecraft:efficiency"));
        assertEquals(Integer.valueOf(3), TargetRequirements.parse(
            new String[] {"target", "diamond_pickaxe", "unbreaking", "3"}, 2).get("minecraft:unbreaking"));
    }

    @Test
    public void rejectsMalformedCommandsInsteadOfIgnoringArguments() {
        invalid();
        invalid("efficiency", "4", "unbreaking");
        invalid("efficiency", "4", "unbreaking", "three");
        invalid("efficiency", "0");
        invalid("efficiency", "-1");
        invalid("efficiency", "2147483648");
        invalid("bad:name:extra", "1");
    }

    private void invalid(String... args) {
        try {
            parse(args);
            fail("Expected invalid command to be rejected");
        } catch (IllegalArgumentException expected) {
            assertFalse(expected.getMessage().isEmpty());
        }
    }
}
