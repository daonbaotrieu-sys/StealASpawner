package net.lifesteal.stealaspawner.data;

import net.lifesteal.stealaspawner.util.LevelFormulas;
import net.lifesteal.stealaspawner.util.SasConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the ConcurrentHashMap cache of every tracked spawner plus the trust
 * table, and is the only place that writes CreatureSpawner state back onto
 * a live block. Every public mutator here is safe to call from the main
 * thread; DB writes are dispatched async internally.
 */
public final class SpawnerManager {

    private final JavaPlugin plugin;
    private final Database database;
    private final SasConfig config;

    private final Map<SpawnerKey, SpawnerData> cache = new ConcurrentHashMap<>();
    private Map<UUID, Set<UUID>> trust = new ConcurrentHashMap<>();

    public SpawnerManager(JavaPlugin plugin, Database database, SasConfig config) {
        this.plugin = plugin;
        this.database = database;
        this.config = config;
    }

    // ------------------------------------------------------------------
    // Loading / saving
    // ------------------------------------------------------------------

    /**
     * Blocking SQLite read. Called once, synchronously, from onEnable -
     * see {@link net.lifesteal.stealaspawner.StealASpawnerPlugin#onEnable()}
     * for why that's an intentional trade-off rather than an oversight.
     */
    public void loadFromDatabase() {
        List<SpawnerData> all = database.loadAllSpawners();
        for (SpawnerData data : all) {
            cache.put(data.key(), data);
        }
        trust = database.loadAllTrusts();
        plugin.getLogger().info("Loaded " + all.size() + " tracked spawners from SQLite.");
    }

    /**
     * Blocking batch save. The periodic autosave task calls this async;
     * onDisable calls it once more synchronously for a guaranteed final
     * flush before the JVM exits (async tasks aren't reliable that late
     * in shutdown).
     */
    public void saveAll() {
        database.upsertAll(List.copyOf(cache.values()));
    }

    private void saveAsync(SpawnerData data) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.upsert(data));
    }

    private void deleteAsync(SpawnerKey key) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.delete(key));
    }

    // ------------------------------------------------------------------
    // Cache access
    // ------------------------------------------------------------------

    public SpawnerData get(Location loc) {
        return cache.get(SpawnerKey.of(loc));
    }

    public SpawnerData get(Block block) {
        return cache.get(SpawnerKey.of(block));
    }

    public boolean isTracked(Block block) {
        return cache.containsKey(SpawnerKey.of(block));
    }

    public java.util.Collection<SpawnerData> all() {
        return cache.values();
    }

    /**
     * Registers a freshly placed (or re-placed) spawner: puts it in cache,
     * persists it, and immediately applies its computed stats to the block.
     */
    public void register(SpawnerData data, Block block) {
        cache.put(data.key(), data);
        saveAsync(data);
        applyToBlock(data, block);
    }

    public void remove(SpawnerKey key) {
        cache.remove(key);
        deleteAsync(key);
    }

    // ------------------------------------------------------------------
    // Trust
    // ------------------------------------------------------------------

    public boolean isTrusted(UUID owner, UUID player) {
        Set<UUID> set = trust.get(owner);
        return set != null && set.contains(player);
    }

    public boolean isOwnerOrTrusted(SpawnerData data, UUID player) {
        return data.owner().equals(player) || isTrusted(data.owner(), player);
    }

    public void addTrust(UUID owner, UUID trusted) {
        trust.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet()).add(trusted);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.addTrust(owner, trusted));
    }

    // ------------------------------------------------------------------
    // Stat computation + block application
    // ------------------------------------------------------------------

    /**
     * Writes the computed speed/yield/capacity effects onto the real
     * CreatureSpawner block. Must run on the main thread with the block's
     * chunk already loaded (callers guarantee this - either right after
     * place/upgrade, or from the chunk-load listener).
     */
    public void applyToBlock(SpawnerData data, Block block) {
        if (!(block.getState() instanceof CreatureSpawner spawner)) {
            return;
        }
        spawner.setSpawnedType(data.entityType());

        int effSpeed = LevelFormulas.effectiveLevel(data.levelSpeed(), config.speed.effectCap);
        int delay = LevelFormulas.speedDelayTicks(effSpeed, config.speed.baseDelayTicks,
                config.speed.decayFactor, config.speed.minDelayTicks);
        spawner.setDelay(delay);
        spawner.setMinSpawnDelay(delay);
        spawner.setMaxSpawnDelay(Math.max(delay, 1));

        int effCapacity = LevelFormulas.effectiveLevel(data.levelCapacity(), config.capacity.effectCap);
        int maxNearby = LevelFormulas.capacityMaxNearby(effCapacity, config.capacity.baseCap, config.capacity.hardCapServer);
        spawner.setMaxNearbyEntities(maxNearby);

        spawner.update(true, false);
    }

    /** Re-applies every tracked spawner's stats within a freshly loaded chunk. */
    public void applyAllInChunk(org.bukkit.Chunk chunk) {
        for (org.bukkit.block.BlockState state : chunk.getTileEntities()) {
            if (!(state instanceof CreatureSpawner)) continue;
            SpawnerData data = get(state.getBlock());
            if (data != null) {
                applyToBlock(data, state.getBlock());
            }
        }
    }

    public double currentYieldIncome(SpawnerData data) {
        double base = config.baseIncome.getOrDefault(data.entityType(), 0.0);
        int effYield = LevelFormulas.effectiveLevel(data.levelYield(), config.yield.effectCap);
        return LevelFormulas.yieldIncome(effYield, base, config.yield.incomePerLevel);
    }

    // ------------------------------------------------------------------
    // Upgrades
    // ------------------------------------------------------------------

    public double upgradeCost(SpawnerData data, SpawnerData.Track track, int amount) {
        var t = trackConfig(track);
        int current = data.level(track);
        int clampedAmount = Math.min(amount, config.maxLevel - current);
        if (clampedAmount <= 0) return Double.POSITIVE_INFINITY;
        return LevelFormulas.totalUpgradeCost(current, clampedAmount, t.baseCost, t.growthRate, t.softCapLevel);
    }

    /** Applies `amount` levels (already paid for by the caller) and re-syncs the block if loaded. */
    public void applyUpgrade(SpawnerData data, SpawnerData.Track track, int amount) {
        int current = data.level(track);
        int next = Math.min(config.maxLevel, current + amount);
        data.setLevel(track, next);
        saveAsync(data);

        World world = Bukkit.getWorld(data.key().world());
        if (world != null) {
            Location loc = new Location(world, data.key().x(), data.key().y(), data.key().z());
            if (world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                applyToBlock(data, loc.getBlock());
            }
        }
    }

    private SasConfig.Track trackConfig(SpawnerData.Track track) {
        return switch (track) {
            case SPEED -> config.speed;
            case YIELD -> config.yield;
            case CAPACITY -> config.capacity;
        };
    }
}
