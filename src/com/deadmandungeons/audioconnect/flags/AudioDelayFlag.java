package com.deadmandungeons.audioconnect.flags;

import java.util.HashMap;
import java.util.Map;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.flags.compat.FlagHandler;
import com.deadmandungeons.audioconnect.flags.compat.LegacyFlag;
import com.deadmandungeons.audioconnect.messages.AudioMessage;
import com.deadmandungeons.audioconnect.messages.AudioMessage.Range;
import com.deadmandungeons.connect.commons.Result;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.flags.StringFlag;

public class AudioDelayFlag extends Flag<AudioDelay> implements FlagHandler<AudioDelay> {
	
	private final AudioConnect plugin = AudioConnect.getInstance();
	private final StringFlag stringFlag = new StringFlag(null);
	
	private AudioDelayFlag(String name) {
		super(name);
	}
	
	
	public static Flag<AudioDelay> create() {
		return create(null);
	}
	
	public static Flag<AudioDelay> create(String name) {
		return new AudioDelayFlag(name);
	}
	
	public static Flag<AudioDelay> createLegacy() {
		return createLegacy(null);
	}
	
	public static Flag<AudioDelay> createLegacy(String name) {
		return new LegacyFlag<>(new AudioDelayFlag(null), name);
	}
	
	
	@Override
	public AudioDelay parseInput(FlagContext context) throws InvalidFlagFormat {
		return parseInput(stringFlag.parseInput(context));
	}
	
	@Override
	public AudioDelay parseInput(String input) throws InvalidFlagFormat {
		Range delayTime = null;
		String trackId = null;
		String[] properties = input.split(":");
		if (properties.length == 1 && !properties[0].startsWith("time=")) {
			properties[0] = "time=" + properties[0];
		}
		for (String property : properties) {
			String[] keyValuePair = property.split("=");
			if (keyValuePair.length != 2) {
				throw new InvalidFlagFormat("AudioDelay properties must be in the format <key>=<value>");
			}
			String key = keyValuePair[0], value = keyValuePair[1];
			if (delayTime == null && key.equals("time")) {
				delayTime = Range.parse(value);
				if (delayTime == null) {
					throw new InvalidFlagFormat(plugin.getMessenger().getMessage("failed.invalid-delay-range", true, value));
				}
			} else if (trackId == null && key.equals("track")) {
				trackId = value;
				Result<String> audioIdValidation = AudioMessage.validateIdentifier(trackId);
				if (!audioIdValidation.isSuccess()) {
					throw new InvalidFlagFormat("track " + audioIdValidation.getFailReason());
				}
			} else {
				throw new InvalidFlagFormat("Duplicate or unkown AudioDelay property '" + key + "'");
			}
		}
		if (delayTime == null) {
			throw new InvalidFlagFormat("AudioDelay is missing required 'time' property");
		}
		
		return new AudioDelay(delayTime, trackId);
	}
	
	@Override
	public AudioDelay unmarshal(Object object) {
		if (object instanceof Map<?, ?>) {
			Map<?, ?> map = (Map<?, ?>) object;
			
			Range delayTime;
			Object rawDelayTime = map.get("range");
			if (!(rawDelayTime instanceof String) || (delayTime = Range.parse((String) rawDelayTime)) == null) {
				return null;
			}
			
			String trackId = null;
			Object rawTrackId = map.get("track");
			if (rawTrackId != null) {
				if (!(rawTrackId instanceof String) || !AudioMessage.validateIdentifier((String) rawTrackId).isSuccess()) {
					return null;
				}
				trackId = (String) rawTrackId;
			}
			
			return new AudioDelay(delayTime, trackId);
		}
		return null;
	}
	
	@Override
	public Object marshal(AudioDelay audioDelay) {
		Map<String, Object> properties = new HashMap<>();
		properties.put("range", audioDelay.getDelayTime().toString());
		if (audioDelay.getTrackId() != null) {
			properties.put("track", audioDelay.getTrackId());
		}
		return properties;
	}
	
}
