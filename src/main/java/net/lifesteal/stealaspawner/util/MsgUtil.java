package net.lifesteal.stealaspawner.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Every player-facing string in StealASpawner goes through here so the
 * whole plugin speaks MiniMessage instead of the legacy ChatColor codes.
 */
public final class MsgUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private MsgUtil() {
    }

    public static Component parse(String raw) {
        return MM.deserialize(raw == null ? "" : raw);
    }

    public static Component parse(String raw, Map<String, String> placeholders) {
        String resolved = raw == null ? "" : raw;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            resolved = resolved.replace("<" + e.getKey() + ">", e.getValue());
        }
        return MM.deserialize(resolved);
    }

    public static void send(CommandSender to, SasConfig cfg, String key) {
        to.sendMessage(parse(cfg.msg("prefix") + cfg.msg(key)));
    }

    public static void send(CommandSender to, SasConfig cfg, String key, Map<String, String> placeholders) {
        to.sendMessage(parse(cfg.msg("prefix") + cfg.msg(key), placeholders));
    }

    public static void actionBar(Player to, SasConfig cfg, String key) {
        to.sendActionBar(parse(cfg.msg(key)));
    }

    public static void actionBar(Player to, SasConfig cfg, String key, Map<String, String> placeholders) {
        to.sendActionBar(parse(cfg.msg(key), placeholders));
    }
}
