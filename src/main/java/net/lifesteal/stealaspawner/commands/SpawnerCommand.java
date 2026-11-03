package net.lifesteal.stealaspawner.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.lifesteal.stealaspawner.data.SpawnerData;
import net.lifesteal.stealaspawner.data.SpawnerManager;
import net.lifesteal.stealaspawner.util.EconomyHook;
import net.lifesteal.stealaspawner.util.MsgUtil;
import net.lifesteal.stealaspawner.util.SasConfig;
import net.lifesteal.stealaspawner.util.SpawnerItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class SpawnerCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("info", "upgrade", "top", "trust");
    private static final List<String> TRACKS = List.of("speed", "yield", "capacity");
    private static final double LOOK_DISTANCE = 6.0;

    private final SpawnerManager manager;
    private final SasConfig config;
    private final EconomyHook economy;

    public SpawnerCommand(SpawnerManager manager, SasConfig config, EconomyHook economy) {
        this.manager = manager;
        this.config = config;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("/spawner <info|upgrade|top|trust>", NamedTextColor.RED));
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "info" -> info(sender);
            case "upgrade" -> upgrade(sender, args);
            case "top" -> top(sender);
            case "trust" -> trust(sender, args);
            default -> {
                sender.sendMessage(Component.text("/spawner <info|upgrade|top|trust>", NamedTextColor.RED));
                yield true;
            }
        };
    }

    private boolean info(CommandSender sender) {
        if (!(sender instanceof Player player)) return true;
        SpawnerData data = lookingAt(player);
        if (data == null) {
            MsgUtil.send(player, config, "not-looking-at-spawner");
            return true;
        }

        OfflinePlayer owner = Bukkit.getOfflinePlayer(data.owner());
        player.sendMessage(Component.text("--- Spawner: " + SpawnerItemFactory.prettyName(data.entityType()) + " ---", NamedTextColor.GOLD));
        player.sendMessage(info("Chủ sở hữu", owner.getName() != null ? owner.getName() : data.owner().toString()));
        player.sendMessage(info("Speed", data.levelSpeed() + "/" + config.maxLevel
                + " (effective " + Math.min(data.levelSpeed(), config.speed.effectCap) + "/" + config.speed.effectCap + ")"));
        player.sendMessage(info("Yield", data.levelYield() + "/" + config.maxLevel
                + " (effective " + Math.min(data.levelYield(), config.yield.effectCap) + "/" + config.yield.effectCap + ")"));
        player.sendMessage(info("Capacity", data.levelCapacity() + "/" + config.maxLevel
                + " (effective " + Math.min(data.levelCapacity(), config.capacity.effectCap) + "/" + config.capacity.effectCap + ")"));
        player.sendMessage(info("Thu nhập tích luỹ", economy.format(data.incomeTotal())));
        return true;
    }

    private Component info(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE));
    }

    private boolean upgrade(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("stealaspawner.upgrade")) {
            MsgUtil.send(player, config, "no-permission");
            return true;
        }
        if (args.length < 2 || !TRACKS.contains(args[1].toLowerCase())) {
            player.sendMessage(Component.text("/spawner upgrade <speed|yield|capacity> [amount]", NamedTextColor.RED));
            return true;
        }

        SpawnerData data = lookingAt(player);
        if (data == null) {
            MsgUtil.send(player, config, "not-looking-at-spawner");
            return true;
        }
        if (!manager.isOwnerOrTrusted(data, player.getUniqueId())) {
            MsgUtil.send(player, config, "not-your-spawner");
            return true;
        }

        SpawnerData.Track track = switch (args[1].toLowerCase()) {
            case "speed" -> SpawnerData.Track.SPEED;
            case "yield" -> SpawnerData.Track.YIELD;
            default -> SpawnerData.Track.CAPACITY;
        };

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException ignored) {
                // fall back to 1
            }
        }

        double cost = manager.upgradeCost(data, track, amount);
        if (Double.isInfinite(cost)) {
            MsgUtil.send(player, config, "max-level-reached", Map.of("max", String.valueOf(config.maxLevel)));
            return true;
        }
        if (!economy.isEnabled()) {
            MsgUtil.send(player, config, "no-economy");
            return true;
        }
        if (!economy.withdraw(player, cost)) {
            MsgUtil.send(player, config, "insufficient-funds", Map.of(
                    "cost", economy.format(cost), "balance", economy.format(economy.getBalance(player))));
            return true;
        }

        manager.applyUpgrade(data, track, amount);
        MsgUtil.send(player, config, "upgrade-success", Map.of(
                "track", track.name(), "level", String.valueOf(data.level(track)), "cost", economy.format(cost)));
        return true;
    }

    private boolean top(CommandSender sender) {
        List<SpawnerData> byIncome = new ArrayList<>(manager.all());
        byIncome.sort(Comparator.comparingDouble(SpawnerData::incomeTotal).reversed());

        sender.sendMessage(Component.text("--- Top Spawner (thu nhập) ---", NamedTextColor.GOLD));
        int rank = 1;
        for (SpawnerData data : byIncome.subList(0, Math.min(10, byIncome.size()))) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(data.owner());
            String name = owner.getName() != null ? owner.getName() : data.owner().toString();
            sender.sendMessage(Component.text("#" + rank + " ", NamedTextColor.YELLOW)
                    .append(Component.text(name, NamedTextColor.WHITE))
                    .append(Component.text(" - " + economy.format(data.incomeTotal()), NamedTextColor.GREEN)));
            rank++;
        }

        List<SpawnerData> byMaxLevel = new ArrayList<>(manager.all());
        byMaxLevel.sort(Comparator.comparingInt((SpawnerData d) ->
                Math.max(d.levelSpeed(), Math.max(d.levelYield(), d.levelCapacity()))).reversed());

        sender.sendMessage(Component.text("--- Top Spawner (level cao nhất, prestige) ---", NamedTextColor.GOLD));
        rank = 1;
        for (SpawnerData data : byMaxLevel.subList(0, Math.min(10, byMaxLevel.size()))) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(data.owner());
            String name = owner.getName() != null ? owner.getName() : data.owner().toString();
            int best = Math.max(data.levelSpeed(), Math.max(data.levelYield(), data.levelCapacity()));
            sender.sendMessage(Component.text("#" + rank + " ", NamedTextColor.YELLOW)
                    .append(Component.text(name, NamedTextColor.WHITE))
                    .append(Component.text(" - level " + best, NamedTextColor.AQUA)));
            rank++;
        }
        return true;
    }

    private boolean trust(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length < 2) {
            player.sendMessage(Component.text("/spawner trust <player>", NamedTextColor.RED));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            return true;
        }
        manager.addTrust(player.getUniqueId(), target.getUniqueId());
        MsgUtil.send(player, config, "trust-added", Map.of("player", args[1]));
        return true;
    }

    private SpawnerData lookingAt(Player player) {
        Block target = player.getTargetBlockExact((int) LOOK_DISTANCE, FluidCollisionMode.NEVER);
        if (target == null) return null;
        return manager.get(target);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("upgrade")) {
            return filter(TRACKS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("trust")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String s : options) {
            if (s.toLowerCase().startsWith(prefix.toLowerCase())) out.add(s);
        }
        return out;
    }
}
