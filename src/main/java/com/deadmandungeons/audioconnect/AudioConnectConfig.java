package com.deadmandungeons.audioconnect;

import com.deadmandungeons.audioconnect.messages.AudioMessage;
import com.deadmandungeons.connect.commons.ConnectUtils;
import com.deadmandungeons.deadmanplugin.Conversion;
import com.deadmandungeons.deadmanplugin.DeadmanPlugin;
import com.deadmandungeons.deadmanplugin.filedata.DeadmanConfig;
import com.deadmandungeons.deadmanplugin.filedata.PluginFile;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class AudioConnectConfig extends DeadmanConfig {

    private static final String INVALID_REQUIRED_PROPERTY = "The required %s config property is missing or invalid! Client cannot be started...";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{2,16}$");

    private static final AudioConnect plugin = AudioConnect.getInstance();

    static {
        plugin.getConversion().registerConverter(AudioTrackSettings.class, new AudioTrackSettingsConverter());
    }

    private final ConfigEntry<String> locale = entry(String.class, "options.locale");
    private final ConfigEntry<Number> commandCooldown = entry(Number.class, "options.command-cooldown");
    private final ConfigEntry<Number> announceFrequency = entry(Number.class, "options.announce-frequency");
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
    private final MapConfigEntry<String, AudioTrackSettings> audioTracks = mapEntry(AudioTrackSettings.class, "audio-tracks");

    private volatile PluginFile localeFile;
    private volatile UUID userId;
    private volatile UUID serverId;
    private volatile URI websocketUri;
    private volatile URL webappUrl;
    private volatile String defaultTrackId;

    @Override
    public synchronized void loadEntries(DeadmanPlugin plugin) throws IllegalStateException {
        super.loadEntries(plugin);
        localeFile = null;
        userId = null;
        serverId = null;
        websocketUri = null;
        webappUrl = null;
        defaultTrackId = null;
    }


    public synchronized boolean validate() {
        if (getConnectionUserId() == null) {
            plugin.getLogger().severe(String.format(INVALID_REQUIRED_PROPERTY, connectionUserId.getPath()));
            return false;
        }
        if (StringUtils.isEmpty(connectionUserPassword.value())) {
            plugin.getLogger().severe(String.format(INVALID_REQUIRED_PROPERTY, connectionUserPassword.getPath()));
            return false;
        }
        if (getConnectionServerId() == null) {
            plugin.getLogger().severe(String.format(INVALID_REQUIRED_PROPERTY, connectionServerId.getPath()));
            return false;
        }
        if (audioTracks.value().isEmpty()) {
            plugin.getLogger().severe(String.format(INVALID_REQUIRED_PROPERTY, audioTracks.getPath()));
            return false;
        }
        return true;
    }


    public synchronized String getLocale() {
        return locale.value();
    }

    public synchronized PluginFile getLocaleFile() {
        if (localeFile == null) {
            localeFile = createLocaleFile(locale);
        }
        return localeFile;
    }

    public synchronized int getCommandCooldown() {
        return commandCooldown.value().intValue();
    }

    public synchronized int getAnnounceFrequency() {
        return announceFrequency.value().intValue();
    }


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

    public synchronized String getConnectionUserPassword() {
        return connectionUserPassword.value();
    }

    public synchronized UUID getConnectionServerId() {
        if (serverId == null) {
            String serveridStr = connectionServerId.value();
            if (!StringUtils.isEmpty(serveridStr)) {
                serverId = ConnectUtils.parseId(serveridStr);
            }
        }
        return serverId;
    }

    public synchronized URI getConnectionWebsocketUri() {
        if (websocketUri == null) {
            websocketUri = createWebsocketUri(connectionSecure, connectionHost, connectionWebsocketPort);
        }
        return websocketUri;
    }

    public synchronized URL getConnectionWebappUrl() {
        if (webappUrl == null) {
            webappUrl = createWebappUrl(connectionSecure, connectionHost, connectionWebappPort, connectionWebappPath);
        }
        return webappUrl;
    }

    public synchronized boolean isConnectionSecure() {
        return connectionSecure.value();
    }

    public synchronized String getConnectionHost() {
        return connectionHost.value();
    }

    public synchronized int getConnectionWebsocketPort() {
        return connectionWebsocketPort.value().intValue();
    }

    public int getConnectionWebappPort() {
        return connectionWebappPort.value().intValue();
    }

    public synchronized String getConnectionWebappPath() {
        return connectionWebappPath.value();
    }

    public synchronized int getReconnectInterval() {
        return reconnectInterval.value().intValue();
    }

    public synchronized int getReconnectMaxInterval() {
        return reconnectMaxInterval.value().intValue();
    }

    public synchronized double getReconnectDelay() {
        return reconnectDelay.value().doubleValue();
    }

    public synchronized int getReconnectMaxAttempts() {
        return reconnectMaxAttempts.value().intValue();
    }

    public synchronized Map<String, AudioTrackSettings> getAudioTracks() {
        return audioTracks.value();
    }

    public synchronized String getDefaultTrackId() {
        if (defaultTrackId == null) {
            defaultTrackId = findDefaultTrackId(audioTracks);
        }
        return defaultTrackId;
    }


    private static PluginFile createLocaleFile(ConfigEntry<String> locale) {
        try {
            String filePath = "locale" + File.separator + "messages_" + locale.value().trim() + ".yml";
            return PluginFile.creator(plugin, filePath).defaultFile(filePath).create();
        } catch (Exception e) {
            String defaultLocale = locale.defaultValue().trim();
            plugin.getLogger().warning("Unsupported locale at " + locale.getPath() + " in config. Using default: " + defaultLocale);

            String filePath = "locale" + File.separator + "messages_" + defaultLocale + ".yml";
            return PluginFile.creator(plugin, filePath).defaultFile(filePath).create();
        }
    }

    private static URI createWebsocketUri(ConfigEntry<Boolean> secure, ConfigEntry<String> host, ConfigEntry<Number> port) {
        try {
            return createUri((secure.value() ? "wss" : "ws"), host.value(), port.value().intValue(), "/supplier");
        } catch (URISyntaxException e1) {
            try {
                URI uri = createUri((secure.defaultValue() ? "wss" : "ws"), host.defaultValue(), port.defaultValue().intValue(), "/supplier");
                plugin.getLogger().warning("Invalid host syntax at " + host.getPath() + " in config. Using default URI " + uri);
                return uri;
            } catch (URISyntaxException e2) {
                String paths = StringUtils.join(new String[]{secure.getPath(), host.getPath(), port.getPath()}, ", ");
                throw new IllegalStateException("A URI for the config values at paths (" + paths + ") in the default configuration file " +
                        "could not be created! The default configuration must contain valid values.", e2);
            }
        }
    }

    private static URL createWebappUrl(ConfigEntry<Boolean> secure, ConfigEntry<String> host, ConfigEntry<Number> port, ConfigEntry<String> path) {
        try {
            int validPort = (port.value().intValue() != 80 ? port.value().intValue() : -1);
            String validPath = path.value().replaceAll("^([^/])", "/$1").replaceAll("/$", "");
            return createUri((secure.value() ? "https" : "http"), host.value(), validPort, validPath).toURL();
        } catch (MalformedURLException | URISyntaxException e1) {
            try {
                int validPort = (port.defaultValue().intValue() != 80 ? port.defaultValue().intValue() : -1);
                String validPath = path.defaultValue().replaceAll("^([^/])", "/$1").replaceAll("/$", "");
                URL url = createUri((secure.defaultValue() ? "https" : "http"), host.defaultValue(), validPort, validPath).toURL();
                plugin.getLogger().warning("Invalid host syntax at " + host.getPath() + " in config. Using default URL " + url);
                return url;
            } catch (MalformedURLException | URISyntaxException e2) {
                String paths = StringUtils.join(new String[]{secure.getPath(), host.getPath(), path.getPath()}, ", ");
                throw new IllegalStateException("A URL for the config values at paths (" + paths + ") in the default configuration file " +
                        "could not be created! The default configuration must contain valid values.", e2);
            }
        }
    }

    private static URI createUri(String protocol, String host, int port, String path) throws URISyntaxException {
        return new URI(protocol, null, host, port, path, null, null);
    }

    private static String findDefaultTrackId(MapConfigEntry<String, AudioTrackSettings> audioTracks) {
        String defaultTrackId = null;
        for (Map.Entry<String, AudioTrackSettings> trackEntry : audioTracks.value().entrySet()) {
            if (defaultTrackId == null) {
                defaultTrackId = trackEntry.getKey();
            }
            if (trackEntry.getValue().isDefaultTrack()) {
                defaultTrackId = trackEntry.getKey();
            }
        }
        return defaultTrackId;
    }


    static class AudioTrackSettings {

        private final boolean defaultTrack;
        private final boolean repeating;
        private final boolean random;
        private final boolean fading;

        AudioTrackSettings(boolean defaultTrack, boolean repeating, boolean random, boolean fading) {
            this.defaultTrack = defaultTrack;
            this.repeating = repeating;
            this.random = random;
            this.fading = fading;
        }

        boolean isDefaultTrack() {
            return defaultTrack;
        }

        boolean isRepeating() {
            return repeating;
        }

        boolean isRandom() {
            return random;
        }

        boolean isFading() {
            return fading;
        }

    }

    private static class AudioTrackSettingsConverter implements Conversion.Converter<AudioTrackSettings> {

        @Override
        public AudioTrackSettings convert(Object object) {
            if (object instanceof ConfigurationSection) {
                ConfigurationSection section = (ConfigurationSection) object;
                String trackId = section.getName();
                try {
                    AudioMessage.validateIdentifier(trackId);

                    boolean defaultTrack = section.getBoolean("default");
                    boolean repeating = section.getBoolean("repeating");
                    boolean random = section.getBoolean("random");
                    boolean fading = section.getBoolean("fading");
                    return new AudioTrackSettings(defaultTrack, repeating, random, fading);
                } catch (AudioMessage.IdentifierSyntaxException e) {
                    String warning = "The configured track ID '" + trackId + "' is invalid. " + e.getMessage();
                    plugin.getLogger().warning(warning);
                }
            }
            return null;
        }
    }

}
