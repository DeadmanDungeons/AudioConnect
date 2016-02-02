package com.deadmandungeons.audioconnect.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.deadmandungeons.deadmanplugin.command.Arguments;
import org.deadmandungeons.deadmanplugin.command.Command;
import org.deadmandungeons.deadmanplugin.command.CommandInfo;

import com.deadmandungeons.audioconnect.AudioConnect;

//@formatter:off
@CommandInfo(
	name = "Connect",
	permissions = {"audioconnect.user.connect"},
	description = "Get the AudioConnect URL for your playing session on this server",
	inGameOnly = true
)//@formatter:on
public class ConnectCommand implements Command {
	
	private final AudioConnect plugin = AudioConnect.getInstance();
	
	@Override
	public boolean execute(CommandSender sender, Arguments args) {
		Arguments.validateType(args, getClass());
		
		Player player = (Player) sender;
		
		String connectUrl = plugin.getClient().getPlayerConnectUrl(player.getUniqueId());
		if (connectUrl == null) {
			plugin.getMessenger().sendErrorMessage(sender, "failed.not-connected-server");
			return false;
		}
		if (plugin.getClient().isPlayerTracked(player.getUniqueId())) {
			plugin.getMessenger().sendErrorMessage(player, "failed.already-connected");
			return false;
		}
		
		plugin.getMessenger().sendMessage(player, "misc.top-bar");
		plugin.getMessenger().sendImportantMessage(player, "misc.connect-details", connectUrl);
		plugin.getMessenger().sendMessage(player, "misc.bottom-bar");
		return true;
	}
	
}
