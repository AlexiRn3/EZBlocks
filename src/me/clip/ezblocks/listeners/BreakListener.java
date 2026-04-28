package me.clip.ezblocks.listeners;

import me.clip.ezblocks.EZBlocks;

import org.bukkit.Bukkit;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.EventExecutor;

/**
 * Single block-break listener that registers itself at a runtime-configurable
 * EventPriority. Replaces the 6 BreakListener{Lowest,Low,Normal,High,Highest,Monitor}
 * classes that previously existed.
 */
public class BreakListener implements Listener {

	private final EZBlocks plugin;

	public BreakListener(EZBlocks i, EventPriority priority) {
		this.plugin = i;

		EventExecutor executor = (listener, event) -> {
			if (!(event instanceof BlockBreakEvent)) {
				return;
			}
			BlockBreakEvent e = (BlockBreakEvent) event;
			if (e.isCancelled()) {
				return;
			}
			if (plugin.getBreakHandler().check(e.getPlayer(), e.getBlock())) {
				plugin.getBreakHandler().handleBlockBreakEvent(e.getPlayer(), e.getBlock());
			}
		};

		Bukkit.getPluginManager().registerEvent(
				BlockBreakEvent.class,
				this,
				priority,
				executor,
				plugin,
				false);
	}

	public static EventPriority parsePriority(String name) {
		if (name == null) {
			return EventPriority.HIGHEST;
		}
		try {
			return EventPriority.valueOf(name.toUpperCase());
		} catch (IllegalArgumentException ex) {
			return EventPriority.HIGHEST;
		}
	}
}
