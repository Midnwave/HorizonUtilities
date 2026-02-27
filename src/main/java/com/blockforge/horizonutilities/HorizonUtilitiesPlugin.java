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
import com.blockforge.horizonutilities.hooks.LuckPermsHook;
import com.blockforge.horizonutilities.hooks.PlaceholderAPIExpansion;
import com.blockforge.horizonutilities.chatbubbles.ChatBubbleConfig;
import com.blockforge.horizonutilities.chatbubbles.ChatBubbleManager;
import com.blockforge.horizonutilities.chatbubbles.ChatBubbleListener;
import com.blockforge.horizonutilities.chatbubbles.commands.ChatBubbleCommand;
import com.blockforge.horizonutilities.warps.admin.AdminWarpManager;
import com.blockforge.horizonutilities.warps.admin.commands.AdminWarpCommand;
import com.blockforge.horizonutilities.warps.admin.commands.SpawnCommand;
import com.blockforge.horizonutilities.warps.player.PlayerWarpManager;
import com.blockforge.horizonutilities.warps.player.commands.PlayerWarpCommand;
import com.blockforge.horizonutilities.warps.player.gui.PlayerWarpGUIListener;
import com.blockforge.horizonutilities.combat.CombatConfig;
import com.blockforge.horizonutilities.combat.CombatManager;
import com.blockforge.horizonutilities.combat.CombatListener;
import com.blockforge.horizonutilities.combat.commands.CombatCommand;
import com.blockforge.horizonutilities.customitems.CustomItemRegistry;
import com.blockforge.horizonutilities.customitems.commands.CustomItemCommand;
import com.blockforge.horizonutilities.customitems.items.GrapplingHookItem;
import com.blockforge.horizonutilities.customitems.items.IceBombItem;
import com.blockforge.horizonutilities.customitems.items.WolfWhistleItem;
import com.blockforge.horizonutilities.customitems.listeners.GrapplingHookListener;
import com.blockforge.horizonutilities.customitems.listeners.IceBombListener;
import com.blockforge.horizonutilities.customitems.listeners.WolfWhistleListener;
import com.blockforge.horizonutilities.tournaments.TournamentManager;
import com.blockforge.horizonutilities.tournaments.TournamentListener;
import com.blockforge.horizonutilities.tournaments.commands.TournamentCommand;
import com.blockforge.horizonutilities.config.dialog.HorizonConfigCommand;
import com.blockforge.horizonutilities.crafting.CraftingTableConfig;
import com.blockforge.horizonutilities.crafting.CraftingTableManager;
import com.blockforge.horizonutilities.crafting.CraftingTableListener;
import com.blockforge.horizonutilities.jobs.JobManager;
import com.blockforge.horizonutilities.jobs.quests.QuestsIntegration;
import com.blockforge.horizonutilities.maintenance.MaintenanceCommand;
import com.blockforge.horizonutilities.maintenance.MaintenanceListener;
import com.blockforge.horizonutilities.maintenance.MaintenanceManager;
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
    private LuckPermsHook luckPermsHook;
    private AuraSkillsManager auraSkillsManager;
    private JobManager jobManager;
    private QuestsIntegration questsIntegration;
    private TaxManager taxManager;
    private TradeConfig tradeConfig;
    private TradeManager tradeManager;
    private BountyConfig bountyConfig;
    private BountyManager bountyManager;
    private MaintenanceManager maintenanceManager;
    private ChatBubbleConfig chatBubbleConfig;
    private ChatBubbleManager chatBubbleManager;
    private AdminWarpManager adminWarpManager;
    private PlayerWarpManager playerWarpManager;
    private PlayerWarpGUIListener playerWarpGUIListener;
    private CombatConfig combatConfig;
    private CombatManager combatManager;
    private CustomItemRegistry customItemRegistry;
    private TournamentManager tournamentManager;
    private CraftingTableConfig craftingTableConfig;
    private CraftingTableManager craftingTableManager;

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

        luckPermsHook = new LuckPermsHook();
        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            if (luckPermsHook.setup()) {
                getLogger().info("LuckPerms hooked.");
            }
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        auraSkillsManager = new AuraSkillsManager(this);

        taxManager = new TaxManager(this);
        jobManager = new JobManager(this);
        questsIntegration = new QuestsIntegration(this);

        tradeConfig = new TradeConfig(this);
        tradeConfig.load();
        tradeManager = new TradeManager(this, tradeConfig);

        bountyConfig = new BountyConfig(this);
        bountyConfig.load();
        bountyManager = new BountyManager(this, bountyConfig, gpfrHook);
        bountyManager.loadCache();

        maintenanceManager = new MaintenanceManager(this);
        maintenanceManager.load();

        chatBubbleConfig = new ChatBubbleConfig(this);
        chatBubbleConfig.load();
        chatBubbleManager = new ChatBubbleManager(this, chatBubbleConfig);

        adminWarpManager = new AdminWarpManager(this);

        playerWarpManager = new PlayerWarpManager(this);
        playerWarpGUIListener = new PlayerWarpGUIListener(this, playerWarpManager);

        combatConfig = new CombatConfig(this);
        combatConfig.load();
        combatManager = new CombatManager(this, combatConfig);

        customItemRegistry = new CustomItemRegistry(this);
        customItemRegistry.register(new GrapplingHookItem(this));
        customItemRegistry.register(new IceBombItem(this));
        customItemRegistry.register(new WolfWhistleItem(this));

        tournamentManager = new TournamentManager(this);

        craftingTableConfig = new CraftingTableConfig(this);
        craftingTableConfig.load();
        craftingTableManager = new CraftingTableManager(this, craftingTableConfig);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new MaintenanceListener(maintenanceManager), this);
        pm.registerEvents(new ChatBubbleListener(this, chatBubbleManager), this);
        pm.registerEvents(playerWarpGUIListener, this);
        pm.registerEvents(new CombatListener(this, combatManager), this);
        pm.registerEvents(new GrapplingHookListener(this), this);
        pm.registerEvents(new IceBombListener(this), this);
        pm.registerEvents(new WolfWhistleListener(this), this);
        pm.registerEvents(new TournamentListener(this, tournamentManager), this);
        pm.registerEvents(new CraftingTableListener(this, craftingTableManager), this);
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

        var tournamentBukkitCmd = getCommand("tournament");
        if (tournamentBukkitCmd != null) {
            var tournCmd = new TournamentCommand(this, tournamentManager);
            tournamentBukkitCmd.setExecutor(tournCmd);
            tournamentBukkitCmd.setTabCompleter(tournCmd);
        }

        var customItemBukkitCmd = getCommand("customitem");
        if (customItemBukkitCmd != null) {
            var ciCmd = new CustomItemCommand(this);
            customItemBukkitCmd.setExecutor(ciCmd);
            customItemBukkitCmd.setTabCompleter(ciCmd);
        }

        var combatBukkitCmd = getCommand("combat");
        if (combatBukkitCmd != null) {
            var combatCmd = new CombatCommand(this, combatManager);
            combatBukkitCmd.setExecutor(combatCmd);
            combatBukkitCmd.setTabCompleter(combatCmd);
        }

        var pwarpCmd = getCommand("pwarp");
        if (pwarpCmd != null) {
            var pwarpExec = new PlayerWarpCommand(this, playerWarpManager, playerWarpGUIListener);
            pwarpCmd.setExecutor(pwarpExec);
            pwarpCmd.setTabCompleter(pwarpExec);
        }

        var adminWarpCmd = new AdminWarpCommand(this, adminWarpManager);
        var warpCmd = getCommand("warp");
        if (warpCmd != null) { warpCmd.setExecutor(adminWarpCmd); warpCmd.setTabCompleter(adminWarpCmd); }
        var setWarpCmd = getCommand("setwarp");
        if (setWarpCmd != null) { setWarpCmd.setExecutor(adminWarpCmd); setWarpCmd.setTabCompleter(adminWarpCmd); }
        var delWarpCmd = getCommand("delwarp");
        if (delWarpCmd != null) { delWarpCmd.setExecutor(adminWarpCmd); delWarpCmd.setTabCompleter(adminWarpCmd); }
        var warpsCmd = getCommand("warps");
        if (warpsCmd != null) { warpsCmd.setExecutor(adminWarpCmd); warpsCmd.setTabCompleter(adminWarpCmd); }

        var spawnCmd = new SpawnCommand(this, adminWarpManager);
        var spawnBukkitCmd = getCommand("spawn");
        if (spawnBukkitCmd != null) { spawnBukkitCmd.setExecutor(spawnCmd); spawnBukkitCmd.setTabCompleter(spawnCmd); }
        var setSpawnCmd = getCommand("setspawn");
        if (setSpawnCmd != null) { setSpawnCmd.setExecutor(spawnCmd); setSpawnCmd.setTabCompleter(spawnCmd); }

        var bubblesCmd = getCommand("chatbubbles");
        if (bubblesCmd != null) {
            var bubbleCmd = new ChatBubbleCommand(this, chatBubbleManager);
            bubblesCmd.setExecutor(bubbleCmd);
            bubblesCmd.setTabCompleter(bubbleCmd);
        }

        var maintCmd = getCommand("maintenance");
        if (maintCmd != null) {
            var maintenanceCommand = new MaintenanceCommand(this);
            maintCmd.setExecutor(maintenanceCommand);
            maintCmd.setTabCompleter(maintenanceCommand);
        }

        var horizonConfigCmd = getCommand("horizonconfig");
        if (horizonConfigCmd != null) {
            var hcCmd = new HorizonConfigCommand(this);
            horizonConfigCmd.setExecutor(hcCmd);
            horizonConfigCmd.setTabCompleter(hcCmd);
        }

        new AuctionExpireTask(this).start();
        chatGameManager.startScheduler();
        new LotteryDrawTask(this, lotteryManager).start();

        // Expire bounties every 5 minutes
        getServer().getScheduler().runTaskTimerAsynchronously(this,
                () -> bountyManager.expireOldBounties(), 6000L, 6000L);

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
        if (chatBubbleConfig != null) chatBubbleConfig.load();
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
    public LuckPermsHook getLuckPermsHook() { return luckPermsHook; }
    public AuraSkillsManager getAuraSkillsManager() { return auraSkillsManager; }
    public JobManager getJobManager() { return jobManager; }
    public QuestsIntegration getQuestsIntegration() { return questsIntegration; }
    public TaxManager getTaxManager() { return taxManager; }
    public TradeConfig getTradeConfig() { return tradeConfig; }
    public TradeManager getTradeManager() { return tradeManager; }
    public BountyConfig getBountyConfig() { return bountyConfig; }
    public BountyManager getBountyManager() { return bountyManager; }
    public MaintenanceManager getMaintenanceManager() { return maintenanceManager; }
    public ChatBubbleConfig getChatBubbleConfig()     { return chatBubbleConfig; }
    public ChatBubbleManager getChatBubbleManager()   { return chatBubbleManager; }
    public AdminWarpManager getAdminWarpManager()     { return adminWarpManager; }
    public PlayerWarpManager getPlayerWarpManager()   { return playerWarpManager; }
    public CombatConfig getCombatConfig()             { return combatConfig; }
    public CombatManager getCombatManager()           { return combatManager; }
    public CustomItemRegistry getCustomItemRegistry() { return customItemRegistry; }
    public TournamentManager getTournamentManager()       { return tournamentManager; }
    public CraftingTableConfig getCraftingTableConfig()   { return craftingTableConfig; }
    public CraftingTableManager getCraftingTableManager() { return craftingTableManager; }
}
