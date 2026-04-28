package me.clip.ezblocks.listeners;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import me.clip.ezblocks.EZBlocks;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

/**
 * TokenEnchant integration.
 *
 * Listens to TEBlockExplodeEvent (and similar multi-block explosion events
 * fired by TokenEnchant enchants like Explosive / JackHammer / Nuke) so that
 * blocks broken by enchants are counted toward a player's total, not just
 * blocks broken directly with the pickaxe.
 *
 * Implemented via reflection to keep TokenEnchant a true soft-dependency:
 * EZBlocks compiles and runs without the TE jar on the classpath.
 */
public class TEListener implements Listener {

	private static final Logger LOG = Logger.getLogger("EZBlocks");

	/**
	 * Candidate TokenEnchant event classes, in order of preference.
	 * The package has shifted across TE versions; we try each.
	 */
	private static final String[] CANDIDATE_EVENTS = {
		"com.vk2gpz.tokenenchant.event.TEBlockExplodeEvent",
		"com.vk2gpz.tokenenchant.api.event.TEBlockExplodeEvent",
		"com.vk2gpz.tokenenchant.event.MultiBlockExplodeEvent"
	};

	/** First time we see an explosion fired by a given enchant, log it. */
	private final Set<String> seenEnchants = ConcurrentHashMap.newKeySet();

	private final EZBlocks plugin;

	private TEListener(EZBlocks plugin) {
		this.plugin = plugin;
	}

	/**
	 * Attempts to register this listener for a TokenEnchant explosion event.
	 *
	 * @return true if a TE event class was found and registered, false otherwise.
	 */
	public static boolean tryRegister(EZBlocks plugin) {
		Class<? extends Event> eventClass = findEventClass();
		if (eventClass == null) {
			return false;
		}

		TEListener listener = new TEListener(plugin);

		EventExecutor executor = (l, event) -> {
			if (!eventClass.isInstance(event)) {
				return;
			}
			if (event instanceof Cancellable && ((Cancellable) event).isCancelled()) {
				return;
			}
			listener.handle(event);
		};

		Bukkit.getPluginManager().registerEvent(
				eventClass,
				listener,
				EventPriority.MONITOR,
				executor,
				plugin,
				true);

		LOG.info("[EZBlocks] Listening for TokenEnchant event: " + eventClass.getName());
		return true;
	}

	@SuppressWarnings("unchecked")
	private static Class<? extends Event> findEventClass() {
		for (String name : CANDIDATE_EVENTS) {
			try {
				Class<?> c = Class.forName(name);
				if (Event.class.isAssignableFrom(c)) {
					return (Class<? extends Event>) c;
				}
			} catch (ClassNotFoundException ignored) {
			}
		}
		return null;
	}

	private void handle(Event event) {
		Player player = invokePlayer(event);
		if (player == null) {
			return;
		}

		String enchant = invokeEnchantName(event);
		if (enchant != null && seenEnchants.add(enchant)) {
			LOG.info("[EZBlocks] First multi-block break seen from TE enchant: " + enchant);
		}

		List<Block> blocks = invokeBlockList(event);
		if (blocks == null || blocks.isEmpty()) {
			// Fallback: just the source block
			Block source = invokeBlock(event);
			if (source != null) {
				processBlock(player, source);
			}
			return;
		}

		// Validate per-block: world, Y-coord, blacklist, etc. are checked
		// individually so that a single ineligible block in the explosion
		// doesn't disqualify the whole batch (and vice-versa).
		for (Block b : blocks) {
			if (b == null || b.getType() == Material.AIR) {
				continue;
			}
			processBlock(player, b);
		}
	}

	private void processBlock(Player player, Block block) {
		if (!plugin.getBreakHandler().check(player, block)) {
			return;
		}
		plugin.getBreakHandler().handleBlockBreakEvent(player, block);
	}

	@SuppressWarnings("unchecked")
	private static List<Block> invokeBlockList(Event event) {
		try {
			Method m = event.getClass().getMethod("blockList");
			Object out = m.invoke(event);
			if (out instanceof List) {
				return (List<Block>) out;
			}
		} catch (NoSuchMethodException ignored) {
			// Try getBlocks() as a fallback
			try {
				Method m = event.getClass().getMethod("getBlocks");
				Object out = m.invoke(event);
				if (out instanceof List) {
					return (List<Block>) out;
				}
			} catch (Exception ignored2) {
			}
		} catch (Exception e) {
			LOG.warning("[EZBlocks] Failed to read block list from TE event: " + e.getMessage());
		}
		return null;
	}

	private static Player invokePlayer(Event event) {
		try {
			Method m = event.getClass().getMethod("getPlayer");
			Object out = m.invoke(event);
			if (out instanceof Player) {
				return (Player) out;
			}
		} catch (Exception e) {
			LOG.warning("[EZBlocks] Failed to read player from TE event: " + e.getMessage());
		}
		return null;
	}

	private static Block invokeBlock(Event event) {
		try {
			Method m = event.getClass().getMethod("getBlock");
			Object out = m.invoke(event);
			if (out instanceof Block) {
				return (Block) out;
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static String invokeEnchantName(Event event) {
		// TokenEnchant exposes the triggering enchant under a few different
		// names depending on version. Try each.
		String[] candidates = {"getEnchantName", "getEnchant", "getName"};
		for (String name : candidates) {
			try {
				Method m = event.getClass().getMethod(name);
				Object out = m.invoke(event);
				if (out instanceof String) {
					return (String) out;
				}
				if (out != null) {
					return out.toString();
				}
			} catch (NoSuchMethodException ignored) {
			} catch (Exception ignored) {
				return null;
			}
		}
		return null;
	}
}
