package com.deadmandungeons.audioconnect.flags;

import javax.annotation.Nullable;

import com.deadmandungeons.audioconnect.messages.AudioMessage.Range;

public class AudioDelay {
	
	private final Range delayRange;
	private final String trackId;
	
	AudioDelay(Range delayRange, String trackId) {
		this.delayRange = delayRange;
		this.trackId = trackId;
	}
	
	public Range getDelayRange() {
		return delayRange;
	}
	
	@Nullable
	public String getTrackId() {
		return trackId;
	}
	
	@Override
	public String toString() {
		String str = delayRange.toString();
		if (trackId != null) {
			str += " (track: " + trackId + ")";
		}
		return str;
	}
	
}
