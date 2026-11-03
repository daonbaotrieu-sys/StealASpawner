package net.lifesteal.stealaspawner.commands;

import net.lifesteal.stealaspawner.gui.ShopGUI;
import net.lifesteal.stealaspawner.util.EconomyHook;
import net.lifesteal.stealaspawner.util.MsgUtil;
import net.lifesteal.stealaspawner.util.SasConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SpawnerShopCommand implements CommandExecutor {

    private final SasConfig config;
    private final EconomyHook economy;

    public SpawnerShopCommand(SasConfig config, EconomyHook economy) {
        this.config = config;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Chỉ người chơi mới dùng được lệnh này.");
            return true;
        }
        if (!economy.isEnabled()) {
            MsgUtil.send(player, config, "no-economy");
            return true;
        }
        ShopGUI.open(player, config, economy);
        return true;
    }
}
