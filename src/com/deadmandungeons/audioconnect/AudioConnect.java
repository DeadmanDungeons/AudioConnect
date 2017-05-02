package com.deadmandungeons.audioconnect;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import com.deadmandungeons.audioconnect.AudioConnectClient.PlayerAudioDataWriter;
import com.deadmandungeons.audioconnect.AudioConnectConfig.AudioTrackSettings;
import com.deadmandungeons.audioconnect.command.CommandHandler;
import com.deadmandungeons.audioconnect.flags.AudioDelay;
import com.deadmandungeons.audioconnect.flags.AudioDelayFlag;
import com.deadmandungeons.audioconnect.flags.AudioTrack;
import com.deadmandungeons.audioconnect.flags.AudioTrackFlag;
import com.deadmandungeons.audioconnect.messages.AudioMessage;
import com.deadmandungeons.audioconnect.messages.AudioMessage.Range;
import com.deadmandungeons.connect.commons.ConnectUtils;
import com.deadmandungeons.connect.commons.Messenger.Message;
import com.deadmandungeons.connect.commons.Result;
import com.deadmandungeons.deadmanplugin.Conversion.Converter;
import com.deadmandungeons.deadmanplugin.DeadmanPlugin;
import com.deadmandungeons.deadmanplugin.Messenger;
import com.deadmandungeons.deadmanplugin.filedata.DeadmanConfig;
import com.deadmandungeons.deadmanplugin.filedata.PluginFile;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.util.Locations;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.SetFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

/**
 * The main plugin class.<br>
 * The instance of this class can be obtained by {@link #getInstance()}
 * @author Jon
 */
public final class AudioConnect extends DeadmanPlugin {
	
	private static final String TRACKING_METADATA = "audio-tracking-data";
	private static final String GLOBAL_REGION_ID = "__global__";
	private static final int REGION_CHECK_DELAY = 3000;
	
	private final Config config = new Config();
	private final AudioList audioList = new AudioList(getLogger());
	
	private Messenger messenger;
	private WorldGuardPlugin worldGuard;
	private SetFlag<AudioTrack> audioFlag;
	private SetFlag<AudioDelay> audioDelayFlag;
	
	private AudioConnectClient client;
	
	/**
	 * @return the AudioConnect plugin instance
	 */
	public static AudioConnect getInstance() {
		return getDeadmanPlugin(AudioConnect.class);
	}
	
	
	@Override
	protected void onPluginLoad() {
		PluginFile langFile = PluginFile.creator(this, LANG_DIRECTORY + "english.yml").defaultFile("english.yml").create();
		messenger = new Messenger(this, langFile);
		
		worldGuard = (WorldGuardPlugin) Bukkit.getPluginManager().getPlugin("WorldGuard");
		try {
			worldGuard.getClass().getMethod("getFlagRegistry");
			
			// WorldGuard version is 6.2 or above
			audioFlag = new SetFlag<>("audio", AudioTrackFlag.create());
			audioDelayFlag = new SetFlag<>("audio-delay", AudioDelayFlag.create());
			
			worldGuard.getFlagRegistry().register(audioFlag);
			worldGuard.getFlagRegistry().register(audioDelayFlag);
		} catch (NoSuchMethodException e) {
			String version = worldGuard.getDescription().getVersion();
			getLogger().info("Detected an older version of WorldGuard (" + version + "). Attempting to make AudioConnect compatible...");
			
			audioFlag = new SetFlag<>("audio", AudioTrackFlag.createLegacy());
			audioDelayFlag = new SetFlag<>("audio-delay", AudioDelayFlag.createLegacy());
			
			try {
				Flag<?>[] flagsList = DefaultFlag.flagsList;
				
				Field flagsListField = DefaultFlag.class.getField("flagsList");
				Field modifiersField = Field.class.getDeclaredField("modifiers");
				modifiersField.setAccessible(true);
				modifiersField.setInt(flagsListField, flagsListField.getModifiers() & ~Modifier.FINAL);
				
				Flag<?>[] newFlagsList = new Flag<?>[flagsList.length + 2];
				System.arraycopy(flagsList, 0, newFlagsList, 0, flagsList.length);
				newFlagsList[flagsList.length] = audioFlag;
				newFlagsList[flagsList.length + 1] = audioDelayFlag;
				
				flagsListField.set(null, newFlagsList);
				
				getLogger().info("Successfully adjusted for compatibility with the current WorldGuard version");
			} catch (Exception e2) {
				getLogger().log(Level.SEVERE, "Failed to inject audio flags into WorldGuard. Consider upgrading WorldGuard to v6.2 or higher", e2);
			}
		}
	}
	
