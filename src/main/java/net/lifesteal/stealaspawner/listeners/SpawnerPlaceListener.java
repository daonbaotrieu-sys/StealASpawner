package net.lifesteal.stealaspawner.listeners;

import net.lifesteal.stealaspawner.data.SpawnerData;
import net.lifesteal.stealaspawner.data.SpawnerKey;
import net.lifesteal.stealaspawner.data.SpawnerManager;
import net.lifesteal.stealaspawner.util.MsgUtil;
import net.lifesteal.stealaspawner.util.RegionHook;
import net.lifesteal.stealaspawner.util.SasConfig;
import net.lifesteal.stealaspawner.util.SpawnerItemFactory;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class SpawnerPlaceListener implements Listener {

    private final SpawnerManager manager;
    private final RegionHook regionHook;
    private final SasConfig config;

    public SpawnerPlaceListener(SpawnerManager manager, RegionHook regionHook, SasConfig config) {
        this.manager = manager;
        this.regionHook = regionHook;
        this.config = config;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack held = event.getItemInHand();
        if (!SpawnerItemFactory.isSasSpawner(held)) {
            // Not our item - a vanilla silk-touched spawner placed by some
            // other means is intentionally left untracked and un-economic.
            return;
        }

        Player player = event.getPlayer();

        if (!regionHook.canBuild(player, event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }

        EntityType type = SpawnerItemFactory.readEntityType(held);
        int speed = SpawnerItemFactory.readLevel(held, SpawnerData.Track.SPEED);
        int yield = SpawnerItemFactory.readLevel(held, SpawnerData.Track.YIELD);
        int capacity = SpawnerItemFactory.readLevel(held, SpawnerData.Track.CAPACITY);

        SpawnerKey key = SpawnerKey.of(event.getBlock());
        SpawnerData data = new SpawnerData(key, player.getUniqueId(), type,
                System.currentTimeMillis(), speed, yield, capacity, 0.0);

        manager.register(data, event.getBlock());

        MsgUtil.send(player, config, "spawner-placed", Map.of("entity", SpawnerItemFactory.prettyName(type)));
    }
}
