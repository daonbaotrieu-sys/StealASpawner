package net.lifesteal.stealaspawner.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;

/**
 * Reads config.yml once into plain fields so hot paths (mob death, income
 * tick) never walk a ConfigurationSection. Call {@link #reload()} after
 * plugin.reloadConfig() to refresh every field in place.
 */
public final class SasConfig {

    public static final class Track {
        public double baseCost;
        public double growthRate;
        public int softCapLevel;
        public int effectCap;
        // speed-only
        public int baseDelayTicks;
        public int minDelayTicks;
        public double decayFactor;
        // yield-only
        public double incomePerLevel;
        // capacity-only
        public int baseCap;
        public int hardCapServer;
    }

    private final JavaPlugin plugin;

    public String dbFile;
    public long gracePeriodMillis;
    public long incomeIntervalTicks;
    public long autosaveIntervalTicks;
    public int broadcastRadius;
    public int maxLevel;

    public final Map<EntityType, Double> dropRates = new EnumMap<>(EntityType.class);
    public final Map<EntityType, Double> baseIncome = new EnumMap<>(EntityType.class);
    public final Map<EntityType, Double> shopPrices = new EnumMap<>(EntityType.class);

    public final Track speed = new Track();
    public final Track yield = new Track();
    public final Track capacity = new Track();

    public final Map<String, String> messages = new java.util.HashMap<>();

    public SasConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        var cfg = plugin.getConfig();

        dbFile = cfg.getString("database.file", "spawners.db");
        gracePeriodMillis = cfg.getLong("grace-period-seconds", 300) * 1000L;
        incomeIntervalTicks = cfg.getLong("income-interval-seconds", 20) * 20L;
        autosaveIntervalTicks = cfg.getLong("autosave-interval-minutes", 5) * 60L * 20L;
        broadcastRadius = cfg.getInt("broadcast-radius", 50);
        maxLevel = cfg.getInt("max-level", 1000);

        loadEntityMap(cfg.getConfigurationSection("drop-rates"), dropRates);
        loadEntityMap(cfg.getConfigurationSection("base-income"), baseIncome);
        loadEntityMap(cfg.getConfigurationSection("shop"), shopPrices);

        loadTrack(cfg.getConfigurationSection("tracks.speed"), speed);
        speed.baseDelayTicks = cfg.getInt("tracks.speed.base-delay-ticks", 400);
        speed.minDelayTicks = cfg.getInt("tracks.speed.min-delay-ticks", 20);
        speed.decayFactor = cfg.getDouble("tracks.speed.decay-factor", 0.985);

        loadTrack(cfg.getConfigurationSection("tracks.yield"), yield);
        yield.incomePerLevel = cfg.getDouble("tracks.yield.income-per-level", 0.01);

        loadTrack(cfg.getConfigurationSection("tracks.capacity"), capacity);
        capacity.baseCap = cfg.getInt("tracks.capacity.base-cap", 6);
        capacity.hardCapServer = cfg.getInt("tracks.capacity.hard-cap-server", 30);

        messages.clear();
        ConfigurationSection msgSec = cfg.getConfigurationSection("messages");
        if (msgSec != null) {
            for (String key : msgSec.getKeys(false)) {
                messages.put(key, msgSec.getString(key, ""));
            }
        }
    }

    private void loadTrack(ConfigurationSection sec, Track track) {
        if (sec == null) return;
        track.baseCost = sec.getDouble("base-cost", 100.0);
        track.growthRate = sec.getDouble("growth-rate", 0.08);
        track.softCapLevel = sec.getInt("soft-cap-level", 200);
        track.effectCap = sec.getInt("effect-cap", 60);
    }

    private void loadEntityMap(ConfigurationSection sec, Map<EntityType, Double> target) {
        target.clear();
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                EntityType type = EntityType.valueOf(key.toUpperCase());
                target.put(type, sec.getDouble(key));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("config.yml: unknown EntityType '" + key + "', skipping.");
            }
        }
    }

    public String msg(String key) {
        return messages.getOrDefault(key, "<red>Missing message: " + key);
    }
}
