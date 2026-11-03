package net.lifesteal.stealaspawner;

import net.lifesteal.stealaspawner.commands.SasAdminCommand;
import net.lifesteal.stealaspawner.commands.SpawnerCommand;
import net.lifesteal.stealaspawner.commands.SpawnerShopCommand;
import net.lifesteal.stealaspawner.data.Database;
import net.lifesteal.stealaspawner.data.SpawnerData;
import net.lifesteal.stealaspawner.data.SpawnerManager;
import net.lifesteal.stealaspawner.gui.GuiListener;
import net.lifesteal.stealaspawner.listeners.ChunkLoadListener;
import net.lifesteal.stealaspawner.listeners.MobDeathListener;
import net.lifesteal.stealaspawner.listeners.SpawnerBreakListener;
import net.lifesteal.stealaspawner.listeners.SpawnerInteractListener;
import net.lifesteal.stealaspawner.listeners.SpawnerPlaceListener;
import net.lifesteal.stealaspawner.placeholder.SasPlaceholderExpansion;
import net.lifesteal.stealaspawner.util.EconomyHook;
import net.lifesteal.stealaspawner.util.Keys;
import net.lifesteal.stealaspawner.util.RegionHook;
import net.lifesteal.stealaspawner.util.SasConfig;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class StealASpawnerPlugin extends JavaPlugin {

    private SasConfig config;
    private Database database;
    private SpawnerManager manager;
    private EconomyHook economyHook;
    private RegionHook regionHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Keys.init(this);

        config = new SasConfig(this);

        database = new Database(this);
        database.connect(config.dbFile);

        manager = new SpawnerManager(this, database, config);
        // Blocking by design: the spec calls for the full spawner cache to
        // be warm before any player can interact with a spawner, and a
        // local SQLite read of a few thousand rows is on the order of
        // milliseconds - cheaper than the startup cost most plugins pay
        // elsewhere.
        manager.loadFromDatabase();

        economyHook = new EconomyHook(this);
        regionHook = new RegionHook(this);
        regionHook.setup(); // WorldGuard/GriefPrevention are direct softdepends, safe to resolve now.

        registerListeners();
        registerCommands();

        // Vault's actual Economy provider usually comes from a third plugin
        // (EssentialsX, etc.) that we don't softdepend on directly, so its
        // load order relative to us isn't guaranteed. ServerLoadEvent fires
        // only after every plugin has finished enabling, which is the one
        // safe point to resolve it and to register the PlaceholderAPI
        // expansion.
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onServerLoad(ServerLoadEvent event) {
                setupLateIntegrations();
            }
        }, this);

        startIncomeTask();
        startAutosaveTask();

        getLogger().info("StealASpawner enabled, tracking " + manager.all().size() + " spawners.");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.saveAll();
        }
        if (database != null) {
            database.close();
        }
    }

    private void setupLateIntegrations() {
        if (economyHook.setup()) {
            getLogger().info("Hooked into Vault economy.");
        } else {
            getLogger().warning("No Vault economy provider found - income, shop and upgrades are disabled until one is installed.");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new SasPlaceholderExpansion(manager, economyHook).register();
            getLogger().info("Registered PlaceholderAPI expansion (%sas_balance%, %sas_rank%).");
        }
    }

    private void registerListeners() {
        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new MobDeathListener(config), this);
        pm.registerEvents(new SpawnerPlaceListener(manager, regionHook, config), this);
        pm.registerEvents(new SpawnerBreakListener(manager, regionHook, config), this);
        pm.registerEvents(new ChunkLoadListener(manager), this);
        pm.registerEvents(new SpawnerInteractListener(manager, config, economyHook), this);
        pm.registerEvents(new GuiListener(manager, config, economyHook), this);
    }

    private void registerCommands() {
        getCommand("spawnershop").setExecutor(new SpawnerShopCommand(config, economyHook));
        SpawnerCommand spawnerCommand = new SpawnerCommand(manager, config, economyHook);
        getCommand("spawner").setExecutor(spawnerCommand);
        getCommand("spawner").setTabCompleter(spawnerCommand);
        getCommand("sas").setExecutor(new SasAdminCommand(config));
    }

    /** Vault deposits run on the main thread deliberately - most economy plugins aren't thread-safe. */
    private void startIncomeTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!economyHook.isEnabled()) return;
            for (SpawnerData data : manager.all()) {
                double income = manager.currentYieldIncome(data);
                OfflinePlayer owner = Bukkit.getOfflinePlayer(data.owner());
                if (economyHook.deposit(owner, income)) {
                    data.addIncome(income);
                }
            }
        }, config.incomeIntervalTicks, config.incomeIntervalTicks);
    }

    private void startAutosaveTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> manager.saveAll(),
                config.autosaveIntervalTicks, config.autosaveIntervalTicks);
    }
}
