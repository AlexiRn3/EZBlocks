package me.clip.ezblocks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import me.clip.ezblocks.tasks.LoadTask;
import me.clip.ezblocks.tasks.PlayerSaveTask;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class BreakHandler implements Listener {

	EZBlocks plugin;

	public static ConcurrentHashMap<String, Integer> breaks = new ConcurrentHashMap<String, Integer>();

	public BreakHandler(EZBlocks i) {
		plugin = i;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onJoin(PlayerJoinEvent e) {

		String uuid = e.getPlayer().getUniqueId().toString();

		plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new LoadTask(plugin, uuid));
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {

		String uuid = e.getPlayer().getUniqueId().toString();

		Integer broken = breaks.remove(uuid);
		if (broken != null) {
			plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new PlayerSaveTask(plugin, uuid, broken));
		}
	}

	private boolean isAllowedBlock(Material m) {

		if (EZBlocks.options.getBlacklistedBlocks() == null
				|| EZBlocks.options.getBlacklistedBlocks().isEmpty()) {
			return true;
		}

		if (EZBlocks.options.blacklistIsWhitelist()) {
			return EZBlocks.options.getBlacklistedBlocks().contains(m.toString());
		}
		return !EZBlocks.options.getBlacklistedBlocks().contains(m.toString());
	}

	private boolean isTool(ItemStack i) {

		return i != null
				&& EZBlocks.options.getTrackedTools() != null
				&& EZBlocks.options.getTrackedTools().contains(i.getType().name());
	}

	private String getName(ItemStack i) {
		switch (i.getType().name()) {
		case "WOOD_PICKAXE":
		case "WOODEN_PICKAXE":
			return "Wood Pickaxe";
		case "STONE_PICKAXE":
			return "Stone Pickaxe";
		case "IRON_PICKAXE":
			return "Iron Pickaxe";
		case "GOLD_PICKAXE":
		case "GOLDEN_PICKAXE":
			return "Golden Pickaxe";
		case "DIAMOND_PICKAXE":
			return "Diamond Pickaxe";
		case "NETHERITE_PICKAXE":
			return "Netherite Pickaxe";
		case "WOOD_AXE":
		case "WOODEN_AXE":
			return "Wood Axe";
		case "STONE_AXE":
			return "Stone Axe";
		case "IRON_AXE":
			return "Iron Axe";
		case "GOLD_AXE":
		case "GOLDEN_AXE":
			return "Golden Axe";
		case "DIAMOND_AXE":
			return "Diamond Axe";
		case "NETHERITE_AXE":
			return "Netherite Axe";
		case "WOOD_SPADE":
		case "WOODEN_SHOVEL":
			return "Wood Shovel";
		case "STONE_SPADE":
		case "STONE_SHOVEL":
			return "Stone Shovel";
		case "IRON_SPADE":
		case "IRON_SHOVEL":
			return "Iron Shovel";
		case "GOLD_SPADE":
		case "GOLDEN_SHOVEL":
			return "Golden Shovel";
		case "DIAMOND_SPADE":
		case "DIAMOND_SHOVEL":
			return "Diamond Shovel";
		case "NETHERITE_SHOVEL":
			return "Netherite Shovel";
		default:
			return i.getType().name();
		}
	}

	public boolean check(Player p, Block b) {
		if (b == null || b.getType() == Material.AIR) {
			return false;
		}

		if (!isAllowedBlock(b.getType())) {
			return false;
		}

		ItemStack i = p.getInventory().getItemInMainHand();

		if (i == null) {
			return false;
		}

		if (!isTool(i)) {
			return false;
		}

		if (EZBlocks.options.survivalOnly() && !p.getGameMode().equals(GameMode.SURVIVAL)) {
			return false;
		}

		if (!EZBlocks.options.getEnabledWorlds().contains(p.getWorld().getName())
				&& !EZBlocks.options.getEnabledWorlds().contains("all")) {
			return false;
		}

		if (EZBlocks.options.onlyBelowY()
				&& b.getLocation().getBlockY() > EZBlocks.options.getBelowYCoord()) {
			return false;
		}

		return true;
	}

	public void handleBlockBreakEvent(final Player p, final Block block) {

		ItemStack i = p.getInventory().getItemInMainHand();

		String uuid = p.getUniqueId().toString();

		int b;

		if (!breaks.containsKey(uuid)) {

			if (plugin.playerconfig.hasData(uuid)) {

				b = plugin.playerconfig.getBlocksBroken(uuid) + 1;

			} else {

				b = 1;
			}

		} else {

			b = breaks.get(uuid) + 1;
		}

		breaks.put(uuid, b);

		plugin.rewards.giveReward(p, b);

		plugin.rewards.giveIntervalReward(p, b);

		if (EZBlocks.options.pickaxeNeverBreaks()) {

			ItemMeta meta = i.getItemMeta();
			if (meta instanceof Damageable) {
				((Damageable) meta).setDamage(0);
				i.setItemMeta(meta);
			}
		}

		if (EZBlocks.options.usePickCounter()
				&& p.hasPermission("ezblocks.pickaxecounter")) {

			handlePickCounter(p, i);
		}

	}

	private void handlePickCounter(Player p, ItemStack i) {

		String format = ChatColor.translateAlternateColorCodes('&', EZBlocks.options.getPickCounterFormat());
		int one = format.indexOf('%');
		int two = format.lastIndexOf('%');
		String first = format.substring(0, one);
		String second = format.substring(two+1);

		ItemMeta meta = i.getItemMeta();
		if (meta == null) {
			return;
		}

		if (EZBlocks.options.usePickCounterDisplayName()) {

			int breaks = 1;

			if (meta.hasDisplayName()) {

				String displayName = meta.getDisplayName();

				if (displayName.startsWith(first) && displayName.endsWith(second)) {

					String f = displayName.replace(first, "");
					f = f.replace(second, "").trim();
					int amt = getInt(f);
					breaks = amt+1;
					meta.setDisplayName(format.replace("%blocks%", String.valueOf(breaks)));
					i.setItemMeta(meta);
					plugin.rewards.givePickaxeReward(p, breaks);
					plugin.rewards.givePickaxeIntervalReward(p, breaks);

				} else if (displayName.contains(" "+first) && displayName.endsWith(second)) {

					int split = displayName.indexOf(first, 0);
					String name = displayName.substring(0, split);
					String f = displayName.substring(split);
					f = f.replace(first, "");
					f = f.replace(second, "").trim();

					int amt = getInt(f);
					breaks = amt+1;
					meta.setDisplayName(name+format.replace("%blocks%", String.valueOf(breaks)));
					i.setItemMeta(meta);
					plugin.rewards.givePickaxeReward(p, breaks);
					plugin.rewards.givePickaxeIntervalReward(p, breaks);

				} else {

					meta.setDisplayName(displayName+" "+format.replace("%blocks%", "1"));
					i.setItemMeta(meta);
					plugin.rewards.givePickaxeReward(p, 1);
					plugin.rewards.givePickaxeIntervalReward(p, 1);
				}

			} else {

				String type = getName(i);

				meta.setDisplayName(type+" "+format.replace("%blocks%", "1"));
				i.setItemMeta(meta);
				plugin.rewards.givePickaxeReward(p, 1);
				plugin.rewards.givePickaxeIntervalReward(p, 1);
			}

		} else {

			if (meta.hasLore()) {

				int breaks = 0;
				boolean contains = false;
				List<String> lore = meta.getLore();
				List<String> newLore = new ArrayList<String>();

				for (String line : lore) {

					if (line.startsWith(first) && line.endsWith(second)) {

						contains = true;
						String amount = line.replace(first, "").replace(second, "");

						breaks = getInt(amount);

						newLore.add(format.replace("%blocks%", String.valueOf(breaks+1)));

					} else {

						newLore.add(line);
					}
				}

				if (!contains) {

					newLore.add(format.replace("%blocks%", "1"));
				}

				meta.setLore(newLore);
				i.setItemMeta(meta);
				plugin.rewards.givePickaxeReward(p, breaks+1);
				plugin.rewards.givePickaxeIntervalReward(p, breaks+1);

			} else {

				List<String> lore = new ArrayList<String>();
				lore.add(format.replace("%blocks%", "1"));
				meta.setLore(lore);
				i.setItemMeta(meta);
				plugin.rewards.givePickaxeReward(p, 1);
				plugin.rewards.givePickaxeIntervalReward(p, 1);

			}
		}
	}

	public int getInt(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public boolean isInt(String s) {
		try {
			Integer.parseInt(s);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

}
