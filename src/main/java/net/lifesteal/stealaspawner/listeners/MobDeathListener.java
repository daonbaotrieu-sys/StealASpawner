package net.lifesteal.stealaspawner.listeners;

import net.lifesteal.stealaspawner.util.MsgUtil;
import net.lifesteal.stealaspawner.util.SasConfig;
import net.lifesteal.stealaspawner.util.SpawnerItemFactory;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Every player kill rolls against config.yml's drop-rates table. Hit it,
 * and a fresh level-1/1/1 spawner for that mob drops at the corpse.
 */
public final class MobDeathListener implements Listener {

    private final SasConfig config;

    public MobDeathListener(SasConfig config) {
        this.config = config;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }

        EntityType type = event.getEntityType();
        Double chance = config.dropRates.get(type);
        if (chance == null || chance <= 0.0) {
            return;
        }

        // Spec requires ThreadLocalRandom specifically, never Math.random().
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }

        ItemStack spawnerItem = SpawnerItemFactory.createFresh(type);
        event.getEntity().getWorld().dropItemNaturally(event.getEntity().getLocation(), spawnerItem);

        MsgUtil.send(killer, config, "spawner-dropped",
                Map.of("entity", SpawnerItemFactory.prettyName(type)));
    }
}
