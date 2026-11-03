package net.lifesteal.stealaspawner.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.lifesteal.stealaspawner.util.EconomyHook;
import net.lifesteal.stealaspawner.util.SasConfig;
import net.lifesteal.stealaspawner.util.SpawnerItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ShopGUI {

    private ShopGUI() {
    }

    public static void open(Player player, SasConfig config, EconomyHook economy) {
        Map<Integer, EntityType> slotToType = new HashMap<>();
        ShopHolder holder = new ShopHolder(slotToType);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Spawner Shop", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        holder.setInventory(inv);

        // Cheapest first, deterministic ordering across opens.
        var sorted = new TreeMap<Double, EntityType>();
        for (var entry : config.shopPrices.entrySet()) {
            sorted.put(entry.getValue() + entry.getKey().ordinal() * 1e-6, entry.getKey());
        }

        int slot = 0;
        for (var entry : sorted.entrySet()) {
            if (slot >= inv.getSize()) break;
            EntityType type = entry.getValue();
            double price = config.shopPrices.get(type);

            ItemStack icon = buildIcon(type, price, economy);
            inv.setItem(slot, icon);
            slotToType.put(slot, type);
            slot++;
        }

        player.openInventory(inv);
    }

    private static ItemStack buildIcon(EntityType type, double price, EconomyHook economy) {
        Material material = spawnEggFor(type);
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();

        meta.displayName(Component.text(SpawnerItemFactory.prettyName(type) + " Spawner", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Giá: ", NamedTextColor.GRAY)
                        .append(Component.text(economy.format(price), NamedTextColor.GOLD))
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Click để mua - level 1 mọi track.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        icon.setItemMeta(meta);
        return icon;
    }

    private static Material spawnEggFor(EntityType type) {
        try {
            return Material.valueOf(type.name() + "_SPAWN_EGG");
        } catch (IllegalArgumentException ex) {
            return Material.SPAWNER;
        }
    }
}