	@Override
	protected void onPluginEnable() {
		setConfig(config);
		
		getCommand("ac").setExecutor(new CommandHandler(this, messenger, config.getCommandCooldown()));
		
		Bukkit.getScheduler().runTaskTimer(this, new ConnectAnouncement(), 0, config.getAnnounceFrequency() * 20);
		
		client = new AudioConnectClient(this, config, audioList, new PlayerAudioTracker());
		
		if (config.validate()) {
			client.connect();
		}
	}
	
	@Override
	protected void onPluginDisable() {
		client.shutdown().awaitUninterruptibly();
	}
	
	
	/**
	 * @return the AudioConnectConfig instance containing parsed plugin configuration values
	 */
	public AudioConnectConfig getConfiguration() {
		return config;
	}
	
	/**
	 * @return the AudioList instance containing the available audio IDs for the configured account
	 */
	public AudioList getAudioList() {
		return audioList;
	}
	
	/**
	 * @return the AudioConnectClient instance responsible for interfacing with the configured AudioConnect server
	 */
	public AudioConnectClient getClient() {
		return client;
	}
	
	/**
	 * @return the Messenger instance responsible for obtaining and sending plugin messages to the user
	 */
	public Messenger getMessenger() {
		return messenger;
	}
	
	/**
	 * @param playerId the UUID of the player to obtain the connect URL for
	 * @return the web client URL to connect as the player with the given UUID
	 */
	public String getPlayerConnectUrl(UUID playerId) {
		String webappUrl = config.getConnectionWebappUrl().toString();
		String serverId = ConnectUtils.encodeUuidBase64(config.getConnectionServerId());
		String encodedPlayerId = ConnectUtils.encodeUuidBase64(playerId);
		
		return webappUrl + "/connect?s=" + serverId + "&u=" + encodedPlayerId;
	}
	
	
	private class ConnectAnouncement implements Runnable {
		
		@Override
		public void run() {
			if (!client.isConnected()) {
				return;
			}
			
			for (Player player : Bukkit.getOnlinePlayers()) {
				if (!client.isPlayerConnected(player.getUniqueId())) {
					String announcement = messenger.getMessage("misc.announcement", true);
					String connectUrl = getPlayerConnectUrl(player.getUniqueId());
					String connectDetails = messenger.getMessage("misc.connect-details", true, connectUrl);
					
					messenger.sendMessage(player, "misc.top-bar");
					player.sendMessage(announcement + connectDetails);
					messenger.sendMessage(player, "misc.bottom-bar");
				}
			}
		}
		
	}
	
	private class PlayerAudioTracker implements PlayerAudioDataWriter {
		
		private final List<Message> messageBuffer = new ArrayList<>();
		
		@Override
		public void writeData(Player player) {
			Location loc = player.getLocation();
			PreviousTrackingData previousData = getPreviousData(player);
			
			long now = System.currentTimeMillis();
			if (previousData.timestamp + REGION_CHECK_DELAY > now) {
				return;
			}
			if (!Locations.isDifferentBlock(previousData.location, loc)) {
				return;
			}
			
			if (writeAudioMessages(player, previousData, messageBuffer, false) > 0) {
				previousData.timestamp = now;
			}
			
			previousData.location = loc;
		}
		
