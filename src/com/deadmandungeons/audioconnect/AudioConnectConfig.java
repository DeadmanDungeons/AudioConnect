package com.deadmandungeons.audioconnect;

import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.UUID;

public interface AudioConnectConfig {

    boolean validate();

    UUID getConnectionUserId();

    String getConnectionUserPassword();

    UUID getConnectionServerId();

    URI getConnectionWebsocketUri();

    URL getConnectionWebappUrl();

    boolean isConnectionSecure();

    String getConnectionHost();

    int getConnectionWebsocketPort();

    int getConnectionWebappPort();

    String getConnectionWebappPath();

    int getReconnectInterval();

    int getReconnectMaxInterval();

    double getReconnectDelay();

    int getReconnectMaxAttempts();


    int getCommandCooldown();

    int getAnnounceFrequency();

    Map<String, AudioTrackSettings> getAudioTracks();

    class AudioTrackSettings {

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

}
