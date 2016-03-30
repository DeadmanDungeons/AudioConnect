package com.deadmandungeons.audioconnect;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class PlayerScheduler {
	
	private final Plugin plugin;
	private final PlayerTaskHandler handler;
	private final int tickFrequency;
	private final int maxSchedulers;
	private final Set<PlayerTask> playerTasks;
	
	private final Set<PlayerTask> startingTasks = new HashSet<>();;
	
	public PlayerScheduler(Plugin plugin, PlayerTaskHandler handler, int tickFrequency, int maxSchedulers) {
		if (maxSchedulers <= 0 || maxSchedulers > tickFrequency) {
			throw new IllegalArgumentException("maxSchedulers cannot be less than 1 or more than tickFrequency");
		}
		this.plugin = plugin;
		this.handler = handler;
		this.tickFrequency = tickFrequency;
		this.maxSchedulers = maxSchedulers;
		playerTasks = new LinkedHashSet<>(maxSchedulers);
		
		int tickDisplacement = tickFrequency / maxSchedulers;
		for (int i = 0; i < maxSchedulers; i++) {
			playerTasks.add(new PlayerTask(i, tickDisplacement));
		}
	}
	
	
	public boolean addPlayer(UUID playerId) {
		boolean idle = true;
		PlayerTask lightestTask = null;
		for (PlayerTask playerTask : playerTasks) {
			if (lightestTask == null || lightestTask.playerIds.size() > playerTask.playerIds.size()) {
				lightestTask = playerTask;
			}
			if (playerTask.task != null) {
				idle = false;
			}
		}
		return lightestTask.addPlayer(playerId, idle);
	}
	
	public boolean removePlayer(UUID playerId) {
		for (PlayerTask playerTask : playerTasks) {
			if (playerTask.removePlayer(playerId)) {
				return true;
			}
		}
		return false;
	}
	
	public void clear() {
		for (PlayerTask playerTask : playerTasks) {
			playerTask.playerIds.clear();
			playerTask.checkCancel();
		}
	}
	
	
	private class PlayerTask implements Runnable {
		
		private final Set<UUID> playerIds = new HashSet<>();
		private final int index;
		private final int tickDisplacement;
		private BukkitTask task;
		
		private PlayerTask(int index, int tickDisplacement) {
			this.index = index;
			this.tickDisplacement = tickDisplacement;
		}
		
		@Override
		public void run() {
			if (!startingTasks.isEmpty()) {
				for (PlayerTask startingTask : startingTasks) {
					// TODO it may be beneficial (depending on use case) to make each additional scheduler be executed when
					// half of the frequency period of this scheduler has passed so that the rhythm of scheduler executions
					// are balanced regardless of load fluctuations
					int diff = startingTask.index - index;
					int delay = (diff < 0 ? maxSchedulers + diff : diff) * tickDisplacement;
					startingTask.task = Bukkit.getScheduler().runTaskTimer(plugin, startingTask, delay, tickFrequency);
				}
				startingTasks.clear();
			}
			
			Iterator<UUID> iter = playerIds.iterator();
			while (iter.hasNext()) {
				UUID playerId = iter.next();
				Player player = Bukkit.getPlayer(playerId);
				if (player != null) {
					handler.handle(player);
				} else {
					iter.remove();
					checkCancel();
				}
			}
		}
		
		private boolean addPlayer(UUID playerId, boolean idle) {
			if (task == null) {
				if (idle) {
					task = Bukkit.getScheduler().runTaskTimer(plugin, this, 0, tickFrequency);
				} else {
					startingTasks.add(this);
				}
			}
			
			return playerIds.add(playerId);
		}
		
		private boolean removePlayer(UUID playerId) {
			boolean removed = playerIds.remove(playerId);
			if (removed) {
				checkCancel();
			}
			return removed;
		}
		
		private void checkCancel() {
			if (playerIds.isEmpty() && task != null) {
				task.cancel();
				task = null;
			}
		}
		
	}
	
	
	public static interface PlayerTaskHandler {
		
		void handle(Player player);
		
	}
	
}
