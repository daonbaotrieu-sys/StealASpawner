package net.lifesteal.stealaspawner.listeners;

import net.lifesteal.stealaspawner.data.SpawnerData;
import net.lifesteal.stealaspawner.data.SpawnerManager;
import net.lifesteal.stealaspawner.gui.SpawnerPanelGUI;
import net.lifesteal.stealaspawner.util.EconomyHook;
import net.lifesteal.stealaspawner.util.MsgUtil;
import net.lifesteal.stealaspawner.util.SasConfig;
import net.lifesteal.stealaspawner.util.SpawnerItemFactory;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class SpawnerInteractListener implements Listener {

    private final SpawnerManager manager;
    private final SasConfig config;
    private final EconomyHook economy;

    public SpawnerInteractListener(SpawnerManager manager, SasConfig config, EconomyHook economy) {
        this.manager = manager;
        this.config = config;
        this.economy = economy;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Paper fires this event once per hand; only react to the main hand
        // to avoid opening the panel twice on a single right-click.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (event.getClickedBlock().getType() != Material.SPAWNER) {
            return;
        }

        SpawnerData data = manager.get(event.getClickedBlock());
        if (data == null) {
            return;
        }

        Player player = event.getPlayer();
        event.setCancelled(true);

        if (!manager.isOwnerOrTrusted(data, player.getUniqueId())) {
            MsgUtil.send(player, config, "not-your-spawner");
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (player.isSneaking() && SpawnerItemFactory.isSasSpawner(held)) {
            mergeHeldSpawner(player, data, held, event.getClickedBlock());
            return;
        }

        SpawnerPanelGUI.open(player, data, config, economy, manager);
    }

    /**
     * Shift+right-click with a matching-type spawner item in hand merges
     * that item's 3 track levels straight into the placed spawner, free of
     * currency - an alternative sink for spawner items besides selling or
     * placing them fresh. Requires the same EntityType; a mismatched item
     * is treated as a deliberate (failed) merge attempt, not silently
     * reinterpreted as a plain right-click.
     */
    private void mergeHeldSpawner(Player player, SpawnerData data, ItemStack held, org.bukkit.block.Block block) {
        EntityType heldType = SpawnerItemFactory.readEntityType(held);
        if (heldType != data.entityType()) {
            MsgUtil.actionBar(player, config, "merge-wrong-type");
            return;
        }

        int addSpeed = SpawnerItemFactory.readLevel(held, SpawnerData.Track.SPEED);
        int addYield = SpawnerItemFactory.readLevel(held, SpawnerData.Track.YIELD);
        int addCapacity = SpawnerItemFactory.readLevel(held, SpawnerData.Track.CAPACITY);

        manager.applyMerge(data, addSpeed, addYield, addCapacity);

        if (held.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            held.setAmount(held.getAmount() - 1);
        }

        block.getWorld().playSound(block.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.5, 0.5), 12, 0.3, 0.3, 0.3, 0);

        MsgUtil.actionBar(player, config, "merge-success", Map.of(
                "speed", String.valueOf(data.levelSpeed()),
                "yield", String.valueOf(data.levelYield()),
                "capacity", String.valueOf(data.levelCapacity())
        ));
    }
}