		@Override
		public void flushData() {
			if (messageBuffer.size() > 0) {
				client.writeAndFlush(messageBuffer.toArray(new AudioMessage[messageBuffer.size()]));
				messageBuffer.clear();
			}
		}
		
		@Override
		public void writeAudioMessages(Player player, List<Message> messageBuffer) {
			writeAudioMessages(player, getPreviousData(player), messageBuffer, true);
		}
		
		private int writeAudioMessages(Player player, PreviousTrackingData previousData, List<Message> messageBuffer, boolean ignoreEquals) {
			Map<String, Set<String>> audioIdsByTrack = null;
			Map<String, Range> audioDelayByTrack = null;
			
			Location loc = player.getLocation();
			RegionManager regionManager = worldGuard.getRegionManager(loc.getWorld());
			ApplicableRegionSet regions = regionManager.getApplicableRegions(loc);
			Iterator<ProtectedRegion> iterator = regions.iterator();
			
			int audioTrackPriority = 0, audioDelayPriority = 0;
			ProtectedRegion globalRegion = regionManager.getRegion(GLOBAL_REGION_ID);
			for (int i = 0; i <= regions.size(); i++) {
				if (i == 0 && globalRegion == null) {
					continue;
				}
				
				ProtectedRegion region = (i == 0 ? globalRegion : iterator.next());
				
				Set<AudioTrack> audioTracks = region.getFlag(audioFlag);
				if (audioTracks != null && region.getPriority() >= audioTrackPriority) {
					if (region.getPriority() > audioTrackPriority) {
						audioTrackPriority = region.getPriority();
						if (audioIdsByTrack != null) {
							audioIdsByTrack.clear();
						}
					}
					
					if (audioIdsByTrack == null) {
						audioIdsByTrack = new HashMap<>();
					}
					for (AudioTrack audioTrack : audioTracks) {
						if ((audioTrack.getDayTime() == null || audioTrack.getDayTime().check(loc.getWorld()))
								&& audioList.contains(audioTrack.getAudioId())) {
							Set<String> audioIds = audioIdsByTrack.get(audioTrack.getTrackId());
							if (audioIds == null) {
								audioIds = new HashSet<>();
								audioIdsByTrack.put(audioTrack.getTrackId(), audioIds);
							}
							
							audioIds.add(audioTrack.getAudioId());
						}
					}
				}
				
				Set<AudioDelay> audioDelays = region.getFlag(audioDelayFlag);
				if (audioDelays != null && region.getPriority() >= audioDelayPriority) {
					if (region.getPriority() > audioDelayPriority) {
						audioDelayPriority = region.getPriority();
						if (audioDelayByTrack != null) {
							audioDelayByTrack.clear();
						}
					}
					
					if (audioDelayByTrack == null) {
						audioDelayByTrack = new HashMap<>();
					}
					for (AudioDelay audioDelay : audioDelays) {
						audioDelayByTrack.put(audioDelay.getTrackId(), audioDelay.getDelayTime());
					}
				}
			}
			
			if (previousData.audioIdsByTrack == null && previousData.audioDelayByTrack == null
					&& (audioIdsByTrack == null && audioDelayByTrack == null)) {
				return 0;
			}
			
			Set<String> trackIds = new HashSet<>();
			addAllKeys(audioIdsByTrack, trackIds);
			addAllKeys(audioDelayByTrack, trackIds);
			addAllKeys(previousData.audioIdsByTrack, trackIds);
			addAllKeys(previousData.audioDelayByTrack, trackIds);
			
			int writeCount = 0;
			for (String trackId : trackIds) {
				if (trackId != null && !config.getAudioTracks().containsKey(trackId)) {
					continue;
				}
				Set<String> audioIds = getValueOrNull(audioIdsByTrack, trackId);
				Set<String> previousAudioIds = getValueOrNull(previousData.audioIdsByTrack, trackId);
				Range audioDelay = getValueOrNull(audioDelayByTrack, trackId);
				Range previousAudioDelay = getValueOrNull(previousData.audioDelayByTrack, trackId);
				if (!ignoreEquals && Objects.equals(audioIds, previousAudioIds) && Objects.equals(audioDelay, previousAudioDelay)) {
					continue;
				}
				
				AudioMessage.Builder messageBuilder = AudioMessage.builder(player.getUniqueId());
				if (trackId != null) {
					messageBuilder.track(trackId);
				}
				if (audioIds != null) {
					for (String audioId : audioIds) {
						messageBuilder.audio(audioId);
					}
				}
				if (audioDelay != null) {
					messageBuilder.delayRange(audioDelay);
				}
				messageBuffer.add(messageBuilder.build());
				writeCount++;
			}
			
			previousData.audioIdsByTrack = audioIdsByTrack;
			previousData.audioDelayByTrack = audioDelayByTrack;
			
			return writeCount;
		}
		
