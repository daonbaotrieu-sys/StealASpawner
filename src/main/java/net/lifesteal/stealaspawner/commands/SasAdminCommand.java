package net.lifesteal.stealaspawner.commands;

import net.lifesteal.stealaspawner.util.MsgUtil;
import net.lifesteal.stealaspawner.util.SasConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class SasAdminCommand implements CommandExecutor {

    private final SasConfig config;

    public SasAdminCommand(SasConfig config) {
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("stealaspawner.admin")) {
            MsgUtil.send(sender, config, "no-permission");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("/sas reload");
            return true;
        }
        config.reload();
        MsgUtil.send(sender, config, "reload-done");
        return true;
    }
}
