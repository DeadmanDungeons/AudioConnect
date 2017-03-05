package com.deadmandungeons.audioconnect.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.deadmandungeons.audioconnect.AudioConnect;
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
				@ArgumentInfo(argName = "player-name", argType = ArgType.VARIABLE),
				@ArgumentInfo(argName = "audio-id", argType = ArgType.VARIABLE),
				@ArgumentInfo(argName = "track-id", argType = ArgType.OPT_VARIABLE),
			},
			description = "Play the audio identified by the given audio-id in the connected player's browser. " 
					+ "The audio will be played in the given track or the default track if otherwise not specified."
		)
	}
)//@formatter:on
public class SendCommand implements Command {
	
	private final AudioConnect plugin = AudioConnect.getInstance();
	
	@Override
	public boolean execute(CommandSender sender, Arguments args) {
		Arguments.validateType(args, getClass());
		
		if (!plugin.getClient().isConnected()) {
			plugin.getMessenger().sendErrorMessage(sender, "failed.not-connected-server");
			return false;
		}
		
		String playerName = (String) args.getArgs()[0];
		String audioId = (String) args.getArgs()[1];
		String trackId = (args.getArgs().length == 3 ? (String) args.getArgs()[2] : null);
		
		Player player = Bukkit.getPlayer(playerName);
		if (player == null || !plugin.getClient().isPlayerConnected(player.getUniqueId())) {
			plugin.getMessenger().sendErrorMessage(sender, "failed.player-not-connected", playerName);
			return false;
		}
		if (!plugin.getAudioList().contains(audioId)) {
			String reason = plugin.getMessenger().getMessage("failed.audio-not-uploaded", false);
			plugin.getMessenger().sendErrorMessage(sender, "failed.invalid-audio-id", audioId, reason);
			return false;
		}
		
		AudioMessage.Builder audioMessageBuilder = AudioMessage.builder(player.getUniqueId()).audio(audioId);
		if (trackId != null) {
			audioMessageBuilder.track(trackId);
		}
		
		plugin.getClient().writeAndFlush(audioMessageBuilder.build());
		
		plugin.getMessenger().sendMessage(sender, "succeeded.audio-msg-sent", audioId, playerName);
		return true;
	}
	
}
