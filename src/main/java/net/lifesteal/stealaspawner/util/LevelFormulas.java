package net.lifesteal.stealaspawner.util;

/**
 * Pure, stateless math for the 3-track upgrade system. No config or Bukkit
 * types touched here on purpose - every call site passes the exact numbers
 * involved, which makes the formulas trivial to unit test in isolation.
 *
 * effectiveLevel = min(nominalLevel, effectCap)
 * cost(level)    = baseCost * (1 + level * growthRate) * (1 + level / softCapLevel)
 *                  ^ cost to go FROM level TO level+1.
 */
public final class LevelFormulas {

    private LevelFormulas() {
    }

    public static int effectiveLevel(int nominalLevel, int effectCap) {
        return Math.min(nominalLevel, effectCap);
    }

    /** Cost of a single upgrade step, from `level` to `level + 1`. */
    public static double stepCost(int level, double baseCost, double growthRate, int softCapLevel) {
        double growthFactor = 1.0 + level * growthRate;
        double softCapFactor = 1.0 + (double) level / softCapLevel;
        return baseCost * growthFactor * softCapFactor;
    }

    /**
     * Total cost to buy `amount` consecutive levels starting at `currentLevel`.
     * Caller is responsible for clamping amount so currentLevel + amount
     * never exceeds max-level.
     */
    public static double totalUpgradeCost(int currentLevel, int amount, double baseCost,
                                           double growthRate, int softCapLevel) {
        double total = 0.0;
        for (int i = 0; i < amount; i++) {
            total += stepCost(currentLevel + i, baseCost, growthRate, softCapLevel);
        }
        return total;
    }

    /**
     * Speed track: spawner delay in ticks. Decays geometrically with
     * effective level, floored at minDelayTicks so a maxed-out spawner
     * still can't out-tick vanilla's fastest legal cycle.
     */
    public static int speedDelayTicks(int effectiveLevel, int baseDelayTicks,
                                       double decayFactor, int minDelayTicks) {
        double delay = baseDelayTicks * Math.pow(decayFactor, effectiveLevel);
        return (int) Math.max(minDelayTicks, Math.round(delay));
    }

    /** Yield track: income multiplier applied on top of the mob's base income. */
    public static double yieldIncome(int effectiveLevel, double baseIncome, double incomePerLevel) {
        return baseIncome * (1.0 + incomePerLevel * effectiveLevel);
    }

    /** Capacity track: max nearby entities, clamped to the server-wide hard cap. */
    public static int capacityMaxNearby(int effectiveLevel, int baseCap, int hardCapServer) {
        return Math.min(baseCap + effectiveLevel, hardCapServer);
    }
}
