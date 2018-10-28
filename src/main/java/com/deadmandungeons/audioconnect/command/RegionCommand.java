package com.deadmandungeons.audioconnect.command;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.flags.AudioDelay;
import com.deadmandungeons.audioconnect.flags.AudioTrack;
import com.deadmandungeons.deadmanplugin.command.ArgumentInfo;
import com.deadmandungeons.deadmanplugin.command.ArgumentInfo.ArgType;
import com.deadmandungeons.deadmanplugin.command.Arguments;
import com.deadmandungeons.deadmanplugin.command.Command;
import com.deadmandungeons.deadmanplugin.command.CommandInfo;
import com.deadmandungeons.deadmanplugin.command.SubCommandInfo;
import com.sk89q.worldguard.protection.flags.SetFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

//@formatter:off
@CommandInfo(
    name = "Region",
    description = "Manage the audio settings of a WorldGuard region. " +
            "This is a convenient alternative to using WorldGuard flags directly",
    permissions = "audioconnect.admin.region",
    inGameOnly = true,
    subCommands = {
        @SubCommandInfo(
            arguments = {
                @ArgumentInfo(argName = "info", argType = ArgType.NON_VARIABLE),
                @ArgumentInfo(argName = "region-id", argType = ArgType.VARIABLE)
            },
            description = "Display all audio settings of the given region"
        ),
        @SubCommandInfo(
            arguments = {
                @ArgumentInfo(argName = "add", argType = ArgType.NON_VARIABLE),
                @ArgumentInfo(argName = "audio", argType = ArgType.NON_VARIABLE),
                @ArgumentInfo(argName = "region-id", argType = ArgType.VARIABLE),
                @ArgumentInfo(argName = "audio-setting", argType = ArgType.VARIABLE, varType = AudioTrack.class)
            },
            description = "Add an audio setting to the given region"
        ),
        @SubCommandInfo(
            arguments = {
                @ArgumentInfo(argName = "remove", argType = ArgType.NON_VARIABLE),
                @ArgumentInfo(argName = "audio", argType = ArgType.NON_VARIABLE),
                @ArgumentInfo(argName = "region-id", argType = ArgType.VARIABLE),
                @ArgumentInfo(argName = "audio-setting", argType = ArgType.VARIABLE, varType = AudioTrack.class)
            },
            description = "Remove an audio setting from the given region"
        ),
        @SubCommandInfo(
            arguments = {
                @ArgumentInfo(argName = "add", argType = ArgType.NON_VARIABLE),
                @ArgumentInfo(argName = "delay", argType = ArgType.NON_VARIABLE),
                @ArgumentInfo(argName = "region-id", argType = ArgType.VARIABLE),
                @ArgumentInfo(argName = "delay-setting", argType = ArgType.VARIABLE, varType = AudioDelay.class)
            },
            description = "Add an audio delay setting to the given region"
        ),
        @SubCommandInfo(
            arguments = {
                @ArgumentInfo(argName = "remove", argType = ArgType.NON_VARIABLE),
                @ArgumentInfo(argName = "delay", argType = ArgType.NON_VARIABLE),
                @ArgumentInfo(argName = "region-id", argType = ArgType.VARIABLE),
                @ArgumentInfo(argName = "delay-setting", argType = ArgType.VARIABLE, varType = AudioDelay.class)
            },
            description = "Remove an audio delay setting from the given region"
        )
    }
)//@formatter:on
public class RegionCommand implements Command {

    private final AudioConnect plugin = AudioConnect.getInstance();

    @Override
    public boolean execute(CommandSender sender, Arguments args) {
        Arguments.validateType(args, getClass());

        Player player = (Player) sender;

        int command = args.getSubCmdIndex();

        String regionId = (String) args.getArgs()[command == 0 ? 1 : 2];
        RegionManager regionManager = plugin.getWorldGuardAdapter().getRegionManager(player.getWorld());
        if (regionManager == null) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.edit-invalid-region", regionId);
            return false;
        }

