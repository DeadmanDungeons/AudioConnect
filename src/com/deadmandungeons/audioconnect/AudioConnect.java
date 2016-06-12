package com.deadmandungeons.audioconnect;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.metadata.FixedMetadataValue;
import com.deadmandungeons.deadmanplugin.DeadmanPlugin;
import com.deadmandungeons.deadmanplugin.DeadmanUtils;
import com.deadmandungeons.deadmanplugin.Messenger;
import com.deadmandungeons.deadmanplugin.filedata.DeadmanConfig;
import com.deadmandungeons.deadmanplugin.filedata.DeadmanConfig.ConfigEntry;
import com.deadmandungeons.deadmanplugin.filedata.PluginFile;

import com.deadmandungeons.audioconnect.PlayerScheduler.PlayerTaskHandler;
import com.deadmandungeons.audioconnect.command.CommandHandler;
import com.deadmandungeons.audioconnect.messages.AudioMessage;
import com.deadmandungeons.audioconnect.messages.AudioMessage.AudioFile;
import com.deadmandungeons.audioconnect.messages.AudioMessage.Range;
import com.deadmandungeons.connect.commons.ConnectUtils;
import com.deadmandungeons.connect.commons.Result;
import com.mewin.WGCustomFlags.WGCustomFlagsPlugin;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.util.Locations;
import com.sk89q.worldguard.protection.flags.SetFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;


public final class AudioConnect extends DeadmanPlugin implements Listener {
	
	private static final String TRACKING_DATA_METADATA = "AC:tracking-data";
	private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{2,16}$");
	private static final String REGION_FLAG_CMD_REGEX = "^region (?:flag|f) [\\S]+(?: -w [\\S]+)? ([\\S]+) ([\\S]+(?: [\\S]+)*?)$";
	private static final Pattern REGION_FLAG_CMD_PATTERN = Pattern.compile(REGION_FLAG_CMD_REGEX);
	private static final int REGION_CHECK_DELAY = 3000;
	private static final int WORLD_TIME_NIGHT_TICKS = 13000;
	
	
	// execute every 20 ticks (1 second) with max of 5 displaced scheduler tasks
	private final PlayerScheduler scheduler = new PlayerScheduler(this, new PlayerTrackingHandler(), 20, 5);
	private final AudioConnectClient client = new AudioConnectClient(this, scheduler);
	private final Map<String, AudioFile> audioFileCache = new HashMap<>();
	
	private SetFlag<String> audioFlag;
	private SetFlag<String> audioNightFlag;
	private SetFlag<String> audioDayFlag;
	private StringFlag audioDelayFlag;
	private Messenger messenger;
	
