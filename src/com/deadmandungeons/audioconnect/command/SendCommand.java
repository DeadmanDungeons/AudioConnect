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
import com.deadmandungeons.audioconnect.client.AudioConnectClient;
import com.deadmandungeons.audioconnect.messages.AudioMessage;


//@formatter:off
@CommandInfo(
	name = "Send",
	permissions = {"audioconnect.admin.send"},
	inGameOnly = false,
	subCommands = {
		@SubCommandInfo(
			arguments = {
				@ArgumentInfo(argName = "player-name", argType = ArgType.VARIABLE),
				@ArgumentInfo(argName = "audio-id", argType = ArgType.VARIABLE)
			}, 
			description = "Send a temporarily interrupting AudioMessage to the connected players browser"
		)
	}
)//@formatter:on
public class SendCommand implements Command {
	
	private final AudioConnect plugin = AudioConnect.getInstance();
	
	@Override
	public boolean execute(CommandSender sender, Arguments args) {
		Arguments.validateType(args, getClass());
		
		String playerName = (String) args.getArgs()[0];
		String audioId = (String) args.getArgs()[1];
		
		Player player = Bukkit.getPlayer(playerName);
		if (player == null || !AudioConnectClient.getInstance().isPlayerTracked(player.getUniqueId())) {
			plugin.getMessenger().sendErrorMessage(sender, "failed.player-not-connected", playerName);
			return false;
		}
		
		AudioMessage audioMessage = AudioMessage.createInterrupting(player.getUniqueId(), audioId);
		AudioConnectClient.getInstance().writeAndFlush(audioMessage);
		
		plugin.getMessenger().sendMessage(sender, "succeeded.audio-msg-sent", audioId, playerName);
		return true;
	}
	
}
