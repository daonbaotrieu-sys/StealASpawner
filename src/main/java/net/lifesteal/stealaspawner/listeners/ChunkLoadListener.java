package net.lifesteal.stealaspawner.listeners;

import net.lifesteal.stealaspawner.data.SpawnerManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * The plugin never force-loads chunks just to push upgrade stats onto
 * their spawner blocks (that would be a TPS bomb on a big spawner-economy
 * server). Instead, whenever a chunk containing a tracked spawner loads
 * naturally, this re-applies the cached levels onto the real block -
 * covers server restarts, /reload, and players simply walking back in.
 */
public final class ChunkLoadListener implements Listener {

    private final SpawnerManager manager;

    public ChunkLoadListener(SpawnerManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        manager.applyAllInChunk(event.getChunk());
    }
}