		private <T> void addAllKeys(Map<T, ?> from, Set<T> to) {
			if (from != null && from.size() > 0) {
				to.addAll(from.keySet());
			}
		}
		
		private <T> T getValueOrNull(Map<String, T> map, String key) {
			return map != null ? map.get(key) : null;
		}
		
		private PreviousTrackingData getPreviousData(Player player) {
			PreviousTrackingData previousData = getMetadata(player, TRACKING_METADATA, PreviousTrackingData.class);
			if (previousData == null) {
				previousData = new PreviousTrackingData(player.getLocation());
				player.setMetadata(TRACKING_METADATA, new FixedMetadataValue(AudioConnect.this, previousData));
			}
			return previousData;
		}
		
	}
	
	private static class PreviousTrackingData {
		
		private long timestamp;
		private Location location;
		private Map<String, Set<String>> audioIdsByTrack;
		private Map<String, Range> audioDelayByTrack;
		
		private PreviousTrackingData(Location location) {
			this.location = location;
		}
		
	}
	
	
	private static class Config extends DeadmanConfig implements AudioConnectConfig {
		
		private static final String INVALID_REQUIRED_PROPERTY = "The required %s config property is missing or invalid! Client cannot be started...";
		private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{2,16}$");
		
		static {
			getInstance().getConversion().registerConverter(AudioTrackSettings.class, new AudioTrackSettingsConverter());
		}
		
		private final ConfigEntry<String> connectionUserId = entry(String.class, "connection.user-id");
		private final ConfigEntry<String> connectionUserPassword = entry(String.class, "connection.user-password");
		private final ConfigEntry<String> connectionServerId = entry(String.class, "connection.server-id");
		private final ConfigEntry<Boolean> connectionSecure = entry(Boolean.class, "connection.endpoint.secure");
		private final ConfigEntry<String> connectionHost = entry(String.class, "connection.endpoint.host");
		private final ConfigEntry<Number> connectionWebsocketPort = entry(Number.class, "connection.endpoint.websocket-port");
		private final ConfigEntry<Number> connectionWebappPort = entry(Number.class, "connection.endpoint.webapp-port");
		private final ConfigEntry<String> connectionWebappPath = entry(String.class, "connection.endpoint.webapp-path");
		private final ConfigEntry<Number> reconnectInterval = entry(Number.class, "reconnect.interval");
		private final ConfigEntry<Number> reconnectMaxInterval = entry(Number.class, "reconnect.max-interval");
		private final ConfigEntry<Number> reconnectDelay = entry(Number.class, "reconnect.delay");
		private final ConfigEntry<Number> reconnectMaxAttempts = entry(Number.class, "reconnect.max-attempts");
		private final ConfigEntry<Number> commandCooldown = entry(Number.class, "options.command-cooldown");
		private final ConfigEntry<Number> announceFrequency = entry(Number.class, "options.announce-frequency");
		private final MapConfigEntry<String, AudioTrackSettings> audioTracks = mapEntry(AudioTrackSettings.class, "audio-tracks");
		
		
		private volatile UUID userId;
		private volatile UUID serverId;
		private volatile URI websocketUri;
		private volatile URL webappUrl;
		
