package net.lifesteal.stealaspawner.gui;

import net.lifesteal.stealaspawner.data.SpawnerData;
import net.lifesteal.stealaspawner.data.SpawnerManager;
import net.lifesteal.stealaspawner.util.EconomyHook;
import net.lifesteal.stealaspawner.util.MsgUtil;
import net.lifesteal.stealaspawner.util.SasConfig;
import net.lifesteal.stealaspawner.util.SpawnerItemFactory;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class GuiListener implements Listener {

    private final SpawnerManager manager;
    private final SasConfig config;
    private final EconomyHook economy;

    public GuiListener(SpawnerManager manager, SasConfig config, EconomyHook economy) {
        this.manager = manager;
        this.config = config;
        this.economy = economy;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof ShopHolder || holder instanceof SpawnerPanelHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();

        if (!(holder instanceof ShopHolder) && !(holder instanceof SpawnerPanelHolder)) {
            return;
        }
        // Cancel unconditionally first - nothing should ever leave or enter
        // these inventories via drag/shift-click, only our own logic below
        // is allowed to hand out items.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            // Click landed in the player's own inventory while a GUI is open - ignore.
            return;
        }

        if (holder instanceof ShopHolder shop) {
            handleShopClick(player, shop, event.getSlot());
        } else {
            handlePanelClick(player, (SpawnerPanelHolder) holder, top, event.getSlot(), event.isShiftClick());
        }
    }

    private void handleShopClick(Player player, ShopHolder shop, int slot) {
        EntityType type = shop.typeAt(slot);
        if (type == null) {
            return;
        }
        Double price = config.shopPrices.get(type);
        if (price == null) {
            return;
        }

        if (!economy.isEnabled()) {
            MsgUtil.send(player, config, "no-economy");
            return;
        }
        if (!economy.withdraw(player, price)) {
            MsgUtil.send(player, config, "insufficient-funds", Map.of(
                    "cost", economy.format(price), "balance", economy.format(economy.getBalance(player))));
            return;
        }

        ItemStack item = SpawnerItemFactory.createFresh(type);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        leftover.values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
    }

    private void handlePanelClick(Player player, SpawnerPanelHolder holder, Inventory top, int slot, boolean shiftClick) {
        SpawnerData data = holder.data();

        if (!manager.isOwnerOrTrusted(data, player.getUniqueId())) {
            player.closeInventory();
            MsgUtil.send(player, config, "not-your-spawner");
            return;
        }
        if (!player.hasPermission("stealaspawner.upgrade")) {
            MsgUtil.send(player, config, "no-permission");
            return;
        }

        SpawnerData.Track track;
        if (slot == SpawnerPanelGUI.SLOT_SPEED) {
            track = SpawnerData.Track.SPEED;
        } else if (slot == SpawnerPanelGUI.SLOT_YIELD) {
            track = SpawnerData.Track.YIELD;
        } else if (slot == SpawnerPanelGUI.SLOT_CAPACITY) {
            track = SpawnerData.Track.CAPACITY;
        } else {
            return;
        }

        int amount = shiftClick ? 10 : 1;
        double cost = manager.upgradeCost(data, track, amount);
        if (Double.isInfinite(cost)) {
            MsgUtil.send(player, config, "max-level-reached", Map.of("max", String.valueOf(config.maxLevel)));
            return;
        }
        if (!economy.isEnabled()) {
            MsgUtil.send(player, config, "no-economy");
            return;
        }
        if (!economy.withdraw(player, cost)) {
            MsgUtil.send(player, config, "insufficient-funds", Map.of(
                    "cost", economy.format(cost), "balance", economy.format(economy.getBalance(player))));
            return;
        }

        manager.applyUpgrade(data, track, amount);
        SpawnerPanelGUI.refresh(top, data, config, economy, manager);

        MsgUtil.send(player, config, "upgrade-success", Map.of(
                "track", track.name(), "level", String.valueOf(data.level(track)), "cost", economy.format(cost)));
    }
}
