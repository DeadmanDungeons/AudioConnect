package com.deadmandungeons.audioconnect;

import com.deadmandungeons.audioconnect.AudioConnectClient.PlayerAudioDataWriter;
import com.deadmandungeons.audioconnect.command.CommandHandler;
import com.deadmandungeons.audioconnect.compat.WorldGuardAdapter;
import com.deadmandungeons.audioconnect.flags.AudioDelay;
import com.deadmandungeons.audioconnect.flags.AudioDelayFlag;
import com.deadmandungeons.audioconnect.flags.AudioTrack;
import com.deadmandungeons.audioconnect.flags.AudioTrackFlag;
import com.deadmandungeons.audioconnect.messages.AudioMessage;
import com.deadmandungeons.audioconnect.messages.AudioMessage.Range;
import com.deadmandungeons.connect.commons.ConnectUtils;
import com.deadmandungeons.connect.commons.messenger.messages.Message;
import com.deadmandungeons.deadmanplugin.DeadmanPlugin;
import com.deadmandungeons.deadmanplugin.Messenger;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.SetFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

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
    private final boolean spigot = ConnectUtils.checkClass("org.spigotmc.SpigotConfig");

    private WorldGuardAdapter worldGuardAdapter;
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

    @Override
    protected void onPluginLoad() {
        worldGuardAdapter = WorldGuardAdapter.getInstance();
        audioFlag = worldGuardAdapter.initSetFlag("audio", new AudioTrackFlag());
        audioDelayFlag = worldGuardAdapter.initSetFlag("audio-delay", new AudioDelayFlag());
        worldGuardAdapter.installFlags(audioFlag, audioDelayFlag);
    }

    @Override
    protected void onPluginEnable() {
        worldGuardAdapter.initRegionAdapter();
        getLogger().info("Successfully initialized WorldGuard adapter");

        setConfig(config);

        messenger = new Messenger(this, config.getLocaleFile());

        getCommand("ac").setExecutor(new CommandHandler(this, messenger, config.getCommandCooldown()));

        Bukkit.getScheduler().runTaskTimer(this, new ConnectAnnouncement(), 0, config.getAnnounceFrequency() * 20);

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
     * @return the WorldGuardAdapter instance for the current WorldGuard plugin installation
     */
    public WorldGuardAdapter getWorldGuardAdapter() {
        return worldGuardAdapter;
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


    private class ConnectAnnouncement implements Runnable {

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
            List<RegionManager> regionManagers = worldGuardAdapter.getRegionManagers();
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
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "Failed to save '" + regionManager + "' WorldGuard region changes from audio deletion", e);
                }
            }
            for (String audioId : audioIds) {
                getLogger().info("Removed audio '" + audioId + "' from all WorldGuard regions.");
            }
        }

        @Override
        public void replace(String audioId, String newAudioId) {
            List<RegionManager> regionManagers = worldGuardAdapter.getRegionManagers();
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
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "Failed to save '" + regionManager + "' WorldGuard region changes from audio replacement", e);
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
            if (!isDifferentBlock(trackingData.location, loc)) {
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
            String defaultTrackId = config.getDefaultTrackId();

            Location loc = player.getLocation();
            RegionManager regionManager = worldGuardAdapter.getRegionManager(loc.getWorld());
            ApplicableRegionSet regions = worldGuardAdapter.getApplicableRegions(regionManager, loc);
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
                            // Specify default track ID rather than sending null
                            String trackId = (audioTrack.getTrackId() != null ? audioTrack.getTrackId() : defaultTrackId);
                            Set<String> audioIds = audioIdsByTrack.get(trackId);
                            if (audioIds == null) {
                                audioIds = new HashSet<>();
                                audioIdsByTrack.put(trackId, audioIds);
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
                        // Specify default track ID rather than sending null
                        String trackId = (audioDelay.getTrackId() != null ? audioDelay.getTrackId() : defaultTrackId);
                        audioDelayByTrack.put(trackId, audioDelay.getDelayTime());
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

        public boolean isDifferentBlock(Location a, Location b) {
            return (a.getBlockX() != b.getBlockX()) || (a.getBlockY() != b.getBlockY()) || (a.getBlockZ() != b.getBlockZ());
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