		@Override
		public synchronized void loadEntries(DeadmanPlugin plugin) throws IllegalStateException {
			super.loadEntries(plugin);
			userId = null;
			serverId = null;
			websocketUri = null;
			webappUrl = null;
		}
		
		
		@Override
		public synchronized boolean validate() {
			if (getConnectionUserId() == null) {
				getInstance().getLogger().severe(String.format(INVALID_REQUIRED_PROPERTY, connectionUserId.getPath()));
				return false;
			}
			if (StringUtils.isEmpty(connectionUserPassword.value())) {
				getInstance().getLogger().severe(String.format(INVALID_REQUIRED_PROPERTY, connectionUserPassword.getPath()));
				return false;
			}
			if (getConnectionServerId() == null) {
				getInstance().getLogger().severe(String.format(INVALID_REQUIRED_PROPERTY, connectionServerId.getPath()));
				return false;
			}
			if (audioTracks.value().isEmpty()) {
				getInstance().getLogger().severe(String.format(INVALID_REQUIRED_PROPERTY, audioTracks.getPath()));
				return false;
			}
			return true;
		}
		
		@Override
		public synchronized UUID getConnectionUserId() {
			if (userId == null) {
				String userIdStr = connectionUserId.value();
				if (!StringUtils.isEmpty(userIdStr)) {
					userId = ConnectUtils.parseId(userIdStr);
					if (userId == null && USERNAME_PATTERN.matcher(userIdStr).matches()) {
						userId = Bukkit.getOfflinePlayer(userIdStr).getUniqueId();
					}
				}
			}
			return userId;
		}
		
		@Override
		public synchronized String getConnectionUserPassword() {
			return connectionUserPassword.value();
		}
		
		@Override
		public synchronized UUID getConnectionServerId() {
			if (serverId == null) {
				String serveridStr = connectionServerId.value();
				if (!StringUtils.isEmpty(serveridStr)) {
					serverId = ConnectUtils.parseId(serveridStr);
				}
			}
			return serverId;
		}
		
		@Override
		public synchronized URI getConnectionWebsocketUri() {
			if (websocketUri == null) {
				websocketUri = createWebsocketUri(connectionSecure, connectionHost, connectionWebsocketPort);
			}
			return websocketUri;
		}
		
		@Override
		public synchronized URL getConnectionWebappUrl() {
			if (webappUrl == null) {
				webappUrl = createWebappUrl(connectionSecure, connectionHost, connectionWebappPort, connectionWebappPath);
			}
			return webappUrl;
		}
		
		@Override
		public synchronized boolean isConnectionSecure() {
			return connectionSecure.value();
		}
		
		@Override
		public synchronized String getConnectionHost() {
			return connectionHost.value();
		}
		
		@Override
		public synchronized int getConnectionWebsocketPort() {
			return connectionWebsocketPort.value().intValue();
		}
		
		@Override
		public int getConnectionWebappPort() {
			return connectionWebappPort.value().intValue();
		}
		
		@Override
		public synchronized String getConnectionWebappPath() {
			return connectionWebappPath.value();
		}
		
		@Override
		public synchronized int getReconnectInterval() {
			return reconnectInterval.value().intValue();
		}
		
		@Override
		public synchronized int getReconnectMaxInterval() {
			return reconnectMaxInterval.value().intValue();
		}
		
		@Override
		public synchronized double getReconnectDelay() {
			return reconnectDelay.value().doubleValue();
		}
		
