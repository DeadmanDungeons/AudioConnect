package com.deadmandungeons.audioconnect.flags;

import java.util.HashMap;
import java.util.Map;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.messages.AudioMessage;
import com.deadmandungeons.audioconnect.messages.AudioMessage.Range;
import com.deadmandungeons.connect.commons.Result;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.StringFlag;

public class AudioDelayFlag extends Flag<AudioDelay> {
	
	private final AudioConnect plugin = AudioConnect.getInstance();
	private final StringFlag stringFlag = new StringFlag(null);
	
	public AudioDelayFlag(String name, RegionGroup defaultGroup) {
		super(name, defaultGroup);
	}
	
	public AudioDelayFlag(String name) {
		super(name);
	}
	
	@Override
	public AudioDelay parseInput(FlagContext context) throws InvalidFlagFormat {
		String input = stringFlag.parseInput(context);
		
		Range delayRange = null;
		String trackId = null;
		String[] properties = input.split(":");
		if (properties.length == 1 && !properties[0].startsWith("range=")) {
			properties[0] = "range=" + properties[0];
		}
		for (String property : properties) {
			String[] keyValuePair = property.split("=");
			if (keyValuePair.length != 2) {
				throw new InvalidFlagFormat("AudioDelay properties must be in the format <key>=<value>");
			}
			String key = keyValuePair[0], value = keyValuePair[1];
			if (delayRange == null && key.equals("range")) {
				delayRange = Range.parse(value);
				if (delayRange == null) {
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
		if (delayRange == null) {
			throw new InvalidFlagFormat("AudioDelay is missing required 'range' property");
		}
		
		return new AudioDelay(delayRange, trackId);
	}
	
	@Override
	public AudioDelay unmarshal(Object object) {
		if (object instanceof Map<?, ?>) {
			Map<?, ?> map = (Map<?, ?>) object;
			
			Range delayRange;
			String rawDelayRange = stringFlag.unmarshal(map.get("range"));
			if (rawDelayRange == null || (delayRange = Range.parse(rawDelayRange)) == null) {
				return null;
			}
			
			String trackId = stringFlag.unmarshal(map.get("track"));
			if (trackId != null && !AudioMessage.validateIdentifier(trackId).isSuccess()) {
				return null;
			}
			
			return new AudioDelay(delayRange, trackId);
		}
		return null;
	}
	
	@Override
	public Object marshal(AudioDelay audioDelay) {
		Map<String, Object> properties = new HashMap<>();
		properties.put("range", audioDelay.getDelayRange().toString());
		if (audioDelay.getTrackId() != null) {
			properties.put("track", audioDelay.getTrackId());
		}
		return properties;
	}
	
}
