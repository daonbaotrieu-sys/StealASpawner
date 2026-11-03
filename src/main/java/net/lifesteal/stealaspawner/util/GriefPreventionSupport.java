package net.lifesteal.stealaspawner.util;

import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * See {@link WorldGuardSupport} for why this lives in its own class.
 */
final class GriefPreventionSupport {

    boolean canBuild(Player player, Location location) {
        // allowBuild returns null when the action is permitted, or a
        // human-readable denial reason string otherwise.
        return GriefPrevention.instance.allowBuild(player, location) == null;
    }
}
