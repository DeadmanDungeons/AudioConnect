package com.deadmandungeons.audioconnect;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.collect.Sets;

public class AudioList {
	
	private final Set<String> invalidIds = Sets.newConcurrentHashSet();
	private final Set<String> audioIds = Sets.newConcurrentHashSet();
	
	private final AtomicBoolean initialized = new AtomicBoolean();
	private final AudioConnect plugin;
	
	public AudioList(AudioConnect plugin) {
		this.plugin = plugin;
	}
	
	public synchronized boolean isAudioIdValid(String audioId) {
		if (!audioIds.contains(audioId)) {
			if (!invalidIds.add(audioId)) {
				plugin.getLogger().warning("Invalid Identifier: ID '" + audioId + "' was referenced for an audio source that does not exist");
			}
			return false;
		}
		return true;
	}
	
	public synchronized boolean addAll(Collection<String> audioIds) {
		if (!initialized.get()) {
			initialized.set(true);
		}
		return this.audioIds.addAll(audioIds);
	}
	
	public synchronized boolean removeAll(Collection<String> audioIds) {
		return this.audioIds.removeAll(audioIds);
	}
	
	public Set<String> getAudioIds() {
		return Collections.unmodifiableSet(audioIds);
	}
	
	public boolean isInitialized() {
		return initialized.get();
	}
	
}
