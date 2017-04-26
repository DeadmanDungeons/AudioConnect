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
	
	String getConnectionWebappPath();
	
	int getReconnectInterval();
	
	int getReconnectMaxInterval();
	
	double getReconnectDelay();
	
	int getReconnectMaxAttempts();
	
	
	int getCommandCooldown();
	
	int getAnnounceFrequency();
	
	Map<String, AudioTrackSettings> getAudioTracks();
	
	public static class AudioTrackSettings {
		
		private final boolean defaultTrack;
		private final boolean repeating;
		private final boolean random;
		private final boolean fading;
		
		public AudioTrackSettings(boolean defaultTrack, boolean repeating, boolean random, boolean fading) {
			this.defaultTrack = defaultTrack;
			this.repeating = repeating;
			this.random = random;
			this.fading = fading;
		}
		
		public boolean isDefaultTrack() {
			return defaultTrack;
		}
		
		public boolean isRepeating() {
			return repeating;
		}
		
		public boolean isRandom() {
			return random;
		}
		
		public boolean isFading() {
			return fading;
		}
		
	}
	
}
