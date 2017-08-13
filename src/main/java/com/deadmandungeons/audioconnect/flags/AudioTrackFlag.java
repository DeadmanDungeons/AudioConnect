package com.deadmandungeons.audioconnect.flags;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.flags.AudioTrack.DayTime;
import com.deadmandungeons.audioconnect.flags.compat.FlagHandler;
import com.deadmandungeons.audioconnect.flags.compat.LegacyFlag;
import com.deadmandungeons.audioconnect.messages.AudioMessage;
import com.deadmandungeons.audioconnect.messages.AudioMessage.IdentifierSyntaxException;
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

    public AudioTrackFlag() {
        super(null);
    }

    public static Flag<AudioTrack> createLegacy() {
        return new LegacyFlag<>(new AudioTrackFlag(), null);
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
        if (!properties[0].startsWith("id=")) {
            properties[0] = "id=" + properties[0];
        }
        for (String property : properties) {
            String[] keyValuePair = property.split("=");
            if (keyValuePair.length != 2) {
                throw new InvalidFlagFormat("AudioTrack settings must be in the format ':<key>=<value>' following the audio ID");
            }
            String key = keyValuePair[0], value = keyValuePair[1];
            if (audioId == null && key.equals("id")) {
                audioId = value;
                try {
                    AudioMessage.validateIdentifier(audioId);
                } catch (IdentifierSyntaxException e) {
                    String msg = plugin.getMessenger().getMessage("failed.invalid-audio-id", false, audioId, e.getMessage());
                    throw new InvalidFlagFormat(msg);
                }
                if (plugin.getClient().isConnected() && (plugin.getAudioList().isEmpty() || !plugin.getAudioList().contains(audioId))) {
                    String reason = plugin.getMessenger().getMessage("failed.audio-not-added", false);
                    String msg = plugin.getMessenger().getMessage("failed.invalid-audio-id", false, audioId, reason);
                    throw new InvalidFlagFormat(msg);
                }
            } else if (trackId == null && key.equals("track")) {
                trackId = value;
                try {
                    AudioMessage.validateIdentifier(trackId);
                } catch (IdentifierSyntaxException e) {
                    String msg = plugin.getMessenger().getMessage("failed.invalid-track-id", false, trackId, e.getMessage());
                    throw new InvalidFlagFormat(msg);
                }
            } else if (dayTime == null && key.equals("time")) {
                dayTime = DayTime.byName(value);
                if (dayTime == null) {
                    throw new InvalidFlagFormat("AudioTrack time property must be one of " + StringUtils.join(DayTime.VALUES, ", "));
                }
            } else {
                throw new InvalidFlagFormat("Duplicate or unknown AudioTrack property '" + key + "'");
            }
        }
        if (audioId == null) {
            throw new InvalidFlagFormat("AudioTrack is missing required 'audio' property");
        }

        return new AudioTrack(audioId, trackId, dayTime);
    }

    @Override
    public AudioTrack unmarshal(Object object) {
        try {
            if (object instanceof Map<?, ?>) {
                Map<?, ?> map = (Map<?, ?>) object;

                Object rawAudioId = map.get("audio");
                if (!(rawAudioId instanceof String)) {
                    return null;
                }
                String audioId = (String) rawAudioId;
                AudioMessage.validateIdentifier(audioId);

                String trackId = null;
                Object rawTrackId = map.get("track");
                if (rawTrackId != null) {
                    if (!(rawTrackId instanceof String)) {
                        return null;
                    }
                    trackId = (String) rawTrackId;
                    AudioMessage.validateIdentifier(trackId);
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
                if (!(object instanceof String)) {
                    return null;
                }
                String audioId = (String) object;
                AudioMessage.validateIdentifier(audioId);

                return new AudioTrack(audioId, null, null);
            }
        } catch (IdentifierSyntaxException e) {
            return null;
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
