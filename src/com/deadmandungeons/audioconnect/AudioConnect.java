package com.deadmandungeons.audioconnect;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
import com.sk89q.worldguard.protection.flags.SetFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;


public final class AudioConnect extends DeadmanPlugin implements Listener {
	
	private static final String SPIGOT_USER_ID = "%%__USER__%%"; // Injected by spigot repository on download
	private static final String TRACKING_METADATA = "audio-tracking-data";
	private static final String GLOBAL_REGION_ID = "__global__";
	private static final int REGION_CHECK_DELAY = 3000;
	
	private final Config config = new Config();
	private final AudioList audioList = new AudioList(this);
	private final AudioConnectClient client = new AudioConnectClient(this, config, audioList, new PlayerAudioTracker());
	
	private final SetFlag<AudioTrack> audioFlag = new SetFlag<>("audio", new AudioTrackFlag(null));
	private final AudioDelayFlag audioDelayFlag = new AudioDelayFlag("audio-delay");
	
	private Messenger messenger;
	private WorldGuardPlugin worldGuard;
	
	public static AudioConnect getInstance() {
		return getDeadmanPlugin(AudioConnect.class);
	}
	
	
	@Override
	protected void onPluginLoad() throws Exception {
		PluginFile langFile = PluginFile.creator(this, LANG_DIRECTORY + "english.yml").defaultFile("english.yml").create();
		messenger = new Messenger(this, langFile);
		
		worldGuard = (WorldGuardPlugin) Bukkit.getPluginManager().getPlugin("WorldGuard");
		worldGuard.getFlagRegistry().register(audioFlag);
		worldGuard.getFlagRegistry().register(audioDelayFlag);
	}
	
	@Override
	protected void onPluginEnable() {
		setConfig(config);
		
		getServer().getPluginManager().registerEvents(this, this);
		getCommand("ac").setExecutor(new CommandHandler(this, messenger, config.getCommandCooldown()));
		
		if (!config.validate()) {
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
						if (client.isConnected() && !client.isPlayerConnected(player.getUniqueId())) {
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
		}, 0, config.getAnnounceFrequency() * 20);
		
		client.connect();
	}
	
	@Override
	protected void onPluginDisable() {
		client.disconnect();
	}
	
	
	public AudioConnectConfig getConfiguration() {
		return config;
	}
	
	public AudioList getAudioList() {
		return audioList;
	}
	
	public AudioConnectClient getClient() {
		return client;
	}
	
	public Messenger getMessenger() {
		return messenger;
	}
	
	
	@EventHandler
	private void onPlayerJoin(PlayerJoinEvent event) {
		client.notifyPlayerJoin(event.getPlayer());
	}
	
	@EventHandler
	private void onPlayerQuit(PlayerQuitEvent event) {
		client.notifyPlayerQuit(event.getPlayer());
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
								&& audioList.isAudioIdValid(audioTrack.getAudioId())) {
							Set<String> audioIds = audioIdsByTrack.get(audioTrack.getTrackId());
							if (audioIds == null) {
								audioIds = new HashSet<>();
								audioIdsByTrack.put(audioTrack.getTrackId(), audioIds);
							}
							
							audioIds.add(audioTrack.getAudioId());
						}
					}
				}
				
				AudioDelay audioDelay = region.getFlag(audioDelayFlag);
				if (audioDelay != null && region.getPriority() >= audioDelayPriority) {
					if (region.getPriority() > audioDelayPriority) {
						audioDelayPriority = region.getPriority();
						if (audioDelayByTrack != null) {
							audioDelayByTrack.clear();
						}
					}
					
					if (audioDelayByTrack == null) {
						audioDelayByTrack = new HashMap<>();
					}
					
					audioDelayByTrack.put(audioDelay.getTrackId(), audioDelay.getDelayRange());
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
		private final ConfigEntry<String> connectionHost = entry(String.class, "connection.endpoint.host");
		private final ConfigEntry<Number> connectionPort = entry(Number.class, "connection.endpoint.port");
		private final ConfigEntry<Boolean> connectionSecure = entry(Boolean.class, "connection.endpoint.secure");
		private final ConfigEntry<Number> reconnectInterval = entry(Number.class, "reconnect.interval");
		private final ConfigEntry<Number> reconnectMaxInterval = entry(Number.class, "reconnect.max-interval");
		private final ConfigEntry<Number> reconnectDelay = entry(Number.class, "reconnect.delay");
		private final ConfigEntry<Number> reconnectMaxAttempts = entry(Number.class, "reconnect.max-attempts");
		private final ConfigEntry<Number> commandCooldown = entry(Number.class, "options.command-cooldown");
		private final ConfigEntry<Number> announceFrequency = entry(Number.class, "options.announce-frequency");
		private final MapConfigEntry<String, AudioTrackSettings> audioTracks = mapEntry(AudioTrackSettings.class, "audio-tracks");
		
		
		private UUID userId;
		private URI connectionUri;
		
		@Override
		public void loadEntries(DeadmanPlugin plugin) throws IllegalStateException {
			super.loadEntries(plugin);
			userId = null;
			connectionUri = null;
		}
		
		
		@Override
		public boolean validate() {
			if (getConnectionUserId() == null) {
				getInstance().getLogger().severe(String.format(INVALID_REQUIRED_PROPERTY, connectionUserId.getPath()));
				return false;
			}
			if (StringUtils.isEmpty(connectionUserPassword.value())) {
				getInstance().getLogger().severe(String.format(INVALID_REQUIRED_PROPERTY, connectionUserPassword.getPath()));
				return false;
			}
			if (StringUtils.isEmpty(connectionServerId.value())) {
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
		public UUID getConnectionUserId() {
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
		public String getConnectionUserPassword() {
			return connectionUserPassword.value();
		}
		
		@Override
		public String getConnectionServerId() {
			return connectionServerId.value();
		}
		
		@Override
		public URI getConnectionUri() {
			if (connectionUri == null) {
				connectionUri = createConnectionUri(connectionSecure, connectionHost, connectionPort);
			}
			return connectionUri;
		}
		
		@Override
		public String getConnectionHost() {
			return connectionHost.value();
		}
		
		@Override
		public int getConnectionPort() {
			return connectionPort.value().intValue();
		}
		
		@Override
		public boolean isConnectionSecure() {
			return connectionSecure.value();
		}
		
		@Override
		public int getReconnectInterval() {
			return reconnectInterval.value().intValue();
		}
		
		@Override
		public int getReconnectMaxInterval() {
			return reconnectMaxInterval.value().intValue();
		}
		
		@Override
		public double getReconnectDelay() {
			return reconnectDelay.value().doubleValue();
		}
		
		@Override
		public int getReconnectMaxAttempts() {
			return reconnectMaxAttempts.value().intValue();
		}
		
		@Override
		public int getCommandCooldown() {
			return commandCooldown.value().intValue();
		}
		
		@Override
		public int getAnnounceFrequency() {
			return announceFrequency.value().intValue();
		}
		
		@Override
		public Map<String, AudioTrackSettings> getAudioTracks() {
			return audioTracks.value();
		}
		
		private static URI createConnectionUri(ConfigEntry<Boolean> secure, ConfigEntry<String> host, ConfigEntry<Number> port) {
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
