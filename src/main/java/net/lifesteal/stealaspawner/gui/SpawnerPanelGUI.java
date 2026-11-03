package net.lifesteal.stealaspawner.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.lifesteal.stealaspawner.data.SpawnerData;
import net.lifesteal.stealaspawner.data.SpawnerManager;
import net.lifesteal.stealaspawner.util.EconomyHook;
import net.lifesteal.stealaspawner.util.LevelFormulas;
import net.lifesteal.stealaspawner.util.SasConfig;
import net.lifesteal.stealaspawner.util.SpawnerItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class SpawnerPanelGUI {

    public static final int SLOT_SPEED = 2;
    public static final int SLOT_YIELD = 4;
    public static final int SLOT_CAPACITY = 6;

    private SpawnerPanelGUI() {
    }

    public static void open(Player player, SpawnerData data, SasConfig config, EconomyHook economy, SpawnerManager manager) {
        SpawnerPanelHolder holder = new SpawnerPanelHolder(data);
        Inventory inv = Bukkit.createInventory(holder, 9,
                Component.text("Spawner: " + SpawnerItemFactory.prettyName(data.entityType()),
                        NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
        holder.setInventory(inv);
        refresh(inv, data, config, economy, manager);
        player.openInventory(inv);
    }

    /** Redraws all 3 track icons in place - call after any upgrade so the panel reflects new levels live. */
    public static void refresh(Inventory inv, SpawnerData data, SasConfig config, EconomyHook economy, SpawnerManager manager) {
        inv.setItem(SLOT_SPEED, buildIcon(SpawnerData.Track.SPEED, data, config, economy, manager));
        inv.setItem(SLOT_YIELD, buildIcon(SpawnerData.Track.YIELD, data, config, economy, manager));
        inv.setItem(SLOT_CAPACITY, buildIcon(SpawnerData.Track.CAPACITY, data, config, economy, manager));
    }

    private static ItemStack buildIcon(SpawnerData.Track track, SpawnerData data, SasConfig config,
                                        EconomyHook economy, SpawnerManager manager) {
        Material material = switch (track) {
            case SPEED -> Material.SUGAR;
            case YIELD -> Material.EMERALD;
            case CAPACITY -> Material.HOPPER;
        };
        String trackName = switch (track) {
            case SPEED -> "Speed";
            case YIELD -> "Yield";
            case CAPACITY -> "Capacity";
        };
        SasConfig.Track trackCfg = switch (track) {
            case SPEED -> config.speed;
            case YIELD -> config.yield;
            case CAPACITY -> config.capacity;
        };

        int nominal = data.level(track);
        int effective = LevelFormulas.effectiveLevel(nominal, trackCfg.effectCap);

        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text(trackName, NamedTextColor.YELLOW, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(labelled("Level: ", nominal + "/" + config.maxLevel));
        lore.add(labelled("Hiệu ứng: ", effective + "/" + trackCfg.effectCap));
        lore.add(labelled("Hiện tại: ", effectDescription(track, effective, data, config), NamedTextColor.AQUA));

        if (nominal >= config.maxLevel) {
            lore.add(plain("Đã đạt level tối đa!", NamedTextColor.GOLD));
        } else {
            double cost = manager.upgradeCost(data, track, 1);
            lore.add(labelled("Nâng cấp tiếp: ", economy.format(cost), NamedTextColor.GREEN, NamedTextColor.WHITE));
            lore.add(plain("Click: +1 level | Shift-click: +10 level", NamedTextColor.DARK_GRAY));
            if (nominal >= trackCfg.effectCap) {
                lore.add(plain("Hiệu ứng đã đạt mức tối đa, cấp độ thêm chỉ mang tính xếp hạng.", NamedTextColor.YELLOW));
            }
        }

        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private static Component labelled(String label, String value) {
        return labelled(label, value, NamedTextColor.GRAY, NamedTextColor.WHITE);
    }

    private static Component labelled(String label, String value, NamedTextColor valueColor) {
        return labelled(label, value, NamedTextColor.GRAY, valueColor);
    }

    private static Component labelled(String label, String value, NamedTextColor labelColor, NamedTextColor valueColor) {
        return Component.text(label, labelColor)
                .append(Component.text(value, valueColor))
                .decoration(TextDecoration.ITALIC, false);
    }

    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static String effectDescription(SpawnerData.Track track, int effectiveLevel, SpawnerData data, SasConfig config) {
        return switch (track) {
            case SPEED -> {
                int delay = LevelFormulas.speedDelayTicks(effectiveLevel, config.speed.baseDelayTicks,
                        config.speed.decayFactor, config.speed.minDelayTicks);
                yield String.format("Chu kỳ sinh: %.1fs", delay / 20.0);
            }
            case YIELD -> {
                double base = config.baseIncome.getOrDefault(data.entityType(), 0.0);
                double income = LevelFormulas.yieldIncome(effectiveLevel, base, config.yield.incomePerLevel);
                yield String.format("Thu nhập: %.1f/chu kỳ", income);
            }
            case CAPACITY -> {
                int cap = LevelFormulas.capacityMaxNearby(effectiveLevel, config.capacity.baseCap, config.capacity.hardCapServer);
                yield "Tối đa quái gần: " + cap;
            }
        };
    }
}
