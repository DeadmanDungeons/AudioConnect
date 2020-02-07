package com.deadmandungeons.audioconnect.command;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.AudioConnectClient.PlayerConnection;
import com.deadmandungeons.audioconnect.messages.AudioMessage;
import com.deadmandungeons.deadmanplugin.command.ArgumentInfo;
import com.deadmandungeons.deadmanplugin.command.ArgumentInfo.ArgType;
import com.deadmandungeons.deadmanplugin.command.Arguments;
import com.deadmandungeons.deadmanplugin.command.Command;
import com.deadmandungeons.deadmanplugin.command.CommandInfo;
import com.deadmandungeons.deadmanplugin.command.SubCommandInfo;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//@formatter:off
@CommandInfo(
    name = "Send",
    aliases = {"play"},
    permissions = {"audioconnect.admin.send"},
    description = "Send a server command to play an audio source for selected target players (on the provided or default track). \n  " +
        "NOTE: Use asterisk '*' as the <audio-id> to stop/clear the currently playing audio (if there is any).",
    subCommands = {
        @SubCommandInfo(
            arguments = {
                @ArgumentInfo(argName = "@a", argType = ArgType.NON_VARIABLE),
                @ArgumentInfo(argName = "audio-id", argType = ArgType.VARIABLE),
                @ArgumentInfo(argName = "track-id", argType = ArgType.OPT_VARIABLE),
            },
            description = "Send an audio source to play for all players"
        ),
        @SubCommandInfo(
            arguments = {
                @ArgumentInfo(argName = "@a", argType = ArgType.NON_VARIABLE),
                @ArgumentInfo(argName = "world", argType = ArgType.VARIABLE),
                @ArgumentInfo(argName = "region-id", argType = ArgType.VARIABLE),
                @ArgumentInfo(argName = "audio-id", argType = ArgType.VARIABLE),
                @ArgumentInfo(argName = "track-id", argType = ArgType.OPT_VARIABLE),
            },
            description = "Send an audio source to play for all players inside a region"
        ),
        @SubCommandInfo(
            arguments = {
                @ArgumentInfo(argName = "@p", argType = ArgType.NON_VARIABLE),
                @ArgumentInfo(argName = "audio-id", argType = ArgType.VARIABLE),
                @ArgumentInfo(argName = "track-id", argType = ArgType.OPT_VARIABLE),
            },
            description = "Send an audio source to play for the closest player to the executing command_block or entity"
        ),
        @SubCommandInfo(
            arguments = {
                @ArgumentInfo(argName = "@p", argType = ArgType.NON_VARIABLE),
                @ArgumentInfo(argName = "world", argType = ArgType.VARIABLE),
                @ArgumentInfo(argName = "x", argType = ArgType.VARIABLE, varType = Integer.class),
                @ArgumentInfo(argName = "y", argType = ArgType.VARIABLE, varType = Integer.class),
                @ArgumentInfo(argName = "z", argType = ArgType.VARIABLE, varType = Integer.class),
                @ArgumentInfo(argName = "audio-id", argType = ArgType.VARIABLE),
                @ArgumentInfo(argName = "track-id", argType = ArgType.OPT_VARIABLE),
            },
            description = "Send an audio source to play for the closest player to a location"
        ),
        @SubCommandInfo(
            arguments = {
                @ArgumentInfo(argName = "player-name", argType = ArgType.VARIABLE),
                @ArgumentInfo(argName = "audio-id", argType = ArgType.VARIABLE),
                @ArgumentInfo(argName = "track-id", argType = ArgType.OPT_VARIABLE),
            },
            description = "Send an audio source to play for a specific player"
        )
    }
)//@formatter:on
public class SendCommand implements Command {

    private final AudioConnect plugin = AudioConnect.getInstance();

    @Override
    public boolean execute(CommandSender sender, Arguments arguments) {
        Arguments.validateType(arguments, getClass());

        if (!plugin.getClient().isConnected()) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.client-disconnected");
            return false;
        }

        Object[] args = arguments.getArgs();
        int actualArgCount = args.length;
        int fullArgCount = arguments.getSubCmd().arguments().length;
        String trackId = (String) (actualArgCount == fullArgCount ? args[actualArgCount - 1] : null);
        String audioId = (String) (actualArgCount == fullArgCount ? args[actualArgCount - 2] : args[actualArgCount - 1]);

        if (!audioId.equals("*") && !plugin.getAudioList().contains(audioId)) {
            String reason = plugin.getMessenger().getMessage("failed.audio-not-added", false);
            plugin.getMessenger().sendErrorMessage(sender, "failed.invalid-audio-id", audioId, reason);
            return false;
        }

        switch (arguments.getSubCmdIndex()) {
            case 0:
                 return sendAllConnectedPlayers(sender, audioId, trackId);
            case 1:
                return sendInsideConnectedPlayers(sender, (String) args[1], (String) args[2], audioId, trackId);
            case 2:
                return sendNearestConnectedPlayer(sender, audioId, trackId);
            case 3:
               return sendNearestConnectedPlayer(sender, (String) args[1], (int) args[2], (int) args[3], (int) args[4], audioId, trackId);
            case 4:
                return sendConnectedPlayer(sender, (String) args[0], audioId, trackId);
        }

