package net.lifesteal.stealaspawner.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Builds the Material.SPAWNER ItemStack StealASpawner hands out, and reads
 * one back out of a player's hand. Every such item is tagged with
 * {@link Keys#PLUGIN_TAG} so place/steal listeners can recognise it versus
 * a vanilla spawner silk-touched by some other plugin.
 */
public final class SpawnerItemFactory {

    private SpawnerItemFactory() {
    }

    public static ItemStack create(EntityType type, int levelSpeed, int levelYield, int levelCapacity) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Spawner: " + prettyName(type), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(List.of(
                Component.text("Loại: ", NamedTextColor.GRAY)
                        .append(Component.text(prettyName(type), NamedTextColor.WHITE))
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Speed " + levelSpeed + " | Yield " + levelYield + " | Capacity " + levelCapacity,
                        NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        ));

        var pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.PLUGIN_TAG, PersistentDataType.BYTE, (byte) 1);
        pdc.set(Keys.ENTITY_TYPE, PersistentDataType.STRING, type.name());
        pdc.set(Keys.LEVEL_SPEED, PersistentDataType.INTEGER, levelSpeed);
        pdc.set(Keys.LEVEL_YIELD, PersistentDataType.INTEGER, levelYield);
        pdc.set(Keys.LEVEL_CAPACITY, PersistentDataType.INTEGER, levelCapacity);

        item.setItemMeta(meta);
        return item;
    }

    /** Freshly minted spawner (mob drop, shop purchase) always starts at level 1 on every track. */
    public static ItemStack createFresh(EntityType type) {
        return create(type, 1, 1, 1);
    }

    public static boolean isSasSpawner(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) {
            return false;
        }
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(Keys.PLUGIN_TAG, PersistentDataType.BYTE);
    }

    public static EntityType readEntityType(ItemStack item) {
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String name = pdc.get(Keys.ENTITY_TYPE, PersistentDataType.STRING);
        return name == null ? EntityType.PIG : EntityType.valueOf(name);
    }

    public static int readLevel(ItemStack item, net.lifesteal.stealaspawner.data.SpawnerData.Track track) {
        var pdc = item.getItemMeta().getPersistentDataContainer();
        var key = switch (track) {
            case SPEED -> Keys.LEVEL_SPEED;
            case YIELD -> Keys.LEVEL_YIELD;
            case CAPACITY -> Keys.LEVEL_CAPACITY;
        };
        Integer v = pdc.get(key, PersistentDataType.INTEGER);
        return v == null ? 1 : v;
    }

    public static String prettyName(EntityType type) {
        String[] parts = type.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
