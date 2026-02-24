package com.blockforge.horizonutilities;

import com.blockforge.horizonutilities.auction.AuctionExpireTask;
import com.blockforge.horizonutilities.auction.AuctionManager;
import com.blockforge.horizonutilities.blackmarket.BlackMarketManager;
import com.blockforge.horizonutilities.blackmarket.commands.BlackMarketCommand;
import com.blockforge.horizonutilities.blackmarket.commands.BlackMarketTabCompleter;
import com.blockforge.horizonutilities.blackmarket.gui.BlackMarketGUIListener;
import com.blockforge.horizonutilities.blackmarket.listeners.BreakerUseListener;
import com.blockforge.horizonutilities.bounty.BountyConfig;
import com.blockforge.horizonutilities.bounty.BountyManager;
import com.blockforge.horizonutilities.bounty.commands.BountyCommand;
import com.blockforge.horizonutilities.bounty.commands.BountyTabCompleter;
import com.blockforge.horizonutilities.bounty.listeners.BountyKillListener;
import com.blockforge.horizonutilities.bounty.listeners.BountyProximityListener;
import com.blockforge.horizonutilities.economy.EcoAdminCommand;
import com.blockforge.horizonutilities.lottery.LotteryDrawTask;
import com.blockforge.horizonutilities.lottery.LotteryManager;
import com.blockforge.horizonutilities.lottery.commands.LotteryCommand;
import com.blockforge.horizonutilities.lottery.commands.LotteryTabCompleter;
import com.blockforge.horizonutilities.trade.TradeConfig;
import com.blockforge.horizonutilities.trade.TradeManager;
import com.blockforge.horizonutilities.trade.commands.TradeCommand;
import com.blockforge.horizonutilities.trade.commands.TradeTabCompleter;
import com.blockforge.horizonutilities.trade.gui.TradeGUIListener;
import com.blockforge.horizonutilities.trade.listeners.TradePlayerListener;
import com.blockforge.horizonutilities.auction.AuctionNotificationManager;
import com.blockforge.horizonutilities.auction.PriceHistoryManager;
import com.blockforge.horizonutilities.auction.commands.AuctionCommand;
import com.blockforge.horizonutilities.auction.commands.AuctionTabCompleter;
import com.blockforge.horizonutilities.auction.listeners.AuctionGUIListener;
import com.blockforge.horizonutilities.auction.listeners.AuctionPlayerListener;
import com.blockforge.horizonutilities.chat.PlaceholderManager;
import com.blockforge.horizonutilities.chat.listeners.ChatListener;
import com.blockforge.horizonutilities.config.AuctionHouseConfig;
import com.blockforge.horizonutilities.config.ChatGamesConfig;
import com.blockforge.horizonutilities.config.ChatPlaceholdersConfig;
import com.blockforge.horizonutilities.config.ConfigManager;
import com.blockforge.horizonutilities.config.MessagesManager;
import com.blockforge.horizonutilities.economy.VaultHook;
import com.blockforge.horizonutilities.games.ChatGameManager;
import com.blockforge.horizonutilities.games.LeaderboardManager;
import com.blockforge.horizonutilities.games.commands.ChatGamesCommand;
import com.blockforge.horizonutilities.games.commands.ChatGamesTabCompleter;
import com.blockforge.horizonutilities.games.listeners.GameAnswerListener;
import com.blockforge.horizonutilities.auraskills.AuraSkillsManager;
import com.blockforge.horizonutilities.hooks.GPFRHook;
import com.blockforge.horizonutilities.jobs.JobManager;
import com.blockforge.horizonutilities.tax.TaxManager;
import com.blockforge.horizonutilities.jobs.commands.JobsCommand;
import com.blockforge.horizonutilities.jobs.commands.JobsTabCompleter;
import com.blockforge.horizonutilities.jobs.gui.JobsGUIListener;
import com.blockforge.horizonutilities.jobs.listeners.*;
import com.blockforge.horizonutilities.storage.DatabaseManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public class HorizonUtilitiesPlugin extends JavaPlugin {

    private static HorizonUtilitiesPlugin instance;

    private ConfigManager configManager;
    private MessagesManager messagesManager;
    private AuctionHouseConfig auctionHouseConfig;
    private ChatGamesConfig chatGamesConfig;
    private ChatPlaceholdersConfig chatPlaceholdersConfig;
    private DatabaseManager databaseManager;
    private VaultHook vaultHook;
    private AuctionManager auctionManager;
    private AuctionNotificationManager notificationManager;
    private PriceHistoryManager priceHistoryManager;
    private PlaceholderManager placeholderManager;
    private ChatGameManager chatGameManager;
    private LeaderboardManager leaderboardManager;
    private BlackMarketManager blackMarketManager;
    private LotteryManager lotteryManager;
    private GPFRHook gpfrHook;
    private AuraSkillsManager auraSkillsManager;
    private JobManager jobManager;
    private TaxManager taxManager;
    private TradeConfig tradeConfig;
    private TradeManager tradeManager;
    private BountyConfig bountyConfig;
    private BountyManager bountyManager;

    @Override
    public void onEnable() {
        instance = this;

        configManager = new ConfigManager(this);
        configManager.load();

        messagesManager = new MessagesManager(this);
        messagesManager.load();

        auctionHouseConfig = new AuctionHouseConfig(this);
        auctionHouseConfig.load();

        chatGamesConfig = new ChatGamesConfig(this);
        chatGamesConfig.load();

        chatPlaceholdersConfig = new ChatPlaceholdersConfig(this);
        chatPlaceholdersConfig.load();

        databaseManager = new DatabaseManager(this);
        databaseManager.init();

        vaultHook = new VaultHook(this);
        if (!vaultHook.setup()) {
            getLogger().warning("Vault not found — economy features disabled.");
        } else {
            getLogger().info("Vault economy hooked.");
        }

        auctionManager = new AuctionManager(this);
        notificationManager = new AuctionNotificationManager(this);
        priceHistoryManager = new PriceHistoryManager(this);

        placeholderManager = new PlaceholderManager(this);

        leaderboardManager = new LeaderboardManager(this);
        chatGameManager = new ChatGameManager(this);

        blackMarketManager = new BlackMarketManager(this);
        blackMarketManager.loadItems();

        lotteryManager = new LotteryManager(this);
        lotteryManager.loadConfig();
        lotteryManager.loadActiveInstances();

        gpfrHook = new GPFRHook(getLogger());
        if (getServer().getPluginManager().getPlugin("GriefPreventionFlagsReborn") != null) {
            if (gpfrHook.setup()) {
                gpfrHook.registerCustomFlags();
            }
        }

        auraSkillsManager = new AuraSkillsManager(this);

        taxManager = new TaxManager(this);
        jobManager = new JobManager(this);

        tradeConfig = new TradeConfig(this);
        tradeConfig.load();
        tradeManager = new TradeManager(this, tradeConfig);

        bountyConfig = new BountyConfig(this);
        bountyConfig.load();
        bountyManager = new BountyManager(this, bountyConfig, gpfrHook);
        bountyManager.loadCache();

        var pm = getServer().getPluginManager();
        pm.registerEvents(new AuctionGUIListener(this), this);
        pm.registerEvents(new AuctionPlayerListener(this), this);
        pm.registerEvents(new ChatListener(this), this);
        pm.registerEvents(new GameAnswerListener(this), this);
        pm.registerEvents(new BlackMarketGUIListener(this), this);
        pm.registerEvents(new BreakerUseListener(this), this);
        pm.registerEvents(new TradeGUIListener(this, tradeManager), this);
        pm.registerEvents(new TradePlayerListener(this, tradeManager), this);
        pm.registerEvents(new BountyKillListener(bountyManager), this);
        new BountyProximityListener(this, bountyManager);

        // Jobs listeners
        pm.registerEvents(new JobPlayerListener(this), this);
        pm.registerEvents(new JobBlockListener(this), this);
        pm.registerEvents(new JobKillListener(this), this);
        pm.registerEvents(new JobFishListener(this), this);
        pm.registerEvents(new JobCraftListener(this), this);
        pm.registerEvents(new JobBrewListener(this), this);
        pm.registerEvents(new JobEnchantListener(this), this);
        pm.registerEvents(new JobFarmListener(this), this);
        pm.registerEvents(new JobExploreListener(this), this);
        pm.registerEvents(new JobMiscListener(this), this);
        pm.registerEvents(new JobsGUIListener(this), this);

        var ahCmd = getCommand("ah");
        if (ahCmd != null) {
            ahCmd.setExecutor(new AuctionCommand(this));
            ahCmd.setTabCompleter(new AuctionTabCompleter(this));
        }

        var gamesCmd = getCommand("chatgames");
        if (gamesCmd != null) {
            gamesCmd.setExecutor(new ChatGamesCommand(this));
            gamesCmd.setTabCompleter(new ChatGamesTabCompleter());
        }

        var bmCmd = getCommand("bm");
        if (bmCmd != null) {
            bmCmd.setExecutor(new BlackMarketCommand(this));
            bmCmd.setTabCompleter(new BlackMarketTabCompleter(this));
        }

        var lotteryCmd = getCommand("lottery");
        if (lotteryCmd != null) {
            lotteryCmd.setExecutor(new LotteryCommand(this));
            lotteryCmd.setTabCompleter(new LotteryTabCompleter(this));
        }

        var tradeCmd = getCommand("trade");
        if (tradeCmd != null) {
            tradeCmd.setExecutor(new TradeCommand(this, tradeManager));
            tradeCmd.setTabCompleter(new TradeTabCompleter());
        }

        var bountyCmd = getCommand("bounty");
        if (bountyCmd != null) {
            bountyCmd.setExecutor(new BountyCommand(this, bountyManager));
            bountyCmd.setTabCompleter(new BountyTabCompleter());
        }

        var ecoCmd = getCommand("eco");
        if (ecoCmd != null) {
            ecoCmd.setExecutor(new EcoAdminCommand(this));
        }

        var jobsCmd = getCommand("jobs");
        if (jobsCmd != null) {
            jobsCmd.setExecutor(new JobsCommand(this));
            jobsCmd.setTabCompleter(new JobsTabCompleter(this));
        }

        new AuctionExpireTask(this).start();
        chatGameManager.startScheduler();
        new LotteryDrawTask(this, lotteryManager).start();

        if (configManager.isMetricsEnabled()) {
            new Metrics(this, 00000);
        }

        getLogger().info("HorizonUtilities enabled.");
    }

    @Override
    public void onDisable() {
        if (chatGameManager != null) chatGameManager.shutdown();
        if (databaseManager != null) databaseManager.close();
        getLogger().info("HorizonUtilities disabled.");
    }

    public void reloadAllConfigs() {
        configManager.load();
        messagesManager.load();
        auctionHouseConfig.load();
        chatGamesConfig.load();
        chatPlaceholdersConfig.load();
        if (tradeConfig != null) tradeConfig.load();
        if (bountyConfig != null) bountyConfig.load();
    }

    public static HorizonUtilitiesPlugin getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public MessagesManager getMessagesManager() { return messagesManager; }
    public AuctionHouseConfig getAuctionHouseConfig() { return auctionHouseConfig; }
    public ChatGamesConfig getChatGamesConfig() { return chatGamesConfig; }
    public ChatPlaceholdersConfig getChatPlaceholdersConfig() { return chatPlaceholdersConfig; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public VaultHook getVaultHook() { return vaultHook; }
    public AuctionManager getAuctionManager() { return auctionManager; }
    public AuctionNotificationManager getNotificationManager() { return notificationManager; }
    public PriceHistoryManager getPriceHistoryManager() { return priceHistoryManager; }
    public PlaceholderManager getPlaceholderManager() { return placeholderManager; }
    public ChatGameManager getChatGameManager() { return chatGameManager; }
    public LeaderboardManager getLeaderboardManager() { return leaderboardManager; }
    public BlackMarketManager getBlackMarketManager() { return blackMarketManager; }
    public LotteryManager getLotteryManager() { return lotteryManager; }
    public GPFRHook getGpfrHook() { return gpfrHook; }
    public AuraSkillsManager getAuraSkillsManager() { return auraSkillsManager; }
    public JobManager getJobManager() { return jobManager; }
    public TaxManager getTaxManager() { return taxManager; }
    public TradeConfig getTradeConfig() { return tradeConfig; }
    public TradeManager getTradeManager() { return tradeManager; }
    public BountyConfig getBountyConfig() { return bountyConfig; }
    public BountyManager getBountyManager() { return bountyManager; }
}
