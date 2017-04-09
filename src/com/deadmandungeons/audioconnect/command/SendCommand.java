package com.deadmandungeons.audioconnect.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.AudioConnectClient.PlayerConnection;
import com.deadmandungeons.audioconnect.messages.AudioMessage;
import com.deadmandungeons.deadmanplugin.command.ArgumentInfo;
import com.deadmandungeons.deadmanplugin.command.ArgumentInfo.ArgType;
import com.deadmandungeons.deadmanplugin.command.Arguments;
import com.deadmandungeons.deadmanplugin.command.Command;
import com.deadmandungeons.deadmanplugin.command.CommandInfo;
import com.deadmandungeons.deadmanplugin.command.SubCommandInfo;

//@formatter:off
@CommandInfo(
	name = "Send",
	permissions = {"audioconnect.admin.send"},
	inGameOnly = false,
	subCommands = {
		@SubCommandInfo(
			arguments = {
				@ArgumentInfo(argName = "all", argType = ArgType.NON_VARIABLE),
				@ArgumentInfo(argName = "audio-id", argType = ArgType.VARIABLE),
				@ArgumentInfo(argName = "track-id", argType = ArgType.OPT_VARIABLE),
			},
			description = "Send all connected players an audio source to be played on the given track or the default track"
		),
		@SubCommandInfo(
			arguments = {
				@ArgumentInfo(argName = "player-name", argType = ArgType.VARIABLE),
				@ArgumentInfo(argName = "audio-id", argType = ArgType.VARIABLE),
				@ArgumentInfo(argName = "track-id", argType = ArgType.OPT_VARIABLE),
			},
			description = "Send a connected player an audio source to be played on the given track or the default track"
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
		String audioId = (String) args[1];
		String trackId = (args.length == 3 ? (String) args[2] : null);
		
		if (!plugin.getAudioList().contains(audioId)) {
			String reason = plugin.getMessenger().getMessage("failed.audio-not-uploaded", false);
			plugin.getMessenger().sendErrorMessage(sender, "failed.invalid-audio-id", audioId, reason);
			return false;
		}
		
		switch (arguments.getSubCmdIndex()) {
			case 0:
				return sendAllConnectedPlayers(sender, audioId, trackId);
			case 1:
				return sendConnectedPlayer(sender, (String) args[0], audioId, trackId);
		}
		
		return false;
	}
	
	public boolean sendAllConnectedPlayers(CommandSender sender, String audioId, String trackId) {
		List<AudioMessage> audioMessages = new ArrayList<>();
		for (PlayerConnection playerConnection : plugin.getClient().getPlayerConnections()) {
			OfflinePlayer player = playerConnection.getPlayer();
			if (player.isOnline()) {
				AudioMessage.Builder audioMessageBuilder = AudioMessage.builder(player.getUniqueId()).audio(audioId);
				if (trackId != null) {
					audioMessageBuilder.track(trackId);
				}
				audioMessages.add(audioMessageBuilder.build());
			}
		}
		
		if (audioMessages.isEmpty()) {
			plugin.getMessenger().sendErrorMessage(sender, "failed.no-players-connected");
			return false;
		}
		
		plugin.getClient().writeAndFlush(audioMessages.toArray(new AudioMessage[audioMessages.size()]));
		
		plugin.getMessenger().sendMessage(sender, "succeeded.audio-sent-all", audioId);
		return true;
	}
	
	private boolean sendConnectedPlayer(CommandSender sender, String playerName, String audioId, String trackId) {
		Player player = Bukkit.getPlayer(playerName);
		if (player == null || !plugin.getClient().isPlayerConnected(player.getUniqueId())) {
			plugin.getMessenger().sendErrorMessage(sender, "failed.player-not-connected", playerName);
			return false;
		}
		
		AudioMessage.Builder audioMessageBuilder = AudioMessage.builder(player.getUniqueId()).audio(audioId);
		if (trackId != null) {
			audioMessageBuilder.track(trackId);
		}
		
		plugin.getClient().writeAndFlush(audioMessageBuilder.build());
		
		plugin.getMessenger().sendMessage(sender, "succeeded.audio-sent-player", audioId, playerName);
		return true;
	}
	
}
