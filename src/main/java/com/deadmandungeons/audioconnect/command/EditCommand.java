package com.deadmandungeons.audioconnect.command;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.flags.AudioTrack;
import com.deadmandungeons.deadmanplugin.command.ArgumentInfo;
import com.deadmandungeons.deadmanplugin.command.ArgumentInfo.ArgType;
import com.deadmandungeons.deadmanplugin.command.Arguments;
import com.deadmandungeons.deadmanplugin.command.Command;
import com.deadmandungeons.deadmanplugin.command.CommandInfo;
import com.deadmandungeons.deadmanplugin.command.SubCommandInfo;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

//@formatter:off
@CommandInfo(
    name = "edit",
    description = "Edit the audio settings of a WorldGuard region. " +
            "This is a convenient alternative to using the WorldGuard flags directly",
    permissions = "audioconnect.admin.edit",
    inGameOnly = true,
    subCommands = {
        @SubCommandInfo(
            arguments = {
                @ArgumentInfo(argName = "add", argType = ArgType.NON_VARIABLE),
                @ArgumentInfo(argName = "region-id", argType = ArgType.VARIABLE),
                @ArgumentInfo(argName = "audio", argType = ArgType.VARIABLE, varType = AudioTrack.class)
            },
            description = "Add an audio setting to the given region in the current World"
        ),
        @SubCommandInfo(
            arguments = {
                @ArgumentInfo(argName = "remove", argType = ArgType.NON_VARIABLE),
                @ArgumentInfo(argName = "region-id", argType = ArgType.VARIABLE),
                @ArgumentInfo(argName = "audio", argType = ArgType.VARIABLE, varType = AudioTrack.class)
            },
            description = "Remove an audio setting from the given region in the current World"
        )
    }
)//@formatter:on
public class EditCommand implements Command {

    private final AudioConnect plugin = AudioConnect.getInstance();

    @Override
    public boolean execute(CommandSender sender, Arguments args) {
        Arguments.validateType(args, getClass());

        Player player = (Player) sender;

        String regionId = (String) args.getArgs()[1];
        AudioTrack audioTrack = (AudioTrack) args.getArgs()[2];

        RegionManager regionManager = WorldGuardPlugin.inst().getRegionManager(player.getWorld());
        if (regionManager == null) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.edit-invalid-region", regionId);
            return false;
        }


        switch (args.getSubCmdIndex()) {
            case 0:
                return addAudio(sender, audioTrack, regionManager, regionId);
            case 1:
                return removeAudio(sender, audioTrack, regionManager, regionId);
        }
        return false;
    }

    public boolean addAudio(CommandSender sender, AudioTrack audio, RegionManager regionManager, String regionId) {
        ProtectedRegion region = regionManager.getRegion(regionId);
        if (region == null) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.edit-invalid-region", regionId);
            return false;
        }
        Set<AudioTrack> regionAudio = getRegionAudio(region);
        if (!regionAudio.add(audio)) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.edit-audio-exists", regionId);
            return false;
        }

        if (!saveAudioFlag(regionManager, region, regionAudio)) {
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
        Set<AudioTrack> regionAudio = getRegionAudio(region);
        if (!regionAudio.remove(audio)) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.edit-audio-absent", regionId);
            return false;
        }

        if (!saveAudioFlag(regionManager, region, regionAudio)) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.worldguard-save");
            return false;
        }

        plugin.getMessenger().sendMessage(sender, "succeeded.audio-removed", audio, regionId);
        return true;
    }

    private boolean saveAudioFlag(RegionManager regionManager, ProtectedRegion region, Set<AudioTrack> regionAudio) {
        try {
            region.setFlag(plugin.getAudioFlag(), regionAudio);
            regionManager.saveChanges();
            return true;
        } catch (StorageException e) {
            String msg = "Failed to save '" + regionManager + "' WorldGuard region changes from edit command";
            plugin.getLogger().log(Level.WARNING, msg, e);
            return false;
        }
    }

    private Set<AudioTrack> getRegionAudio(ProtectedRegion region) {
        Set<AudioTrack> regionAudio = region.getFlag(plugin.getAudioFlag());
        if (regionAudio == null) {
            return new HashSet<>();
        } else {
            return new HashSet<>(regionAudio);
        }
    }

}
