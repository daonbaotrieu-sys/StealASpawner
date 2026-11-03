package net.lifesteal.stealaspawner.listeners;

import net.lifesteal.stealaspawner.data.SpawnerData;
import net.lifesteal.stealaspawner.data.SpawnerManager;
import net.lifesteal.stealaspawner.util.MsgUtil;
import net.lifesteal.stealaspawner.util.RegionHook;
import net.lifesteal.stealaspawner.util.SasConfig;
import net.lifesteal.stealaspawner.util.SpawnerItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class SpawnerBreakListener implements Listener {

    private final SpawnerManager manager;
    private final RegionHook regionHook;
    private final SasConfig config;

    public SpawnerBreakListener(SpawnerManager manager, RegionHook regionHook, SasConfig config) {
        this.manager = manager;
        this.regionHook = regionHook;
        this.config = config;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) {
            return;
        }

        SpawnerData data = manager.get(block);
        if (data == null) {
            // Untracked spawner (natural dungeon spawner, or placed before
            // this plugin existed) - leave vanilla behaviour alone.
            return;
        }

        Player player = event.getPlayer();

        if (!regionHook.canBuild(player, block.getLocation())) {
            event.setCancelled(true);
            return;
        }

        if (manager.isOwnerOrTrusted(data, player.getUniqueId())) {
            reclaim(event, data);
            return;
        }

        steal(event, data, player);
    }

    /** Owner (or someone they trusted) always gets the item back, levels intact, no tool requirement. */
    private void reclaim(BlockBreakEvent event, SpawnerData data) {
        event.setDropItems(false);
        ItemStack item = SpawnerItemFactory.create(data.entityType(), data.levelSpeed(), data.levelYield(), data.levelCapacity());
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), item);
        manager.remove(data.key());
    }

    private void steal(BlockBreakEvent event, SpawnerData data, Player thief) {
        if (!thief.hasPermission("stealaspawner.steal")) {
            event.setCancelled(true);
            MsgUtil.send(thief, config, "no-permission");
            return;
        }

        if (!hasSilkTouchPickaxe(thief)) {
            event.setCancelled(true);
            MsgUtil.actionBar(thief, config, "need-silk-touch");
            return;
        }

        boolean bypassGrace = thief.hasPermission("stealaspawner.admin");
        if (!bypassGrace && data.inGracePeriod(config.gracePeriodMillis)) {
            event.setCancelled(true);
            MsgUtil.actionBar(thief, config, "grace-protected");
            return;
        }

        Location loc = event.getBlock().getLocation();

        event.setDropItems(false);
        ItemStack item = SpawnerItemFactory.create(data.entityType(), data.levelSpeed(), data.levelYield(), data.levelCapacity());
        loc.getWorld().dropItemNaturally(loc, item);

        manager.remove(data.key());

        loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        loc.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(0.5, 0.5, 0.5), 40, 0.4, 0.4, 0.4, 0.05);

        broadcastNearby(loc, thief, data);
    }

    private boolean hasSilkTouchPickaxe(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!hand.getType().name().endsWith("_PICKAXE")) {
            return false;
        }
        return hand.getItemMeta() != null && hand.getItemMeta().hasEnchant(Enchantment.SILK_TOUCH);
    }

    private void broadcastNearby(Location loc, Player thief, SpawnerData data) {
        OfflinePlayer owner = Bukkit.getOfflinePlayer(data.owner());
        String ownerName = owner.getName() != null ? owner.getName() : "???";

        Map<String, String> placeholders = Map.of(
                "thief", thief.getName(),
                "owner", ownerName,
                "entity", SpawnerItemFactory.prettyName(data.entityType()),
                "speed", String.valueOf(data.levelSpeed()),
                "yield", String.valueOf(data.levelYield()),
                "capacity", String.valueOf(data.levelCapacity())
        );

        double radiusSq = (double) config.broadcastRadius * config.broadcastRadius;
        for (Player nearby : loc.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(loc) <= radiusSq) {
                MsgUtil.actionBar(nearby, config, "steal-broadcast", placeholders);
            }
        }
    }
}
