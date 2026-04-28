package me.clip.ezblocks.listeners;

import java.lang.reflect.Method;
import java.util.logging.Logger;

import me.clip.ezblocks.EZBlocks;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

/**
 * AutoSell integration. Implemented via reflection so AutoSell stays a true
 * soft-dependency: EZBlocks compiles and runs without the AutoSell jar.
 */
public class AutoSellListener implements Listener {

	private static final Logger LOG = Logger.getLogger("EZBlocks");

	private static final String[] EVENT_NAMES = {
		"me.clip.autosell.events.AutoSellEvent",
		"me.clip.autosell.events.DropsToInventoryEvent"
	};

	private final EZBlocks plugin;

	private AutoSellListener(EZBlocks plugin) {
		this.plugin = plugin;
	}

	public static boolean tryRegister(EZBlocks plugin) {
		AutoSellListener listener = new AutoSellListener(plugin);
		boolean any = false;

		for (String name : EVENT_NAMES) {
			Class<? extends Event> evt = loadEvent(name);
			if (evt == null) {
				continue;
			}
			EventExecutor executor = (l, event) -> {
				if (!evt.isInstance(event)) {
					return;
				}
				if (event instanceof Cancellable && ((Cancellable) event).isCancelled()) {
					return;
				}
				listener.handle(event);
			};
			Bukkit.getPluginManager().registerEvent(
					evt, listener, EventPriority.HIGH, executor, plugin, true);
			LOG.info("[EZBlocks] Listening for AutoSell event: " + evt.getName());
			any = true;
		}

		if (!any) {
			LOG.warning("[EZBlocks] AutoSell detected but no known event classes were found.");
		}
		return any;
	}

	@SuppressWarnings("unchecked")
	private static Class<? extends Event> loadEvent(String name) {
		try {
			Class<?> c = Class.forName(name);
			if (Event.class.isAssignableFrom(c)) {
				return (Class<? extends Event>) c;
			}
		} catch (ClassNotFoundException ignored) {
		}
		return null;
	}

	private void handle(Event event) {
		Player player = invokePlayer(event);
		Block block = invokeBlock(event);
		if (player == null || block == null) {
			return;
		}
		plugin.getBreakHandler().handleBlockBreakEvent(player, block);
	}

	private static Player invokePlayer(Event event) {
		try {
			Method m = event.getClass().getMethod("getPlayer");
			Object out = m.invoke(event);
			if (out instanceof Player) {
				return (Player) out;
			}
		} catch (Exception ignored) {
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
}
