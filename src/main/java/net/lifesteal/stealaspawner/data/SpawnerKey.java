package net.lifesteal.stealaspawner.data;

import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * Deliberately not using org.bukkit.Location as a map key: its equals()
 * also factors in pitch/yaw/world *instance* and its hashCode involves a
 * sqrt, both unwanted for a block-position lookup table. This record is
 * just (world name, block x, y, z).
 */
public record SpawnerKey(String world, int x, int y, int z) {

    public static SpawnerKey of(Location loc) {
        return new SpawnerKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public static SpawnerKey of(Block block) {
        return new SpawnerKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }
}
