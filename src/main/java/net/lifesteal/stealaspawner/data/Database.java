package net.lifesteal.stealaspawner.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All methods here are blocking JDBC - every caller in the plugin is
 * expected to invoke these from an async Bukkit task, never the main
 * thread. Failures are logged and swallowed rather than thrown, since a
 * dropped autosave shouldn't crash a running server.
 */
public final class Database {

    private final JavaPlugin plugin;
    private HikariDataSource dataSource;

    public Database(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void connect(String fileName) {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File dbFile = new File(dataFolder, fileName);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        // SQLite only supports one writer at a time; keep the pool small so
        // Hikari doesn't hand out connections that just queue up on a lock.
        config.setMaximumPoolSize(2);
        config.setPoolName("StealASpawner-SQLite");
        dataSource = new HikariDataSource(config);

        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS spawners (
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        owner_uuid TEXT NOT NULL,
                        entity_type TEXT NOT NULL,
                        placed_at INTEGER NOT NULL,
                        level_speed INTEGER NOT NULL DEFAULT 1,
                        level_yield INTEGER NOT NULL DEFAULT 1,
                        level_capacity INTEGER NOT NULL DEFAULT 1,
                        income_total REAL NOT NULL DEFAULT 0,
                        PRIMARY KEY (world, x, y, z)
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS trusted_players (
                        owner_uuid TEXT NOT NULL,
                        trusted_uuid TEXT NOT NULL,
                        PRIMARY KEY (owner_uuid, trusted_uuid)
                    )
                    """);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialise SQLite schema: " + e.getMessage());
        }
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public List<SpawnerData> loadAllSpawners() {
        List<SpawnerData> result = new ArrayList<>();
        String sql = "SELECT * FROM spawners";
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                SpawnerKey key = new SpawnerKey(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                result.add(new SpawnerData(
                        key,
                        UUID.fromString(rs.getString("owner_uuid")),
                        EntityType.valueOf(rs.getString("entity_type")),
                        rs.getLong("placed_at"),
                        rs.getInt("level_speed"),
                        rs.getInt("level_yield"),
                        rs.getInt("level_capacity"),
                        rs.getDouble("income_total")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load spawners from SQLite: " + e.getMessage());
        }
        return result;
    }

    public void upsert(SpawnerData data) {
        String sql = """
                INSERT INTO spawners (world, x, y, z, owner_uuid, entity_type, placed_at,
                                       level_speed, level_yield, level_capacity, income_total)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(world, x, y, z) DO UPDATE SET
                    owner_uuid = excluded.owner_uuid,
                    entity_type = excluded.entity_type,
                    placed_at = excluded.placed_at,
                    level_speed = excluded.level_speed,
                    level_yield = excluded.level_yield,
                    level_capacity = excluded.level_capacity,
                    income_total = excluded.income_total
                """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            SpawnerKey key = data.key();
            ps.setString(1, key.world());
            ps.setInt(2, key.x());
            ps.setInt(3, key.y());
            ps.setInt(4, key.z());
            ps.setString(5, data.owner().toString());
            ps.setString(6, data.entityType().name());
            ps.setLong(7, data.placedAt());
            ps.setInt(8, data.levelSpeed());
            ps.setInt(9, data.levelYield());
            ps.setInt(10, data.levelCapacity());
            ps.setDouble(11, data.incomeTotal());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to upsert spawner at " + data.key() + ": " + e.getMessage());
        }
    }

    public void upsertAll(List<SpawnerData> all) {
        String sql = """
                INSERT INTO spawners (world, x, y, z, owner_uuid, entity_type, placed_at,
                                       level_speed, level_yield, level_capacity, income_total)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(world, x, y, z) DO UPDATE SET
                    owner_uuid = excluded.owner_uuid,
                    entity_type = excluded.entity_type,
                    placed_at = excluded.placed_at,
                    level_speed = excluded.level_speed,
                    level_yield = excluded.level_yield,
                    level_capacity = excluded.level_capacity,
                    income_total = excluded.income_total
                """;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (SpawnerData data : all) {
                    SpawnerKey key = data.key();
                    ps.setString(1, key.world());
                    ps.setInt(2, key.x());
                    ps.setInt(3, key.y());
                    ps.setInt(4, key.z());
                    ps.setString(5, data.owner().toString());
                    ps.setString(6, data.entityType().name());
                    ps.setLong(7, data.placedAt());
                    ps.setInt(8, data.levelSpeed());
                    ps.setInt(9, data.levelYield());
                    ps.setInt(10, data.levelCapacity());
                    ps.setDouble(11, data.incomeTotal());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to batch-save spawners: " + e.getMessage());
        }
    }

    public void delete(SpawnerKey key) {
        String sql = "DELETE FROM spawners WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key.world());
            ps.setInt(2, key.x());
            ps.setInt(3, key.y());
            ps.setInt(4, key.z());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete spawner at " + key + ": " + e.getMessage());
        }
    }

    /** owner UUID -> set of trusted UUIDs, loaded once at startup. */
    public Map<UUID, Set<UUID>> loadAllTrusts() {
        Map<UUID, Set<UUID>> result = new ConcurrentHashMap<>();
        String sql = "SELECT owner_uuid, trusted_uuid FROM trusted_players";
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                UUID trusted = UUID.fromString(rs.getString("trusted_uuid"));
                result.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet()).add(trusted);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load trust table: " + e.getMessage());
        }
        return result;
    }

    public void addTrust(UUID owner, UUID trusted) {
        String sql = "INSERT OR IGNORE INTO trusted_players (owner_uuid, trusted_uuid) VALUES (?, ?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, owner.toString());
            ps.setString(2, trusted.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save trust entry: " + e.getMessage());
        }
    }
}
