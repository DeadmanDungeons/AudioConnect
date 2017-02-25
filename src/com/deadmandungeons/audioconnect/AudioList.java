package com.deadmandungeons.audioconnect;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.google.common.collect.Sets;

public class AudioList {
	
	private static final int WARNING_DELAY_MILLIS = 1000 * 60 * 5;
	
	private final ConcurrentHashMap<String, Long> invalidIds = new ConcurrentHashMap<>();
	private final Set<String> audioIds = Sets.newConcurrentHashSet();
	
	private final Logger logger;
	
	public AudioList(Logger logger) {
		this.logger = logger;
	}
	
	public boolean isAudioIdValid(String audioId) {
		if (!audioIds.contains(audioId)) {
			long now = System.currentTimeMillis();
			Long lastWarnTime = invalidIds.get(audioId);
			if (lastWarnTime == null || lastWarnTime < now - WARNING_DELAY_MILLIS) {
				invalidIds.put(audioId, now);
				logger.warning("Invalid Identifier: ID '" + audioId + "' was referenced for an audio source that does not exist");
			}
			return false;
		}
		return true;
	}
	
	public boolean addAll(Collection<String> audioIds) {
		return this.audioIds.addAll(audioIds);
	}
	
	public boolean removeAll(Collection<String> audioIds) {
		return this.audioIds.removeAll(audioIds);
	}
	
	public Set<String> getAudioIds() {
		return Collections.unmodifiableSet(audioIds);
	}
	
	public boolean isEmpty() {
		return audioIds.isEmpty();
	}
	
}
