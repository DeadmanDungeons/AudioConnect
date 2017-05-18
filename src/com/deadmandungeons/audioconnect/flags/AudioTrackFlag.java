package com.deadmandungeons.audioconnect.flags;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.flags.AudioTrack.DayTime;
import com.deadmandungeons.audioconnect.flags.compat.FlagHandler;
import com.deadmandungeons.audioconnect.flags.compat.LegacyFlag;
import com.deadmandungeons.audioconnect.messages.AudioMessage;
import com.deadmandungeons.connect.commons.Result;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.flags.StringFlag;
import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class AudioTrackFlag extends Flag<AudioTrack> implements FlagHandler<AudioTrack> {

    private final AudioConnect plugin = AudioConnect.getInstance();
    private final StringFlag stringFlag = new StringFlag(null);

    private AudioTrackFlag(String name) {
        super(name);
    }


    public static Flag<AudioTrack> create() {
        return create(null);
    }

    public static Flag<AudioTrack> create(String name) {
        return new AudioTrackFlag(name);
    }

    public static Flag<AudioTrack> createLegacy() {
        return createLegacy(null);
    }

    public static Flag<AudioTrack> createLegacy(String name) {
        return new LegacyFlag<>(new AudioTrackFlag(null), name);
    }


    @Override
    public AudioTrack parseInput(FlagContext context) throws InvalidFlagFormat {
        return parseInput(stringFlag.parseInput(context));
    }

    @Override
    public AudioTrack parseInput(String input) throws InvalidFlagFormat {
        String audioId = null;
        String trackId = null;
        DayTime dayTime = null;
        String[] properties = input.split(":");
        if (properties.length == 1 && !properties[0].startsWith("id=")) {
            properties[0] = "id=" + properties[0];
        }
        for (String property : properties) {
            String[] keyValuePair = property.split("=");
            if (keyValuePair.length != 2) {
                throw new InvalidFlagFormat("AudioTrack properties must be in the format <key>=<value>");
            }
            String key = keyValuePair[0], value = keyValuePair[1];
            if (audioId == null && key.equals("id")) {
                audioId = value;
                Result<String> audioIdValidation = AudioMessage.validateIdentifier(audioId);
                if (!audioIdValidation.isSuccess()) {
                    String msg = plugin.getMessenger().getMessage("failed.invalid-audio-id", false, trackId, audioIdValidation.getFailReason());
                    throw new InvalidFlagFormat(msg);
                }
                if (plugin.getClient().isConnected() && (plugin.getAudioList().isEmpty() || !plugin.getAudioList().contains(audioId))) {
                    String reason = plugin.getMessenger().getMessage("failed.audio-not-added", false);
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

            String audioId;
            Object rawAudioId = map.get("audio");
            if (!(rawAudioId instanceof String) || !AudioMessage.validateIdentifier(audioId = (String) rawAudioId).isSuccess()) {
                return null;
            }

            String trackId = null;
            Object rawTrackId = map.get("track");
            if (rawTrackId != null) {
                if (!(rawTrackId instanceof String) || !AudioMessage.validateIdentifier(trackId = (String) rawTrackId).isSuccess()) {
                    return null;
                }
            }

            DayTime dayTime = null;
            Object rawDayTime = map.get("time");
            if (rawDayTime != null) {
                if (!(rawDayTime instanceof String) || (dayTime = DayTime.byName((String) rawDayTime)) == null) {
                    return null;
                }
            }

            return new AudioTrack(audioId, trackId, dayTime);
        } else {
            String audioId;
            if (!(object instanceof String) || !AudioMessage.validateIdentifier(audioId = (String) object).isSuccess()) {
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
