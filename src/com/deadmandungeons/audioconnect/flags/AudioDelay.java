package com.deadmandungeons.audioconnect.flags;

import javax.annotation.Nullable;

import com.deadmandungeons.audioconnect.messages.AudioMessage.Range;

public class AudioDelay {
	
	private final Range delayTime;
	private final String trackId;
	
	AudioDelay(Range delayTime, String trackId) {
		this.delayTime = delayTime;
		this.trackId = trackId;
	}
	
	public Range getDelayTime() {
		return delayTime;
	}
	
	@Nullable
	public String getTrackId() {
		return trackId;
	}
	
	@Override
	public String toString() {
		String str = delayTime.toString();
		if (trackId != null) {
			str += " (track: " + trackId + ")";
		}
		return str;
	}
	
}
