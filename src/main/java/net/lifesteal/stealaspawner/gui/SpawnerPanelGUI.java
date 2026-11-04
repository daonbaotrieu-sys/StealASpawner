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

/**
 * 27-slot panel with a glass-pane border, a summary item, and the 3 track
 * icons in the middle row. Each icon shows a text progress bar toward its
 * effect-cap rather than a bare "12/60" number, so the player can read
 * progress at a glance instead of doing the division themselves.
 */
public final class SpawnerPanelGUI {

    private static final int SIZE = 27;
    public static final int SLOT_INFO = 10;
    public static final int SLOT_SPEED = 12;
    public static final int SLOT_YIELD = 13;
    public static final int SLOT_CAPACITY = 14;
    private static final int SLOT_OWNER_HEAD = 16;

    private static final int BAR_SEGMENTS = 10;

    private SpawnerPanelGUI() {
    }

    public static void open(Player player, SpawnerData data, SasConfig config, EconomyHook economy, SpawnerManager manager) {
        SpawnerPanelHolder holder = new SpawnerPanelHolder(data);
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                Component.text("✦ Spawner: " + SpawnerItemFactory.prettyName(data.entityType()) + " ✦",
                        NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
        holder.setInventory(inv);
        buildBorder(inv);
        refresh(inv, data, config, economy, manager);
        player.openInventory(inv);
    }

    /** Redraws the live parts of the panel in place - call after any upgrade. */
    public static void refresh(Inventory inv, SpawnerData data, SasConfig config, EconomyHook economy, SpawnerManager manager) {
        inv.setItem(SLOT_INFO, buildInfoItem(data, economy));
        inv.setItem(SLOT_SPEED, buildTrackIcon(SpawnerData.Track.SPEED, data, config, economy, manager));
        inv.setItem(SLOT_YIELD, buildTrackIcon(SpawnerData.Track.YIELD, data, config, economy, manager));
        inv.setItem(SLOT_CAPACITY, buildTrackIcon(SpawnerData.Track.CAPACITY, data, config, economy, manager));
    }

    private static void buildBorder(Inventory inv) {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.empty());
        filler.setItemMeta(meta);

        for (int slot = 0; slot < SIZE; slot++) {
            boolean topOrBottomRow = slot < 9 || slot >= 18;
            boolean leftOrRightColumn = slot % 9 == 0 || slot % 9 == 8;
            if (topOrBottomRow || leftOrRightColumn) {
                inv.setItem(slot, filler);
            }
        }
    }

    private static ItemStack buildInfoItem(SpawnerData data, EconomyHook economy) {
        Material material = spawnEggFor(data.entityType());
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();

        meta.displayName(Component.text(SpawnerItemFactory.prettyName(data.entityType()) + " Spawner",
                NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                labelled("Thu nhập tích luỹ: ", economy.format(data.incomeTotal()), NamedTextColor.GREEN),
                plain("", NamedTextColor.GRAY),
                plain("Click vào 3 ô bên dưới để nâng cấp:", NamedTextColor.GRAY),
                plain("Click trái: +1  |  Click phải: +10  |  Shift: +64", NamedTextColor.DARK_GRAY)
        ));
        icon.setItemMeta(meta);
        return icon;
    }

    private static Material spawnEggFor(org.bukkit.entity.EntityType type) {
        try {
            return Material.valueOf(type.name() + "_SPAWN_EGG");
        } catch (IllegalArgumentException ex) {
            return Material.SPAWNER;
        }
    }

    private static ItemStack buildTrackIcon(SpawnerData.Track track, SpawnerData data, SasConfig config,
                                             EconomyHook economy, SpawnerManager manager) {
        Material material = switch (track) {
            case SPEED -> Material.CLOCK;
            case YIELD -> Material.GOLD_INGOT;
            case CAPACITY -> Material.HOPPER;
        };
        NamedTextColor accent = switch (track) {
            case SPEED -> NamedTextColor.AQUA;
            case YIELD -> NamedTextColor.GOLD;
            case CAPACITY -> NamedTextColor.LIGHT_PURPLE;
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
        boolean maxedEffect = nominal >= trackCfg.effectCap;
        boolean maxedNominal = nominal >= config.maxLevel;

        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text((maxedNominal ? "★ " : "") + trackName, accent, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(progressBar(effective, trackCfg.effectCap, accent));
        lore.add(labelled("Level: ", nominal + "/" + config.maxLevel));
        lore.add(labelled("Hiện tại: ", effectDescription(track, effective, data, config), NamedTextColor.WHITE));
        lore.add(plain("", NamedTextColor.GRAY));

        if (maxedNominal) {
            lore.add(plain("Đã đạt level tối đa!", NamedTextColor.GOLD));
        } else {
            double costNext = manager.upgradeCost(data, track, 1);
            lore.add(labelled("Giá +1 level: ", economy.format(costNext), NamedTextColor.GREEN));
            if (maxedEffect) {
                lore.add(plain("Hiệu ứng đã max - level thêm chỉ để xếp hạng.", NamedTextColor.YELLOW));
            }
            lore.add(plain("", NamedTextColor.GRAY));
            lore.add(plain("▸ Click trái: +1", NamedTextColor.DARK_GRAY));
            lore.add(plain("▸ Click phải: +10", NamedTextColor.DARK_GRAY));
            lore.add(plain("▸ Shift-click: +64", NamedTextColor.DARK_GRAY));
        }

        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    /** Renders a 10-segment bar like [■■■■■■░░░░] so progress reads at a glance. */
    private static Component progressBar(int effective, int effectCap, NamedTextColor filledColor) {
        int filledSegments = effectCap <= 0 ? 0
                : Math.min(BAR_SEGMENTS, (int) Math.round((double) effective / effectCap * BAR_SEGMENTS));

        Component bar = Component.text("[", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false);
        for (int i = 0; i < BAR_SEGMENTS; i++) {
            bar = bar.append(Component.text("■", i < filledSegments ? filledColor : NamedTextColor.DARK_GRAY));
        }
        bar = bar.append(Component.text("] " + effective + "/" + effectCap, NamedTextColor.GRAY));
        return bar;
    }

    private static Component labelled(String label, String value) {
        return labelled(label, value, NamedTextColor.WHITE);
    }

    private static Component labelled(String label, String value, NamedTextColor valueColor) {
        return Component.text(label, NamedTextColor.GRAY)
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
