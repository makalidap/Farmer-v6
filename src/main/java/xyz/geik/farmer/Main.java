package xyz.geik.farmer;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.geik.farmer.api.FarmerAPI;
import xyz.geik.farmer.api.managers.FarmerManager;
import xyz.geik.farmer.configuration.ConfigFile;
import xyz.geik.farmer.configuration.LangFile;
import xyz.geik.farmer.database.MySQL;
import xyz.geik.farmer.database.SQL;
import xyz.geik.farmer.database.SQLite;
import xyz.geik.farmer.helpers.CacheLoader;
import xyz.geik.farmer.integrations.Integrations;
import xyz.geik.farmer.integrations.placeholderapi.PlaceholderAPI;
import xyz.geik.farmer.listeners.ListenerRegister;
import xyz.geik.farmer.modules.FarmerModule;
import xyz.geik.farmer.modules.autoharvest.AutoHarvest;
import xyz.geik.farmer.modules.autoseller.AutoSeller;
import xyz.geik.farmer.modules.production.Production;
import xyz.geik.farmer.modules.spawnerkiller.SpawnerKiller;
import xyz.geik.farmer.modules.voucher.Voucher;
import xyz.geik.farmer.shades.storage.Config;
import xyz.geik.glib.GLib;
import xyz.geik.glib.chat.ChatUtils;
import xyz.geik.glib.database.Database;
import xyz.geik.glib.database.DatabaseType;
import xyz.geik.glib.economy.Economy;
import xyz.geik.glib.economy.EconomyAPI;
import xyz.geik.glib.shades.okaeri.configs.ConfigManager;
import xyz.geik.glib.shades.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import xyz.geik.glib.simplixstorage.SimplixStorageAPI;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Main class of farmer
 * There is only loads, apis and
 * startup task codes.
 */
@Getter
public class Main extends JavaPlugin {

    /**
     * Listener list of modules
     */
    public Map<FarmerModule, Listener> listenerList = new HashMap<>();

    @Getter @Setter
    private SimplixStorageAPI simplixStorageAPI;

    @Getter @Setter
    private static Database database;

    @Getter @Setter
    private static SQL sql;

    @Getter @Setter
    private PlaceholderAPI placeholderAPI;

    @Getter @Setter
    private static Economy economy;

    /**
     * Instance of this class
     */
    @Getter
    private static Main instance;

    /**
     * Config files which using SimplixStorage API for it.
     * Also, you can find usage code of API on helpers#StorageAPI
     */
    @Getter
    private static Config itemsFile, levelFile;

    /**
     * Lang file of plugin
     */
    @Getter
    private static LangFile langFile;

    /**
     * Config file of plugin
     */
    @Getter
    private static ConfigFile configFile;

    /**
     * Main integration of plugin integrations#Integrations
     */
    @Getter
    @Setter
    private static Integrations integration;


    /**
     * Loading files before enable
     */
    public void onLoad() {
        instance = this;
        simplixStorageAPI = new SimplixStorageAPI(this);
        setupFiles();
    }

    /**
     * onEnable method calls from spigot api.
     * This is sort of the main(String... args) method.
     */
    public void onEnable() {
        new GLib(this, getLangFile().getMessages().getPrefix());
        setupDatabase();
        registerEconomy();
        // API Installer
        FarmerAPI.getFarmerManager();
        FarmerAPI.getModuleManager();
        CacheLoader.loadAllItems();
        CacheLoader.loadAllLevels();
        getCommand("farmer").setExecutor(new Commands());
        getCommand("farmer").setTabCompleter(new FarmerTabComplete());
        Integrations.registerIntegrations();
        sendEnableMessage();
        getSql().loadAllFarmers();
        new ListenerRegister();
        loadMetrics();
        registerModules();
    }

    /**
     * disable method calls from spigot api.
     * executing it right before close.
     * async tasks can be fail because server
     * can't handle async tasks while shutting down
     */
    public void onDisable() {
        getSql().updateAllFarmers();
    }

    /**
     * Setups config, lang and modules file file
     */
    public void setupFiles() {
        try {
            this.configFile = ConfigManager.create(ConfigFile.class, (it) -> {
                it.withConfigurer(new YamlBukkitConfigurer());
                it.withBindFile(new File(getDataFolder(), "config.yml"));
                it.saveDefaults();
                it.load(true);
            });

            String langName = configFile.getSettings().getLang();
            //Class langClass = Class.forName("xyz.geik.timer.configuration.lang." + langName);
            //Class<Language> languageClass = langClass;
            this.langFile = ConfigManager.create(LangFile.class, (it) -> {
                it.withConfigurer(new YamlBukkitConfigurer());
                it.withBindFile(new File(getDataFolder() + "/lang", langName + ".yml"));
                it.saveDefaults();
                it.load(true);
            });
            itemsFile = getSimplixStorageAPI().initConfig("items");
            levelFile = getSimplixStorageAPI().initConfig("levels");
        } catch (Exception exception) {
            getPluginLoader().disablePlugin(this);
            throw new RuntimeException("Error loading configuration file");
        }
    }

    /**
     * Setups database
     */
    private void setupDatabase() {
        DatabaseType type = DatabaseType.getDatabaseType(getConfigFile().getDatabase().getDatabaseType());
        if (type.equals(DatabaseType.SQLite))
            new SQLite();
        else
            new MySQL();
    }

    /**
     * Registers economy
     */
    private void registerEconomy() {
        Main.economy = new EconomyAPI(this, getConfigFile().getSettings().getEconomy()).getEconomy();
    }

    /**
     * Register modules to this plugin
     */
    private void registerModules() {
        FarmerAPI.getModuleManager().registerModule(new Voucher());
        FarmerAPI.getModuleManager().registerModule(new Production());
        FarmerAPI.getModuleManager().registerModule(new AutoHarvest());
        FarmerAPI.getModuleManager().registerModule(new AutoSeller());
        FarmerAPI.getModuleManager().registerModule(new SpawnerKiller());
        FarmerAPI.getModuleManager().loadModules();
    }

    /**
     * Sends enable message to console.
     */
    private static void sendEnableMessage() {
        Bukkit.getConsoleSender().sendMessage(ChatUtils.color("&6&l		FARMER 		&b"));
        Bukkit.getConsoleSender().sendMessage(ChatUtils.color("&aDeveloped by &2Geik"));
        Bukkit.getConsoleSender().sendMessage(ChatUtils.color("&aContributors &2" + Arrays.toString(Main.getInstance().getDescription().getAuthors().toArray())));
        Bukkit.getConsoleSender().sendMessage(ChatUtils.color("&aDiscord: &2https://discord.geik.xyz"));
        Bukkit.getConsoleSender().sendMessage(ChatUtils.color("&aWeb: &2https://geik.xyz"));
    }

    /**
     * Custom charted metrics loader
     */
    private void loadMetrics() {
        Metrics metrics = new Metrics(Main.instance, 9646);
        metrics.addCustomChart(new Metrics.SingleLineChart("ciftci_sayisi", () -> FarmerManager.getFarmers().size()));
        metrics.addCustomChart(new Metrics.SimplePie("api_eklentisi", () -> {
            String[] data = getIntegration().getClass().getName().split(".");
            return data[data.length-1];
        }));
    }

    /**
     * Constructor of class
     */
    public Main() {}
}
