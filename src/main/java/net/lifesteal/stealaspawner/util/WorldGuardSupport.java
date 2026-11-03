package net.lifesteal.stealaspawner.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Kept in its own class on purpose: the JVM only resolves WorldGuard's
 * classes the first time one of these methods actually runs, so a server
 * without WorldGuard installed never touches this file and never throws
 * NoClassDefFoundError. {@link RegionHook} only calls into here after
 * confirming the plugin is enabled.
 */
final class WorldGuardSupport {

    boolean canBuild(Player player, Location location) {
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        return query.testBuild(BukkitAdapter.adapt(location), localPlayer);
    }
}
