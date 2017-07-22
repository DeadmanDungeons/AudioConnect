package com.deadmandungeons.audioconnect;

import com.deadmandungeons.audioconnect.AudioConnectClient.PlayerAudioDataWriter;
import com.deadmandungeons.audioconnect.command.CommandHandler;
import com.deadmandungeons.audioconnect.flags.AudioDelay;
import com.deadmandungeons.audioconnect.flags.AudioDelayFlag;
import com.deadmandungeons.audioconnect.flags.AudioTrack;
import com.deadmandungeons.audioconnect.flags.AudioTrackFlag;
import com.deadmandungeons.audioconnect.messages.AudioMessage;
import com.deadmandungeons.audioconnect.messages.AudioMessage.Range;
import com.deadmandungeons.connect.commons.ConnectUtils;
import com.deadmandungeons.connect.commons.Messenger.Message;
import com.deadmandungeons.deadmanplugin.DeadmanPlugin;
import com.deadmandungeons.deadmanplugin.Messenger;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.util.Locations;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.SetFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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

/**
 * The main plugin class.<br>
 * The instance of this class can be obtained by {@link #getInstance()}
 * @author Jon
 */
public final class AudioConnect extends DeadmanPlugin {

    private final AudioConnectConfig config = new AudioConnectConfig();
    private final AudioList audioList = new AudioList(getLogger(), new AudioUpdateHandler());
    private final boolean spigot;

    private WorldGuardPlugin worldGuard;
    private SetFlag<AudioTrack> audioFlag;
    private SetFlag<AudioDelay> audioDelayFlag;

    private Messenger messenger;
    private AudioConnectClient client;

    /**
     * @return the AudioConnect plugin instance
     */
    public static AudioConnect getInstance() {
        return getDeadmanPlugin(AudioConnect.class);
    }

    public AudioConnect() {
        boolean spigot = true;
        try {
            // Check if the server is running on Spigot and thus the Spigot API is available
            Class.forName("org.spigotmc.SpigotConfig");
        } catch (ClassNotFoundException e) {
            spigot = false;
        }
        this.spigot = spigot;
    }

