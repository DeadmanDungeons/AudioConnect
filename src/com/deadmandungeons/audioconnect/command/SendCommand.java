package com.deadmandungeons.audioconnect.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.deadmandungeons.deadmanplugin.command.ArgumentInfo;
import org.deadmandungeons.deadmanplugin.command.ArgumentInfo.ArgType;
import org.deadmandungeons.deadmanplugin.command.Arguments;
import org.deadmandungeons.deadmanplugin.command.Command;
import org.deadmandungeons.deadmanplugin.command.CommandInfo;
import org.deadmandungeons.deadmanplugin.command.SubCommandInfo;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.messages.AudioMessage;
import com.deadmandungeons.audioconnect.messages.AudioMessage.AudioFile;
import com.deadmandungeons.connect.commons.Result;

//@formatter:off
@CommandInfo(
	name = "Send",
	permissions = {"audioconnect.admin.send"},
	inGameOnly = false,
	subCommands = {
		@SubCommandInfo(
			arguments = {
				@ArgumentInfo(argName = "player-name", argType = ArgType.VARIABLE),
				@ArgumentInfo(argName = "file-name", argType = ArgType.VARIABLE)
			},
			description = "Send an AudioMessage to the connected players browser. " 
					+ "The played audio will overlap with any currently playing audio"
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
		String fileName = (String) args.getArgs()[1];
		
		Player player = Bukkit.getPlayer(playerName);
		if (player == null || !plugin.getClient().isPlayerTracked(player.getUniqueId())) {
			plugin.getMessenger().sendErrorMessage(sender, "failed.player-not-connected", playerName);
			return false;
		}
		Result<AudioFile> parseResult = AudioFile.fromFileName(fileName);
		if (parseResult.isError()) {
			plugin.getMessenger().sendErrorMessage(sender, "failed.invalid-file-name", fileName, parseResult.getErrorMessage());
			return false;
		}
		
		
		AudioMessage audioMessage = AudioMessage.builder(player.getUniqueId()).audio(parseResult.getResult()).build();
		plugin.getClient().writeAndFlush(audioMessage);
		
		plugin.getMessenger().sendMessage(sender, "succeeded.audio-msg-sent", fileName, playerName);
		return true;
	}
	
}
