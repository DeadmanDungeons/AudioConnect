package com.deadmandungeons.audioconnect.flags;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.flags.AudioTrack.DayTime;
import com.deadmandungeons.audioconnect.messages.AudioMessage;
import com.deadmandungeons.connect.commons.Result;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.StringFlag;

public class AudioTrackFlag extends Flag<AudioTrack> {
	
	private final AudioConnect plugin = AudioConnect.getInstance();
	private final StringFlag stringFlag = new StringFlag(null);
	
	public AudioTrackFlag(String name, RegionGroup defaultGroup) {
		super(name, defaultGroup);
	}
	
	public AudioTrackFlag(String name) {
		super(name);
	}
	
	@Override
	public AudioTrack parseInput(FlagContext context) throws InvalidFlagFormat {
		String input = stringFlag.parseInput(context);
		
		String audioId = null;
		String trackId = null;
		DayTime dayTime = null;
		String[] properties = input.split(":");
		if (properties.length == 1 && !properties[0].startsWith("audio=")) {
			properties[0] = "audio=" + properties[0];
		}
		for (String property : properties) {
			String[] keyValuePair = property.split("=");
			if (keyValuePair.length != 2) {
				throw new InvalidFlagFormat("AudioTrack properties must be in the format <key>=<value>");
			}
			String key = keyValuePair[0], value = keyValuePair[1];
			if (audioId == null && key.equals("audio")) {
				audioId = value;
				Result<String> audioIdValidation = AudioMessage.validateIdentifier(audioId);
				if (!audioIdValidation.isSuccess()) {
					String msg = plugin.getMessenger().getMessage("failed.invalid-audio-id", false, trackId, audioIdValidation.getFailReason());
					throw new InvalidFlagFormat(msg);
				}
				if (plugin.getClient().isConnected() && (plugin.getAudioList().isEmpty() || !plugin.getAudioList().contains(audioId))) {
					String reason = plugin.getMessenger().getMessage("failed.audio-not-uploaded", false);
					String msg = plugin.getMessenger().getMessage("failed.invalid-audio-id", false, audioId, reason);
					throw new InvalidFlagFormat(msg);
				}
			} else if (trackId == null && key.equals("track")) {
				trackId = value;
				Result<String> trackIdValidation = AudioMessage.validateIdentifier(trackId);
				if (!trackIdValidation.isSuccess()) {
					String msg = plugin.getMessenger().getMessage("failed.invalid-track-id", false, trackId, trackIdValidation.getFailReason());
					throw new InvalidFlagFormat(msg);
				}
			} else if (dayTime == null && key.equals("time")) {
				dayTime = DayTime.byName(value);
				if (dayTime == null) {
					throw new InvalidFlagFormat("AudioTrack time property must be one of " + StringUtils.join(DayTime.VALUES, ", "));
				}
			} else {
				throw new InvalidFlagFormat("Duplicate or unkown AudioTrack property '" + key + "'");
			}
		}
		if (audioId == null) {
			throw new InvalidFlagFormat("AudioTrack is missing required 'audio' property");
		}
		
		return new AudioTrack(audioId, trackId, dayTime);
	}
	
	@Override
	public AudioTrack unmarshal(Object object) {
		if (object instanceof Map<?, ?>) {
			Map<?, ?> map = (Map<?, ?>) object;
			
			String audioId = stringFlag.unmarshal(map.get("audio"));
			if (audioId == null || !AudioMessage.validateIdentifier(audioId).isSuccess()) {
				return null;
			}
			
			String trackId = stringFlag.unmarshal(map.get("track"));
			if (trackId != null && !AudioMessage.validateIdentifier(trackId).isSuccess()) {
				return null;
			}
			
			DayTime dayTime = null;
			String time = stringFlag.unmarshal(map.get("time"));
			if (time != null && (dayTime = DayTime.byName(time)) == null) {
				return null;
			}
			
			return new AudioTrack(audioId, trackId, dayTime);
		} else {
			String audioId = stringFlag.unmarshal(object);
			if (audioId == null || !AudioMessage.validateIdentifier(audioId).isSuccess()) {
				return null;
			}
			
			return new AudioTrack(audioId, null, null);
		}
	}
	
	@Override
	public Object marshal(AudioTrack audioTrack) {
		if (audioTrack.getTrackId() != null || audioTrack.getDayTime() != null) {
			Map<String, Object> properties = new HashMap<>();
			properties.put("audio", audioTrack.getAudioId());
			if (audioTrack.getTrackId() != null) {
				properties.put("track", audioTrack.getTrackId());
			}
			if (audioTrack.getDayTime() != null) {
				properties.put("time", audioTrack.getDayTime().name().toLowerCase());
			}
			return properties;
		} else {
			return audioTrack.getAudioId();
		}
	}
	
}
