package net.lifesteal.stealaspawner.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Checks whether a player may place/break a block at a location, deferring
 * to WorldGuard and/or GriefPrevention when they're installed. With neither
 * plugin present this always allows - StealASpawner doesn't invent its own
 * claim system, it just respects whichever one the server already runs.
 */
public final class RegionHook {

    private final JavaPlugin plugin;
    private WorldGuardSupport worldGuard;
    private GriefPreventionSupport griefPrevention;

    public RegionHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        if (plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
            worldGuard = new WorldGuardSupport();
            plugin.getLogger().info("WorldGuard detected, region protection enabled for spawner place/break.");
        }
        if (plugin.getServer().getPluginManager().isPluginEnabled("GriefPrevention")) {
            griefPrevention = new GriefPreventionSupport();
            plugin.getLogger().info("GriefPrevention detected, claim protection enabled for spawner place/break.");
        }
    }

    /**
     * True if nothing objects. A spawner place/break event should only
     * proceed with StealASpawner's own logic when this returns true.
     */
    public boolean canBuild(Player player, Location location) {
        if (worldGuard != null && !worldGuard.canBuild(player, location)) {
            return false;
        }
        if (griefPrevention != null && !griefPrevention.canBuild(player, location)) {
            return false;
        }
        return true;
    }
}