    @Override
    protected void onPluginLoad() {
        worldGuard = (WorldGuardPlugin) Bukkit.getPluginManager().getPlugin("WorldGuard");
        try {
            worldGuard.getClass().getMethod("getFlagRegistry");

            // WorldGuard version is 6.2 or above
            audioFlag = new SetFlag<>("audio", new AudioTrackFlag());
            audioDelayFlag = new SetFlag<>("audio-delay", new AudioDelayFlag());

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

        messenger = new Messenger(this, config.getLocaleFile());

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

    @Override
    public void reloadConfig() {
        super.reloadConfig();

        if (messenger != null && !messenger.getLangFile().equals(config.getLocaleFile())) {
            messenger.setLangFile(config.getLocaleFile());
        }
    }

    /**
     * Useful in checking if the spigot API is available.
     * @return <code>true</code> if the server is running on Spigot, and <code>false</code> otherwise
     */
    public boolean isSpigot() {
        return spigot;
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
     * @return the custom <code>audio</code> WorldGuard flag instance
     */
    public SetFlag<AudioTrack> getAudioFlag() {
        return audioFlag;
    }

    /**
     * @return the custom <code>audio-delay</code> WorldGuard flag instance
     */
    public SetFlag<AudioDelay> getAudioDelayFlag() {
        return audioDelayFlag;
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


    private class AudioUpdateHandler implements AudioList.UpdateHandler {

        @Override
        public void deleteAll(Set<String> audioIds) {
            List<RegionManager> regionManagers = WorldGuardPlugin.inst().getRegionContainer().getLoaded();
            for (RegionManager regionManager : regionManagers) {
                for (ProtectedRegion region : regionManager.getRegions().values()) {
                    Set<AudioTrack> audioTracks = region.getFlag(audioFlag);
                    if (audioTracks != null) {
                        boolean removedAudio = false;
                        Set<AudioTrack> newAudioTracks = null;
                        for (AudioTrack audioTrack : audioTracks) {
                            if (!audioIds.contains(audioTrack.getAudioId())) {
                                if (newAudioTracks == null) {
                                    newAudioTracks = new HashSet<>(audioTracks.size());
                                }
                                newAudioTracks.add(audioTrack);
                            } else {
                                removedAudio = true;
                            }
                        }
                        if (removedAudio) {
                            region.setFlag(audioFlag, newAudioTracks);
                        }
                    }
                }
            }
            for (RegionManager regionManager : regionManagers) {
                try {
                    regionManager.saveChanges();
                } catch (StorageException e) {
                    getLogger().log(Level.WARNING, "Failed to save '" + regionManager + "' WorldGuard region changes from audio deletion");
                }
            }
            for (String audioId : audioIds) {
                getLogger().info("Removed audio '" + audioId + "' from all WorldGuard regions.");
            }
        }

        @Override
        public void replace(String audioId, String newAudioId) {
            List<RegionManager> regionManagers = WorldGuardPlugin.inst().getRegionContainer().getLoaded();
            for (RegionManager regionManager : regionManagers) {
                for (ProtectedRegion region : regionManager.getRegions().values()) {
                    Set<AudioTrack> audioTracks = region.getFlag(audioFlag);
                    if (audioTracks != null) {
                        boolean replacedAudio = false;
                        Set<AudioTrack> newAudioTracks = new HashSet<>(audioTracks.size());
                        for (AudioTrack audioTrack : audioTracks) {
                            if (audioTrack.getAudioId().equals(audioId)) {
                                newAudioTracks.add(new AudioTrack(newAudioId, audioTrack.getTrackId(), audioTrack.getDayTime()));
                                replacedAudio = true;
                            } else {
                                newAudioTracks.add(audioTrack);
                            }
                        }
                        if (replacedAudio) {
                            region.setFlag(audioFlag, newAudioTracks);
                        }
                    }
                }
            }
            for (RegionManager regionManager : regionManagers) {
                try {
                    regionManager.saveChanges();
                } catch (StorageException e) {
                    getLogger().log(Level.WARNING, "Failed to save '" + regionManager + "' WorldGuard region changes from audio replacement");
                }
            }
            getLogger().info("Replaced audio '" + audioId + "' with '" + newAudioId + "' in all occurring WorldGuard regions.");
        }

    }


    private class PlayerAudioTracker implements PlayerAudioDataWriter {

        private static final String TRACKING_METADATA = "audio-tracking-data";
        private static final String GLOBAL_REGION_ID = "__global__";
        private static final int REGION_CHECK_DELAY = 3000;

        private final List<Message> messageBuffer = new ArrayList<>();

        @Override
        public void writeData(Player player) {
            Location loc = player.getLocation();
            TrackingData trackingData = getTrackingData(player);

            long now = System.currentTimeMillis();
            if (trackingData.timestamp + REGION_CHECK_DELAY > now) {
                return;
            }
            if (!Locations.isDifferentBlock(trackingData.location, loc)) {
                return;
            }

            if (writeAudioMessages(player, trackingData, messageBuffer, false) > 0) {
                trackingData.timestamp = now;
            }

            trackingData.location = loc;
        }

        @Override
        public void flushData() {
            if (messageBuffer.size() > 0) {
                client.writeAndFlush(messageBuffer.toArray(new Message[messageBuffer.size()]));
                messageBuffer.clear();
            }
        }

        @Override
        public void writeAudioMessages(Player player, List<Message> messageBuffer) {
            writeAudioMessages(player, getTrackingData(player), messageBuffer, true);
        }

        private int writeAudioMessages(Player player, TrackingData trackingData, List<Message> messageBuffer, boolean ignoreEquals) {
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
                        if ((audioTrack.getDayTime() == null || audioTrack.getDayTime().check(loc.getWorld())) &&
                                audioList.contains(audioTrack.getAudioId())) {
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

            if (trackingData.audioIdsByTrack == null && trackingData.audioDelayByTrack == null &&
                    (audioIdsByTrack == null && audioDelayByTrack == null)) {
                return 0;
            }

            Set<String> trackIds = new HashSet<>();
            addAllKeys(audioIdsByTrack, trackIds);
            addAllKeys(audioDelayByTrack, trackIds);
            addAllKeys(trackingData.audioIdsByTrack, trackIds);
            addAllKeys(trackingData.audioDelayByTrack, trackIds);

            int writeCount = 0;
            for (String trackId : trackIds) {
                if (trackId != null && !config.getAudioTracks().containsKey(trackId)) {
                    continue;
                }
                Set<String> audioIds = getValueOrNull(audioIdsByTrack, trackId);
                Set<String> previousAudioIds = getValueOrNull(trackingData.audioIdsByTrack, trackId);
                Range audioDelay = getValueOrNull(audioDelayByTrack, trackId);
                Range previousAudioDelay = getValueOrNull(trackingData.audioDelayByTrack, trackId);
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

            trackingData.audioIdsByTrack = audioIdsByTrack;
            trackingData.audioDelayByTrack = audioDelayByTrack;

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

        private TrackingData getTrackingData(Player player) {
            TrackingData trackingData = getMetadata(player, TRACKING_METADATA, TrackingData.class);
            if (trackingData == null) {
                trackingData = new TrackingData(player.getLocation());
                player.setMetadata(TRACKING_METADATA, new FixedMetadataValue(AudioConnect.this, trackingData));
            }
            return trackingData;
        }

    }

    private static class TrackingData {

        private long timestamp;
        private Location location;
        private Map<String, Set<String>> audioIdsByTrack;
        private Map<String, Range> audioDelayByTrack;

        private TrackingData(Location location) {
            this.location = location;
        }

    }

}
