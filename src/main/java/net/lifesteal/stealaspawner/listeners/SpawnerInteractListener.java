package net.lifesteal.stealaspawner.listeners;

import net.lifesteal.stealaspawner.data.SpawnerData;
import net.lifesteal.stealaspawner.data.SpawnerManager;
import net.lifesteal.stealaspawner.gui.SpawnerPanelGUI;
import net.lifesteal.stealaspawner.util.EconomyHook;
import net.lifesteal.stealaspawner.util.MsgUtil;
import net.lifesteal.stealaspawner.util.SasConfig;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

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

        SpawnerPanelGUI.open(player, data, config, economy, manager);
    }
}
