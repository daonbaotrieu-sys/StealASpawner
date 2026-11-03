package net.lifesteal.stealaspawner.data;

import org.bukkit.entity.EntityType;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runtime state for one placed, tracked spawner. One instance lives in
 * {@link SpawnerManager}'s cache per placed spawner; the SQLite row is just
 * this object's durable mirror.
 */
public final class SpawnerData {

    private final SpawnerKey key;
    private volatile UUID owner;
    private volatile EntityType entityType;
    private volatile long placedAt;

    private final AtomicInteger levelSpeed;
    private final AtomicInteger levelYield;
    private final AtomicInteger levelCapacity;

    private volatile double incomeTotal;

    public SpawnerData(SpawnerKey key, UUID owner, EntityType entityType, long placedAt,
                        int levelSpeed, int levelYield, int levelCapacity, double incomeTotal) {
        this.key = key;
        this.owner = owner;
        this.entityType = entityType;
        this.placedAt = placedAt;
        this.levelSpeed = new AtomicInteger(levelSpeed);
        this.levelYield = new AtomicInteger(levelYield);
        this.levelCapacity = new AtomicInteger(levelCapacity);
        this.incomeTotal = incomeTotal;
    }

    public SpawnerKey key() {
        return key;
    }

    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public EntityType entityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public long placedAt() {
        return placedAt;
    }

    public void setPlacedAt(long placedAt) {
        this.placedAt = placedAt;
    }

    public boolean inGracePeriod(long graceMillis) {
        return System.currentTimeMillis() - placedAt < graceMillis;
    }

    public int levelSpeed() {
        return levelSpeed.get();
    }

    public int levelYield() {
        return levelYield.get();
    }

    public int levelCapacity() {
        return levelCapacity.get();
    }

    public int level(Track track) {
        return switch (track) {
            case SPEED -> levelSpeed();
            case YIELD -> levelYield();
            case CAPACITY -> levelCapacity();
        };
    }

    public void setLevel(Track track, int value) {
        switch (track) {
            case SPEED -> levelSpeed.set(value);
            case YIELD -> levelYield.set(value);
            case CAPACITY -> levelCapacity.set(value);
        }
    }

    public double incomeTotal() {
        return incomeTotal;
    }

    public void addIncome(double amount) {
        incomeTotal += amount;
    }

    /** The 3 independent upgrade tracks every spawner has. */
    public enum Track {
        SPEED, YIELD, CAPACITY
    }
}
