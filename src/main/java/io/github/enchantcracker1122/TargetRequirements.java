package io.github.enchantcracker1122;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Command syntax and AND matching, independent of Minecraft's registries. */
public final class TargetRequirements {
    private TargetRequirements() {
    }

    public static Map<String, Integer> parse(String[] args, int start) {
        int count = args.length - start;
        if (count <= 0 || (count > 1 && count % 2 != 0)) {
            throw new IllegalArgumentException("Use enchantment/level pairs, e.g. efficiency 4 unbreaking 3 fortune 3.");
        }
        Map<String, Integer> requirements = new LinkedHashMap<String, Integer>();
        for (int i = start; i < args.length; i += 2) {
            String name = args[i];
            if (!name.matches("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+")) {
                throw new IllegalArgumentException("Invalid enchantment name: " + name);
            }
            String id = name.indexOf(':') < 0 ? "minecraft:" + name : name;
            int level = 1;
            if (count != 1) {
                try {
                    level = Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Level for " + name + " must be a positive whole number.");
                }
            }
            if (level < 1) {
                throw new IllegalArgumentException("Level for " + name + " must be at least 1.");
            }
            Integer previous = requirements.get(id);
            requirements.put(id, previous == null ? level : Math.max(previous, level));
        }
        return Collections.unmodifiableMap(requirements);
    }

    /** Extra enchantments are allowed, but every requested minimum must be met. */
    public static <T> boolean matchesAll(Map<T, Integer> requirements, Map<T, Integer> outcome) {
        if (requirements.isEmpty()) {
            return false;
        }
        for (Map.Entry<T, Integer> entry : requirements.entrySet()) {
            Integer level = outcome.get(entry.getKey());
            if (level == null || level < entry.getValue()) {
                return false;
            }
        }
        return true;
    }
}