		@Override
		public synchronized int getReconnectMaxAttempts() {
			return reconnectMaxAttempts.value().intValue();
		}
		
		@Override
		public synchronized int getCommandCooldown() {
			return commandCooldown.value().intValue();
		}
		
		@Override
		public synchronized int getAnnounceFrequency() {
			return announceFrequency.value().intValue();
		}
		
		@Override
		public synchronized Map<String, AudioTrackSettings> getAudioTracks() {
			return audioTracks.value();
		}
		
		private static URI createWebsocketUri(ConfigEntry<Boolean> secure, ConfigEntry<String> host, ConfigEntry<Number> port) {
			try {
				return createUri((secure.value() ? "wss" : "ws"), host.value(), port.value().intValue(), "/supplier");
			} catch (URISyntaxException e1) {
				try {
					URI uri = createUri((secure.defaultValue() ? "wss" : "ws"), host.defaultValue(), port.defaultValue().intValue(), "/supplier");
					getInstance().getLogger().warning("Invalid host syntax at " + host.getPath() + " in config. Using default URI " + uri);
					return uri;
				} catch (URISyntaxException e2) {
					String paths = StringUtils.join(new String[] { secure.getPath(), host.getPath(), port.getPath() }, ", ");
					throw new IllegalStateException("A URI for the config values at paths (" + paths + ") in the default configuration file "
							+ "could not be created! The default configuration must contain valid values.", e2);
				}
			}
		}
		
		private static URL createWebappUrl(ConfigEntry<Boolean> secure, ConfigEntry<String> host, ConfigEntry<Number> port,
				ConfigEntry<String> path) {
			try {
				int validPort = (port.value().intValue() != 80 ? port.value().intValue() : -1);
				String validPath = path.value().replaceAll("^([^/])", "/$1").replaceAll("/$", "");
				return createUri((secure.value() ? "https" : "http"), host.value(), validPort, validPath).toURL();
			} catch (MalformedURLException | URISyntaxException e1) {
				try {
					int validPort = (port.defaultValue().intValue() != 80 ? port.defaultValue().intValue() : -1);
					String validPath = path.defaultValue().replaceAll("^([^/])", "/$1").replaceAll("/$", "");
					URL url = createUri((secure.defaultValue() ? "https" : "http"), host.defaultValue(), validPort, validPath).toURL();
					getInstance().getLogger().warning("Invalid host syntax at " + host.getPath() + " in config. Using default URL " + url);
					return url;
				} catch (MalformedURLException | URISyntaxException e2) {
					String paths = StringUtils.join(new String[] { secure.getPath(), host.getPath(), path.getPath() }, ", ");
					throw new IllegalStateException("A URL for the config values at paths (" + paths + ") in the default configuration file "
							+ "could not be created! The default configuration must contain valid values.", e2);
				}
			}
		}
		
		private static URI createUri(String protocol, String host, int port, String path) throws URISyntaxException {
			return new URI(protocol, null, host, port, path, null, null);
		}
		
	}
	
	private static class AudioTrackSettingsConverter implements Converter<AudioTrackSettings> {
		
		@Override
		public AudioTrackSettings convert(Object object) {
			if (object instanceof ConfigurationSection) {
				ConfigurationSection section = (ConfigurationSection) object;
				String trackId = section.getName();
				Result<String> trackIdValidation = AudioMessage.validateIdentifier(trackId);
				if (trackIdValidation.isSuccess()) {
					boolean defaultTrack = section.getBoolean("default");
					boolean repeating = section.getBoolean("repeating");
					boolean random = section.getBoolean("random");
					boolean fading = section.getBoolean("fading");
					return new AudioTrackSettings(defaultTrack, repeating, random, fading);
				}
				String warning = "The configured track ID '" + trackId + "' is invalid. " + trackIdValidation.getFailReason();
				getInstance().getLogger().warning(warning);
			}
			return null;
		}
	}
	
}