        if (command == 0) {
            return printAudioInfo(sender, regionManager, regionId);
        } else {
            switch (command) {
                case 1:
                    return addAudio(sender, (AudioTrack) args.getArgs()[3], regionManager, regionId);
                case 2:
                    return removeAudio(sender, (AudioTrack) args.getArgs()[3], regionManager, regionId);
                case 3:
                    return addDelay(sender, (AudioDelay) args.getArgs()[3], regionManager, regionId);
                case 4:
                    return removeDelay(sender, (AudioDelay) args.getArgs()[3], regionManager, regionId);
            }
        }
        return false;
    }


    public boolean printAudioInfo(CommandSender sender, RegionManager regionManager, String regionId) {
        ProtectedRegion region = regionManager.getRegion(regionId);
        if (region == null) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.edit-invalid-region", regionId);
            return false;
        }
        Set<AudioTrack> regionAudio = region.getFlag(plugin.getAudioFlag());
        Set<AudioDelay> regionAudioDelay = region.getFlag(plugin.getAudioDelayFlag());

        StringBuilder audioInfo = new StringBuilder();

        ChatColor color1 = plugin.getMessenger().getPrimaryColor();
        ChatColor color2 = plugin.getMessenger().getSecondaryColor();
        ChatColor color3 = plugin.getMessenger().getTertiaryColor();

        audioInfo.append(color2);
        audioInfo.append("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
        audioInfo.append(" Region Audio Info ");
        audioInfo.append("\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
        audioInfo.append("\n");

        audioInfo.append(color2).append("Region: ").append(color1).append(regionId).append("\n");

        audioInfo.append(color2).append("Audio: ").append("\n");
        if (regionAudio == null || regionAudio.isEmpty()) {
            audioInfo.append(ChatColor.RED).append("  * NONE *").append("\n");
        } else {
            for (AudioTrack audio : regionAudio) {
                audioInfo.append(color3).append("- ").append(color1).append(audio.getAudioId());

                audioInfo.append(color2).append(" (").append("track: ");
                if (audio.getTrackId() != null) {
                    audioInfo.append(color1).append(audio.getTrackId());
                } else {
                    audioInfo.append("default");
                }

                audioInfo.append(color2).append(", time: ");
                if (audio.getDayTime() != null) {
                    audioInfo.append(color1).append(audio.getDayTime().name().toLowerCase());
                } else {
                    audioInfo.append("any");
                }

                audioInfo.append(color2).append(")").append("\n");
            }
        }

        audioInfo.append(color2).append("Audio Delay: ").append("\n");
        if (regionAudioDelay == null || regionAudioDelay.isEmpty()) {
            audioInfo.append(ChatColor.RED).append("  * NONE *").append("\n");
        } else {
            for (AudioDelay audioDelay : regionAudioDelay) {
                audioInfo.append(color3).append("- ").append(color1).append(audioDelay.getDelayTime());

                audioInfo.append(color2).append(" (").append("track: ");
                audioInfo.append(color2).append(" (").append("track: ");
                if (audioDelay.getTrackId() != null) {
                    audioInfo.append(color1).append(audioDelay.getTrackId());
                } else {
                    audioInfo.append("default");
                }

                audioInfo.append(color2).append(")").append("\n");
            }
        }

        sender.sendMessage(audioInfo.toString());
        return true;
    }

    public boolean addAudio(CommandSender sender, AudioTrack audio, RegionManager regionManager, String regionId) {
        ProtectedRegion region = regionManager.getRegion(regionId);
        if (region == null) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.edit-invalid-region", regionId);
            return false;
        }
        Set<AudioTrack> audioTracks = getRegionSetFlag(region, plugin.getAudioFlag());
        if (!audioTracks.add(audio)) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.edit-audio-exists", regionId);
            return false;
        }

        if (!saveRegionSetFlag(regionManager, region, plugin.getAudioFlag(), audioTracks)) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.worldguard-save");
            return false;
        }

        plugin.getMessenger().sendMessage(sender, "succeeded.audio-added", audio, regionId);
        return true;
    }

    public boolean removeAudio(CommandSender sender, AudioTrack audio, RegionManager regionManager, String regionId) {
        ProtectedRegion region = regionManager.getRegion(regionId);
        if (region == null) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.edit-invalid-region", regionId);
            return false;
        }
        Set<AudioTrack> audioTracks = getRegionSetFlag(region, plugin.getAudioFlag());
        if (!audioTracks.remove(audio)) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.edit-audio-absent", regionId);
            return false;
        }

        if (!saveRegionSetFlag(regionManager, region, plugin.getAudioFlag(), audioTracks)) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.worldguard-save");
            return false;
        }

        plugin.getMessenger().sendMessage(sender, "succeeded.audio-removed", audio, regionId);
        return true;
    }

    public boolean addDelay(CommandSender sender, AudioDelay delay, RegionManager regionManager, String regionId) {
        ProtectedRegion region = regionManager.getRegion(regionId);
        if (region == null) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.edit-invalid-region", regionId);
            return false;
        }
        Set<AudioDelay> audioDelays = getRegionSetFlag(region, plugin.getAudioDelayFlag());
        if (!audioDelays.add(delay)) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.edit-delay-exists", regionId);
            return false;
        }

        if (!saveRegionSetFlag(regionManager, region, plugin.getAudioDelayFlag(), audioDelays)) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.worldguard-save");
            return false;
        }

        plugin.getMessenger().sendMessage(sender, "succeeded.delay-added", delay, regionId);
        return true;
    }

    public boolean removeDelay(CommandSender sender, AudioDelay delay, RegionManager regionManager, String regionId) {
        ProtectedRegion region = regionManager.getRegion(regionId);
        if (region == null) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.edit-invalid-region", regionId);
            return false;
        }
        Set<AudioDelay> audioDelays = getRegionSetFlag(region, plugin.getAudioDelayFlag());
        if (!audioDelays.remove(delay)) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.edit-delay-absent", regionId);
            return false;
        }

        if (!saveRegionSetFlag(regionManager, region, plugin.getAudioDelayFlag(), audioDelays)) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.worldguard-save");
            return false;
        }

        plugin.getMessenger().sendMessage(sender, "succeeded.delay-removed", delay, regionId);
        return true;
    }


    private <T> boolean saveRegionSetFlag(RegionManager regionManager, ProtectedRegion region, SetFlag<T> flag, Set<T> value) {
        try {
            region.setFlag(flag, value);
            regionManager.saveChanges();
            return true;
        } catch (StorageException e) {
            String msg = "Failed to save '" + regionManager + "' WorldGuard region changes from edit command";
            plugin.getLogger().log(Level.WARNING, msg, e);
            return false;
        }
    }

    private <T> Set<T> getRegionSetFlag(ProtectedRegion region, SetFlag<T> setFlag) {
        Set<T> flagValue = region.getFlag(setFlag);
        if (flagValue == null) {
            return new HashSet<>();
        } else {
            return new HashSet<>(flagValue);
        }
    }

}
