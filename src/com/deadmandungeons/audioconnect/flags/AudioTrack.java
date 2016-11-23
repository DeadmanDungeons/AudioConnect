package com.deadmandungeons.audioconnect.flags;

import java.util.Objects;

import javax.annotation.Nullable;

import org.apache.commons.lang.builder.HashCodeBuilder;
import org.bukkit.World;

import com.google.common.collect.ImmutableList;

public class AudioTrack {
	
	private final String audioId;
	private final String trackId;
	private final DayTime dayTime;
	
	AudioTrack(String audioId, String trackId, DayTime dayTime) {
		this.audioId = audioId;
		this.trackId = trackId;
		this.dayTime = dayTime;
	}
	
	public String getAudioId() {
		return audioId;
	}
	
	@Nullable
	public String getTrackId() {
		return trackId;
	}
	
	@Nullable
	public DayTime getDayTime() {
		return dayTime;
	}
	
	@Override // audio: [Desert-2(track: primary, time: night)]
	public String toString() {
		StringBuilder builder = new StringBuilder(audioId);
		if (trackId != null || dayTime != null) {
			builder.append("(");
			if (trackId != null) {
				builder.append("track: ").append(trackId);
			}
			if (dayTime != null) {
				if (trackId != null) {
					builder.append(", ");
				}
				builder.append("time: ").append(dayTime.name().toLowerCase());
			}
			builder.append(")");
		}
		return builder.toString();
	}
	
	@Override
	public int hashCode() {
		return new HashCodeBuilder().append(audioId).append(trackId).append(dayTime).toHashCode();
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof AudioTrack)) {
			return false;
		}
		AudioTrack other = (AudioTrack) obj;
		return audioId.equals(other.audioId) && Objects.equals(trackId, other.trackId) && Objects.equals(dayTime, other.dayTime);
	}
	
	
	public static enum DayTime {
		DAY(0, 13000),
		NIGHT(13000, 24000),
		MORNING(0, 6000),
		AFTERNOON(6000, 13000);
		
		public static final ImmutableList<DayTime> VALUES = ImmutableList.copyOf(values());
		
		private final int minTicks, maxTicks;
		
		private DayTime(int minTicks, int maxTicks) {
			this.minTicks = minTicks;
			this.maxTicks = maxTicks;
		}
		
		public boolean check(World world) {
			return world.getTime() >= minTicks && world.getTime() < maxTicks;
		}
		
		public static DayTime byName(String name) {
			for (DayTime dayTime : VALUES) {
				if (dayTime.name().equalsIgnoreCase(name)) {
					return dayTime;
				}
			}
			return null;
		}
		
	}
	
}
