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

/**
 * This class facilitates the scheduled execution of a repeated data processing task
 * for a set of players in an efficient manner through load balancing.<br>
 * Players added to this scheduler are partitioned into separate tasks which are executed
 * at different times (or ticks) displaced from each other as a means to balance the load.
 * The amount of partitioned tasks will never exceed the defined <code>tickFrequency</code>
 * or the optionally defined <code>maximumTasks</code>. The partitioned tasks are only scheduled
 * for execution whenever there are players added, and will stop and idle when there are none.<br>
 * There are 3 simple methods to handle scheduling:<br>
 * <ul>
 * <li>{@link #addPlayer(UUID)}</li>
 * <li>{@link #removePlayer(UUID)}</li>
 * <li>{@link #clear()}</li>
 * </ul>
 * These operations are synchronized and thread safe.<br>
 * Note that all scheduled tasks run on the main server thread which synchronizes
 * with this scheduler as the intrinsic lock.
 * @see {@link #PlayerScheduler(Plugin, PlayerDataWriter, int, int)}
 * @author Jon
 */
public class PlayerScheduler {
	
	private final Plugin plugin;
	private final PlayerDataWriter writer;
	private final int tickFrequency;
	private final int maximumTasks;
	private final Set<PlayerTask> playerTasks;
	
	private final Set<PlayerTask> startingTasks = new HashSet<>();
	
	
	/**
	 * Construct a new PlayerScheduler
	 * <b>Note:</b> maximumTasks will be set as the given tickFrequency, so this is equivalent to:
	 * {@link #PlayerScheduler(Plugin, PlayerDataWriter, int, int) PlayerScheduler(plugin, writer, tickFrequency, tickFrequency)}
	 * @param plugin - The plugin in which tasks will be scheduled for
	 * @param writer - The player data writer that will be notified for each player during each scheduled execution
	 * @param tickFrequency - The frequency in server ticks that the given data writer will be notified for each player
	 * @throws IllegalArgumentException if tickFrequency or maximumTasks is less than 1
	 */
	public PlayerScheduler(Plugin plugin, PlayerDataWriter writer, int tickFrequency) throws IllegalArgumentException {
		this(plugin, writer, tickFrequency, tickFrequency);
	}
	
	/**
	 * Construct a new PlayerScheduler
	 * @param plugin - The plugin in which tasks will be scheduled for
	 * @param writer - The player data writer that will be notified for each player during each scheduled execution
	 * @param tickFrequency - The frequency in server ticks that the given data writer will be notified for each player
	 * @param maximumTasks - The maximum amount of displaced scheduled tasks for load balancing which will not exceed tickFrequency
	 * @throws IllegalArgumentException if tickFrequency or maximumTasks is less than 1
	 */
	public PlayerScheduler(Plugin plugin, PlayerDataWriter writer, int tickFrequency, int maximumTasks) throws IllegalArgumentException {
		if (tickFrequency <= 0 || maximumTasks <= 0) {
			throw new IllegalArgumentException("tickFrequency and maximumTasks cannot be less than 1");
		}
		if (maximumTasks > tickFrequency) {
			maximumTasks = tickFrequency;
		}
		
		this.plugin = plugin;
		this.writer = writer;
		this.tickFrequency = tickFrequency;
		this.maximumTasks = maximumTasks;
		playerTasks = new LinkedHashSet<>(maximumTasks);
		
		int tickDisplacement = tickFrequency / maximumTasks;
		for (int i = 0; i < maximumTasks; i++) {
			playerTasks.add(new PlayerTask(i, tickDisplacement));
		}
	}
	
	
	/**
	 * Add a player to this scheduler to begin scheduled execution of
	 * {@link PlayerDataWriter#writeData(Player)} for the player.<br>
	 * If a player identified by the given ID is not online during scheduled execution,
	 * the playerId will be removed from this scheduler.
	 * @param playerId - The UUID of the player to add to this scheduler
	 * @return <code>true</code> if this scheduler did not already contain the given playerId
	 */
	public synchronized boolean addPlayer(UUID playerId) {
		boolean idle = true;
		PlayerTask lightestTask = null;
		for (PlayerTask playerTask : playerTasks) {
			if (playerTask.playerIds.contains(playerId)) {
				return false;
			}
			if (lightestTask == null || lightestTask.playerIds.size() > playerTask.playerIds.size()) {
				lightestTask = playerTask;
			}
			if (playerTask.task != null) {
				idle = false;
			}
		}
		return lightestTask.addPlayer(playerId, idle);
	}
	
	/**
	 * Remove a player from this scheduler to stop scheduled executions of
	 * {@link PlayerDataWriter#writeData(Player)} for the player.
	 * @param playerId - The UUID of the player to remove from this scheduler
	 * @return <code>true</code> if this scheduler contained the given playerId
	 */
	public synchronized boolean removePlayer(UUID playerId) {
		for (PlayerTask playerTask : playerTasks) {
			if (playerTask.removePlayer(playerId)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Remove all players from this scheduler and cancel all scheduled tasks
	 */
	public synchronized void clear() {
		for (PlayerTask playerTask : playerTasks) {
			playerTask.playerIds.clear();
			playerTask.checkCancel();
		}
	}
	
	
	private class PlayerTask implements Runnable {
		
		private final Set<UUID> playerIds = new HashSet<>();
		private final int index;
		private final int tickDisplacement;
		private volatile BukkitTask task;
		
		private PlayerTask(int index, int tickDisplacement) {
			this.index = index;
			this.tickDisplacement = tickDisplacement;
		}
		
		@Override
		public void run() {
			synchronized (PlayerScheduler.this) {
				if (!startingTasks.isEmpty()) {
					for (PlayerTask startingTask : startingTasks) {
						// TODO it may be beneficial (depending on use case) to make each additional scheduler be executed when
						// half of the frequency period of this scheduler has passed so that the rhythm of scheduler executions
						// are balanced regardless of load fluctuations
						int diff = startingTask.index - index;
						int delay = (diff < 0 ? maximumTasks + diff : diff) * tickDisplacement;
						startingTask.task = Bukkit.getScheduler().runTaskTimer(plugin, startingTask, delay, tickFrequency);
					}
					startingTasks.clear();
				}
				
				Iterator<UUID> iter = playerIds.iterator();
				while (iter.hasNext()) {
					UUID playerId = iter.next();
					Player player = Bukkit.getPlayer(playerId);
					if (player != null) {
						writer.writeData(player);
					} else {
						iter.remove();
						checkCancel();
					}
				}
				if (playerIds.size() > 0) {
					writer.flushData();
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
	
	
	/**
	 * The interface defining basic player data processing methods to be executed by a {@link PlayerScheduler}
	 * @author Jon
	 */
	public static interface PlayerDataWriter {
		
		/**
		 * Called for a single player in a {@link PlayerScheduler} partitioned task
		 * @param player - The player to write and/or process data for
		 */
		void writeData(Player player);
		
		/**
		 * Called after all players in a {@link PlayerScheduler} partitioned task
		 * have been processed by {@link #writeData(Player)}
		 */
		void flushData();
		
	}
	
}