	public static AudioConnect getInstance() {
		return getDeadmanPlugin(AudioConnect.class);
	}
	
	
	@Override
	protected void onPluginEnable() {
		setConfig(Config.config);
		
		audioFlag = new SetFlag<String>("audio", new StringFlag(null));
		audioNightFlag = new SetFlag<String>("audio-night", new StringFlag(null));
		audioDayFlag = new SetFlag<String>("audio-day", new StringFlag(null));
		audioDelayFlag = new StringFlag("audio-delay");
		
		WGCustomFlagsPlugin customFlags = (WGCustomFlagsPlugin) Bukkit.getPluginManager().getPlugin("WGCustomFlags");
		customFlags.addCustomFlag(audioFlag);
		customFlags.addCustomFlag(audioNightFlag);
		customFlags.addCustomFlag(audioDayFlag);
		customFlags.addCustomFlag(audioDelayFlag);
		
		PluginFile langFile = PluginFile.creator(this, LANG_DIRECTORY + "english.yml").defaultFile("english.yml").create();
		messenger = new Messenger(this, langFile);
		
		getServer().getPluginManager().registerEvents(this, this);
		getCommand("ac").setExecutor(new CommandHandler(this, messenger));
		
		UUID userId = Config.getConnectionUserId();
		if (userId == null) {
			String property = Config.CONNECTION_USER_ID.getPath();
			getLogger().severe("The required " + property + " config property is missing or invalid! Client cannot be started...");
			return;
		}
		
		Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
			
			@Override
			public void run() {
				if (!client.isConnected()) {
					return;
				}
				synchronized (client) {
					for (Player player : Bukkit.getOnlinePlayers()) {
						if (client.isConnected() && !client.isPlayerTracked(player.getUniqueId())) {
							String announcement = getMessenger().getMessage("misc.announcement", true);
							String connectUrl = client.getPlayerConnectUrl(player.getUniqueId());
							String connectDetails = getMessenger().getMessage("misc.connect-details", true, connectUrl);
							
							getMessenger().sendMessage(player, "misc.top-bar");
							player.sendMessage(announcement + connectDetails);
							getMessenger().sendMessage(player, "misc.bottom-bar");
						}
					}
				}
			}
		}, 0, Config.ANNOUNCE_FREQUENCY.value().intValue() * 20);
		
		client.connect(userId);
	}
	
	@Override
	protected void onPluginDisable() {
		client.disconnect();
	}
	
	public AudioConnectClient getClient() {
		return client;
	}
	
	public Messenger getMessenger() {
		return messenger;
	}
	
	
	public AudioMessage createAudioMessage(Player player) {
		return createAudioMessage(player, null);
	}
	
	private AudioMessage createAudioMessage(Player player, PreviousTrackingData previousData) {
		Set<AudioFile> audioFiles = new HashSet<>();
		Range delayRange = null;
		
		int priority = 0;
		Location loc = player.getLocation();
		for (ProtectedRegion region : WorldGuardPlugin.inst().getRegionManager(loc.getWorld()).getApplicableRegions(loc)) {
			if (region.getPriority() > priority) {
				audioFiles.clear();
			}
			if (region.getPriority() >= priority) {
				Set<String> audioFileNames = region.getFlag(audioFlag);
				if (audioFileNames != null) {
					addAllAudioFiles(audioFiles, audioFileNames);
				}
				
				if (loc.getWorld().getTime() < WORLD_TIME_NIGHT_TICKS) {
					Set<String> dayAudioFileNames = region.getFlag(audioDayFlag);
					if (dayAudioFileNames != null) {
						addAllAudioFiles(audioFiles, dayAudioFileNames);
					}
				} else {
					Set<String> nightAudioFileNames = region.getFlag(audioNightFlag);
					if (nightAudioFileNames != null) {
						addAllAudioFiles(audioFiles, nightAudioFileNames);
					}
				}
				
				// only get audio-delay flag once and ignore overlapping regions with same flag
				if (delayRange == null) {
					String delayRangeStr = region.getFlag(audioDelayFlag);
					if (delayRangeStr != null) {
						delayRange = Range.parse(delayRangeStr);
					}
				}
				
				priority = region.getPriority();
			}
		}
		
		if (previousData == null || (!Objects.equals(delayRange, previousData.delayRange) || !audioFiles.equals(previousData.audioFiles))) {
			AudioMessage.Builder messageBuilder = AudioMessage.builder(player.getUniqueId()).primary();
			for (AudioFile audioFile : audioFiles) {
				messageBuilder.audio(audioFile);
			}
			if (delayRange != null) {
				messageBuilder.delayRange(delayRange);
			}
			
			if (previousData == null) {
				previousData = getPreviousData(player);
			}
			previousData.audioFiles = audioFiles;
			previousData.delayRange = delayRange;
			
			return messageBuilder.build();
		}
		return null;
	}
	
	private void addAllAudioFiles(Set<AudioFile> audioFiles, Set<String> audioFileNames) {
		for (String audioFileName : audioFileNames) {
			AudioFile audioFile = getCachedAudioFile(audioFileName);
			if (audioFile != null) {
				audioFiles.add(audioFile);
			}
		}
	}
	
	private AudioFile getCachedAudioFile(String audioFileName) {
		if (audioFileCache.containsKey(audioFileName)) {
			return audioFileCache.get(audioFileName);
		} else {
			Result<AudioFile> parseResult = AudioFile.fromFileName(audioFileName);
			if (parseResult.isError()) {
				getLogger().warning("Loaded audio region with invalid file name [" + audioFileName + "]: " + parseResult.getErrorMessage());
			}
			AudioFile audioFile = parseResult.getResult();
			audioFileCache.put(audioFileName, audioFile);
			return audioFile;
		}
	}
	
	private PreviousTrackingData getPreviousData(Player player) {
		PreviousTrackingData previousData = DeadmanUtils.getMetadata(this, player, TRACKING_DATA_METADATA, PreviousTrackingData.class);
		if (previousData == null) {
			previousData = new PreviousTrackingData(player.getLocation());
			player.setMetadata(TRACKING_DATA_METADATA, new FixedMetadataValue(this, previousData));
		}
		return previousData;
	}
	
	
	@EventHandler
	private void onPlayerJoin(PlayerJoinEvent event) {
		client.notifyPlayerJoin(event.getPlayer());
	}
	
	@EventHandler
	private void onPlayerQuit(PlayerQuitEvent event) {
		client.notifyPlayerQuit(event.getPlayer());
	}
	
	@EventHandler(ignoreCancelled = true)
	private void onConsoleFlagRegion(ServerCommandEvent event) {
		validateRegionFlagCommand(event.getCommand(), event.getSender(), event);
	}
	
	@EventHandler(ignoreCancelled = true)
	private void onPlayerFlagRegion(PlayerCommandPreprocessEvent event) {
		validateRegionFlagCommand(event.getMessage().substring(1), event.getPlayer(), event);
	}
	
	private void validateRegionFlagCommand(String command, CommandSender sender, Cancellable event) {
		Matcher matcher = REGION_FLAG_CMD_PATTERN.matcher(command);
		if (matcher.find()) {
			String flagName = matcher.group(1);
			if (audioFlag.getName().equalsIgnoreCase(flagName) || audioDayFlag.getName().equalsIgnoreCase(flagName)
					|| audioNightFlag.getName().equalsIgnoreCase(flagName)) {
				// validate that the audio file names used in the WorldGuard flag command for an audio flag is valid
				String fileNames = matcher.group(2);
				for (String fileName : fileNames.split(",")) {
					Result<AudioFile> parseResult = AudioFile.fromFileName(fileName);
					if (parseResult.isError()) {
						messenger.sendErrorMessage(sender, "failed.invalid-file-name", fileName, parseResult.getErrorMessage());
						event.setCancelled(true);
					}
				}
			} else if (audioDelayFlag.getName().equalsIgnoreCase(flagName)) {
				// validate that the delay range value used in the WorldGuard flag command for audio-delay flag is valid
				String delayRangeStr = matcher.group(2);
				Range delayRange = Range.parse(delayRangeStr);
				if (delayRange == null) {
					messenger.sendErrorMessage(sender, "failed.invalid-delay-range", delayRangeStr);
					event.setCancelled(true);
				}
			}
		}
	}
	
	
	private class PlayerTrackingHandler implements PlayerTaskHandler {
		
		@Override
		public void handle(Player player) {
			Location loc = player.getLocation();
			PreviousTrackingData previousData = getPreviousData(player);
			
			long now = System.currentTimeMillis();
			if (previousData.timestamp + REGION_CHECK_DELAY > now) {
				return;
			}
			if (!Locations.isDifferentBlock(previousData.location, loc)) {
				return;
			}
			
			AudioMessage message = createAudioMessage(player, previousData);
			
			if (message != null) {
				client.writeAndFlush(message);
				
				previousData.timestamp = now;
			}
			
			previousData.location = loc;
		}
		
	}
	
	private static class PreviousTrackingData {
		
		private long timestamp;
		private Location location;
		private Set<AudioFile> audioFiles;
		private Range delayRange;
		
		private PreviousTrackingData(Location location) {
			this.location = location;
		}
		
	}
	
	
	public static class Config {
		
		private static final DeadmanConfig config = new DeadmanConfig();
		public static final ConfigEntry<String> CONNECTION_HOST = config.entry(String.class, "connection.endpoint.host");
		public static final ConfigEntry<Number> CONNECTION_PORT = config.entry(Number.class, "connection.endpoint.port");
		public static final ConfigEntry<Boolean> CONNECTION_SECURE = config.entry(Boolean.class, "connection.endpoint.secure");
		public static final ConfigEntry<String> CONNECTION_USER_ID = config.entry(String.class, "connection.user-id");
		public static final ConfigEntry<Number> RECONNECT_INTERVAL = config.entry(Number.class, "reconnect.interval");
		public static final ConfigEntry<Number> RECONNECT_MAX_INTERVAL = config.entry(Number.class, "reconnect.max-interval");
		public static final ConfigEntry<Number> RECONNECT_DELAY = config.entry(Number.class, "reconnect.delay");
		public static final ConfigEntry<Number> RECONNECT_MAX_ATTEMPTS = config.entry(Number.class, "reconnect.max-attempts");
		public static final ConfigEntry<Number> COMMAND_COOLDOWN = config.entry(Number.class, "options.command-cooldown");
		public static final ConfigEntry<Number> ANNOUNCE_FREQUENCY = config.entry(Number.class, "options.announce-frequency");
		
		
		public static UUID getConnectionUserId() {
			UUID userId = null;
			String userIdStr = CONNECTION_USER_ID.value();
			if (!StringUtils.isEmpty(userIdStr)) {
				userId = ConnectUtils.parseId(userIdStr);
				if (userId == null && USERNAME_PATTERN.matcher(userIdStr).matches()) {
					userId = Bukkit.getOfflinePlayer(userIdStr).getUniqueId();
				}
			}
			return userId;
		}
		
		public static URI getConnectionEndpointUri() {
			return getConnectionEndpointUri(CONNECTION_SECURE, CONNECTION_HOST, CONNECTION_PORT);
		}
		
		private static URI getConnectionEndpointUri(ConfigEntry<Boolean> secure, ConfigEntry<String> host, ConfigEntry<Number> port) {
			try {
				return createUri((secure.value() ? "wss" : "ws"), host.value(), port.value().intValue(), "/supplier");
			} catch (URISyntaxException e1) {
				try {
					return createUri((secure.defaultValue() ? "wss" : "ws"), host.defaultValue(), port.defaultValue().intValue(), "/supplier");
				} catch (URISyntaxException e2) {
					String paths = StringUtils.join(new String[] { secure.getPath(), host.getPath(), port.getPath() }, ", ");
					throw new IllegalStateException("A URI for the config values at paths (" + paths + ") in the default configuration file "
							+ "could not be created! The default configuration must contain valid values.", e2);
				}
			}
		}
		
		private static URI createUri(String protocol, String host, int port, String path) throws URISyntaxException {
			return new URI(protocol, null, host, port, path, null, null);
		}
		
	}
	
}
