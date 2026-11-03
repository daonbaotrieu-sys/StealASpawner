package net.lifesteal.stealaspawner.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.lifesteal.stealaspawner.data.SpawnerData;
import net.lifesteal.stealaspawner.data.SpawnerManager;
import net.lifesteal.stealaspawner.util.EconomyHook;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kept in its own class, only instantiated/registered from the main plugin
 * class when PlaceholderAPI is actually enabled - same lazy-classload
 * reasoning as {@link net.lifesteal.stealaspawner.util.WorldGuardSupport}.
 */
public final class SasPlaceholderExpansion extends PlaceholderExpansion {

    private final SpawnerManager manager;
    private final EconomyHook economy;

    public SasPlaceholderExpansion(SpawnerManager manager, EconomyHook economy) {
        this.manager = manager;
        this.economy = economy;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "sas";
    }

    @Override
    public @NotNull String getAuthor() {
        return "StealASpawner";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        return switch (params.toLowerCase()) {
            case "balance" -> economy.format(economy.getBalance(player));
            case "rank" -> rankOf(player.getUniqueId());
            default -> null;
        };
    }

    private String rankOf(UUID player) {
        Map<UUID, Double> totals = new LinkedHashMap<>();
        for (SpawnerData data : manager.all()) {
            totals.merge(data.owner(), data.incomeTotal(), Double::sum);
        }
        var sorted = totals.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .toList();

        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getKey().equals(player)) {
                return String.valueOf(i + 1);
            }
        }
        return "-";
    }
}
