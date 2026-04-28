package me.clip.ezblocks;

import java.util.Map;

import me.clip.ezblocks.database.Database;
import me.clip.ezblocks.database.MySQL;
import me.clip.ezblocks.listeners.AutoSellListener;
import me.clip.ezblocks.listeners.BreakListener;
import me.clip.ezblocks.listeners.TEListener;
import me.clip.ezblocks.tasks.IntervalSaveTask;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class EZBlocks extends JavaPlugin {

	public PlayerConfig playerconfig = new PlayerConfig(this);
	protected EZBlocksConfig config = new EZBlocksConfig(this);
	protected BreakHandler breakHandler = new BreakHandler(this);
	protected RewardHandler rewards = new RewardHandler(this);
	protected EZBlocksCommands commands = new EZBlocksCommands(this);

	protected static BlockOptions options;

	protected static int saveInterval;

	protected static BukkitTask savetask;

	private static EZBlocks ezblocks;

	public static Database database = null;

	private boolean tokenEnchantHooked = false;

	@Override
	public void onEnable() {

		ezblocks = this;

		config.loadConfigurationFile();

		loadOptions();

		initDb();

		breakHandler = new BreakHandler(this);

		Bukkit.getServer().getPluginManager().registerEvents(breakHandler, this);

		registerBlockBreakListener();

		startSaveTask();

		getCommand("blocks").setExecutor(commands);

		getLogger().info(config.loadGlobalRewards() + " global rewards loaded!");

		getLogger().info(config.loadIntervalRewards() + " interval rewards loaded!");

		getLogger().info(config.loadPickaxeGlobalRewards() + " global pickaxe rewards loaded!");

		getLogger().info(config.loadPickaxeIntervalRewards() + " interval pickaxe rewards loaded!");

		tryHookTokenEnchant();

		// Late-loading TokenEnchant safety net (in case it loads after us)
		Bukkit.getPluginManager().registerEvents(new Listener() {
			@EventHandler(priority = EventPriority.MONITOR)
			public void onPluginEnable(PluginEnableEvent e) {
				if ("TokenEnchant".equalsIgnoreCase(e.getPlugin().getName())) {
					tryHookTokenEnchant();
				}
			}
		}, this);
	}

	private void tryHookTokenEnchant() {
		if (tokenEnchantHooked) {
			return;
		}
		if (!Bukkit.getPluginManager().isPluginEnabled("TokenEnchant")) {
			return;
		}
		if (!config.hookTokenEnchant()) {
			getLogger().info("TokenEnchant is present but 'hooks.tokenenchant.count_exploded_blocks' is disabled in config.");
			return;
		}
		boolean ok = TEListener.tryRegister(this);
		if (ok) {
			tokenEnchantHooked = true;
			getLogger().info("Hooked into TokenEnchant for explosion-based block tracking.");
		} else {
			getLogger().warning("TokenEnchant detected but its event class could not be located. Exploded blocks will NOT be counted. Please report your TokenEnchant version.");
		}
	}

	private void initDb() {
		if (!getConfig().getBoolean("database.enabled")) {
			playerconfig.reload();
			playerconfig.save();
			getLogger().info("Saving/loading via flatfile!");
		} else {
			// Make connection to the database
			try {
				getLogger().info("Creating MySQL connection ...");
				database = new MySQL(getConfig().getString("database.prefix"),
						getConfig().getString("database.hostname"), getConfig()
								.getInt("database.port") + "", getConfig()
								.getString("database.database"), getConfig()
								.getString("database.username"), getConfig()
								.getString("database.password"));
				database.open();

				String table = database.getTablePrefix() + "playerblocks";
				if (!database.checkTable(table)) {
					getLogger().info("Creating MySQL table " + table + " ...");

					database.createTable("CREATE TABLE IF NOT EXISTS `"
							+ table + "` ("
							+ "  `uuid` varchar(50) NOT NULL,"
							+ "  `blocksmined` integer NOT NULL,"
							+ "  PRIMARY KEY (`uuid`)"
							+ ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
				}
			} catch (Exception ex) {
				getLogger().severe("Database init failed: " + ex.getMessage());
				ex.printStackTrace();
				getLogger().severe("Falling back to flatfiles ...");
				database = null;
				playerconfig.reload();
				playerconfig.save();
			}
		}
	}

	private void loadOptions() {
		saveInterval = getConfig().getInt("save_interval");
		options = new BlockOptions();
		options.setUseBlocksCommand(getConfig().getBoolean("blocks_broken_command_enabled"));
		options.setBrokenMsg(getConfig().getString("blocks_broken_message"));
		options.setEnabledWorlds(getConfig().getStringList("enabled_worlds"));
		options.setUsePickCounter(config.pickCounterEnabled());
		options.setUsePickCounterDisplayName(config.pickCounterInDisplay());
		options.setPickCounterFormat(config.pickCounterFormat());
		options.setPickaxeNeverBreaks(getConfig().getBoolean("pickaxe_never_breaks"));
		options.setOnlyBelowY(getConfig().getBoolean("only_track_below_y.enabled"));
		options.setBelowYCoord(getConfig().getInt("only_track_below_y.coord"));
		options.setSurvivalOnly(getConfig().getBoolean("survival_mode_only"));
		options.setBlacklistedBlocks(getConfig().getStringList("material_blacklist"));
		options.setTrackedTools(config.trackedTools());
		options.setBlacklistIsWhitelist(config.blacklistIsWhitelist());
		options.setGiveRewardsOnAddCommand(config.giveRewardsOnAddCommand());
	}

	protected void reload() {
		stopSaveTask();
		getServer().getScheduler().runTask(this, new IntervalSaveTask(this));
		reloadConfig();
		saveConfig();
		loadOptions();
		startSaveTask();
		getLogger().info(config.loadGlobalRewards() + " global rewards loaded!");
		getLogger().info(config.loadIntervalRewards() + " interval rewards loaded!");
		getLogger().info(config.loadPickaxeGlobalRewards() + " global pickaxe rewards loaded!");
		getLogger().info(config.loadPickaxeIntervalRewards() + " interval pickaxe rewards loaded!");
	}

	@Override
	public void onDisable() {
		stopSaveTask();
		if (BreakHandler.breaks != null) {
			int count = 0;
			for (Map.Entry<String, Integer> entry : BreakHandler.breaks.entrySet()) {
				playerconfig.savePlayer(entry.getKey(), entry.getValue());
				count++;
			}
			getLogger().info(count + " players saved!");
			BreakHandler.breaks.clear();
		}

		ezblocks = null;
	}

	protected void registerBlockBreakListener() {

		if (config.useAutoSellEvents() && Bukkit.getPluginManager().getPlugin("AutoSell") != null) {
			getLogger().info("Using AutoSell events for block break and sell detection...");
			AutoSellListener.tryRegister(this);
			return;
		}
		getLogger().info("AutoSell not detected (or disabled in config). Using bukkit BlockBreakEvent listener...");

		EventPriority priority = BreakListener.parsePriority(config.getListenerPriority());
		new BreakListener(this, priority);
		getLogger().info("BlockBreakEvent listener registered on " + priority.name());
	}

	private void startSaveTask() {
		if (savetask != null) {
			savetask.cancel();
			savetask = null;
		}
		getLogger().info("Saving all players every " + saveInterval + " minutes");
		savetask = getServer().getScheduler().runTaskTimerAsynchronously(
				this, new IntervalSaveTask(this), 1L,
				((20L * 60L) * saveInterval));
	}

	private void stopSaveTask() {
		if (savetask != null) {
			savetask.cancel();
			savetask = null;
		}
	}

	public int getBlocksBroken(Player p) {
		Integer v = BreakHandler.breaks.get(p.getUniqueId().toString());
		return v == null ? 0 : v;
	}

	public static EZBlocks getEZBlocks() {
		return ezblocks;
	}

	public BreakHandler getBreakHandler() {
		return breakHandler;
	}
}