        return false;
    }

    public boolean sendAllConnectedPlayers(CommandSender sender, String audioId, String trackId) {
        List<AudioMessage> audioMessages = new ArrayList<>();
        for (PlayerConnection playerConnection : plugin.getClient().getPlayerConnections()) {
            Player player = playerConnection.getOfflinePlayer().getPlayer();
            if (player != null) {
                audioMessages.add(buildAudioMessage(player, audioId, trackId));
            }
        }

        if (audioMessages.isEmpty()) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.no-players-connected");
            return false;
        }

        plugin.getClient().writeAndFlush(audioMessages.toArray(new AudioMessage[0]));

        plugin.getMessenger().sendMessage(sender, "succeeded.audio-sent-all", audioId);
        return true;
    }

    public boolean sendInsideConnectedPlayers(CommandSender sender, String worldName, String regionId, String audioId, String trackId) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "A world named '" + worldName + "' does not exist");
            return false;
        }

        ProtectedRegion region = plugin.getWorldGuardAdapter().getRegionManager(world).getRegion(regionId);
        if (region == null) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.edit-invalid-region", regionId);
            return false;
        }

        Player insidePlayer = null;
        List<AudioMessage> audioMessages = new ArrayList<>();
        for (PlayerConnection playerConnection : plugin.getClient().getPlayerConnections()) {
            Player player = playerConnection.getOfflinePlayer().getPlayer();
            if (player == null || !player.getWorld().getName().equals(worldName)) {
                continue;
            }

            Location playerLoc = player.getLocation();
            if (region.contains(playerLoc.getBlockX(), playerLoc.getBlockY(), playerLoc.getBlockZ())) {
                audioMessages.add(buildAudioMessage(player, audioId, trackId));
                insidePlayer = player;
            }
        }

        if (audioMessages.isEmpty()) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.no-region-players-connected", regionId);
            return false;
        }

        plugin.getClient().writeAndFlush(audioMessages.toArray(new AudioMessage[0]));

        int playerCount = audioMessages.size();
        if (playerCount > 1) {
            plugin.getMessenger().sendMessage(sender, "succeeded.audio-sent-region", audioId, playerCount, regionId);
        } else {
            plugin.getMessenger().sendMessage(sender, "succeeded.audio-sent-player-region", audioId, insidePlayer.getName(), regionId);
        }
        return true;
    }

    public boolean sendNearestConnectedPlayer(CommandSender sender, String audioId, String trackId) {
        if (sender instanceof BlockCommandSender) {
            return sendNearestConnectedPlayer(sender, ((BlockCommandSender) sender).getBlock().getLocation(), audioId, trackId);
        } else if (sender instanceof Entity) {
            return sendNearestConnectedPlayer(sender, ((Entity) sender).getLocation(), audioId, trackId);
        }

        sender.sendMessage(ChatColor.RED + "This command can only be used in game.");
        return false;
    }

    public boolean sendNearestConnectedPlayer(CommandSender sender, String worldName, int x, int y, int z, String audioId, String trackId) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "A world named '" + worldName + "' does not exist");
            return false;
        }

        return sendNearestConnectedPlayer(sender, new Location(world, x, y, z), audioId, trackId);
    }
    public boolean sendNearestConnectedPlayer(CommandSender sender, Location location, String audioId, String trackId) {
        Player nearestPlayer = null;
        double nearestPlayerDistance = 0;
        String worldName = location.getWorld().getName();
        for (PlayerConnection playerConnection : plugin.getClient().getPlayerConnections()) {
            Player player = playerConnection.getOfflinePlayer().getPlayer();
            if (player == null || !player.getWorld().getName().equals(worldName)) {
                continue;
            }

            double distance = player.getLocation().distanceSquared(location);
            if (nearestPlayer == null || distance < nearestPlayerDistance) {
                nearestPlayer = player;
                nearestPlayerDistance = distance;
            }
        }

        if (nearestPlayer == null) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.no-near-player-connected");
            return false;
        }

        return sendPlayer(sender, nearestPlayer, audioId, trackId);
    }

    public boolean sendConnectedPlayer(CommandSender sender, String playerName, String audioId, String trackId) {
        Player player = Bukkit.getPlayer(playerName);
        if (player == null) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.player-not-connected", playerName);
            return false;
        }

        return sendConnectedPlayer(sender, player, audioId, trackId);
    }

    public boolean sendConnectedPlayer(CommandSender sender, Player player, String audioId, String trackId) {
        UUID playerId = player.getUniqueId();
        if (!plugin.getClient().isPlayerConnected(playerId)) {
            if (sender.equals(player)) {
                plugin.getMessenger().sendErrorMessage(sender, "failed.not-connected");
            } else {
                plugin.getMessenger().sendErrorMessage(sender, "failed.player-not-connected", player.getName());
            }
            return false;
        }

        return sendPlayer(sender, player, audioId, trackId);
    }

    private boolean sendPlayer(CommandSender sender, Player player, String audioId, String trackId) {
        AudioMessage audioMessage = buildAudioMessage(player, audioId, trackId);

        plugin.getClient().writeAndFlush(audioMessage);

        plugin.getMessenger().sendMessage(sender, "succeeded.audio-sent-player", audioId, player.getName());
        return true;
    }

    private AudioMessage buildAudioMessage(Player player, String audioId, String trackId) {
        AudioMessage.Builder audioMessageBuilder = AudioMessage.builder(player.getUniqueId());
        if (!audioId.equals("*")) {
            audioMessageBuilder.audio(audioId);
        }
        if (trackId != null) {
            audioMessageBuilder.track(trackId);
        }
        return audioMessageBuilder.build();
    }

}
