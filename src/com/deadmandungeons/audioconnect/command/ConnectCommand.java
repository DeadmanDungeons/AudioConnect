package com.deadmandungeons.audioconnect.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.deadmanplugin.command.Arguments;
import com.deadmandungeons.deadmanplugin.command.Command;
import com.deadmandungeons.deadmanplugin.command.CommandInfo;

//@formatter:off
@CommandInfo(
	name = "Connect",
	permissions = {"audioconnect.user.connect"},
	description = "Get the connection URL for your dynamic audio stream on this server",
	inGameOnly = true
)//@formatter:on
public class ConnectCommand implements Command {
	
	private final AudioConnect plugin = AudioConnect.getInstance();
	
	@Override
	public boolean execute(CommandSender sender, Arguments args) {
		Arguments.validateType(args, getClass());
		
		return execute((Player) sender);
	}
	
	/**
	 * @param player the player to execute the connect command as
	 * @return <code>true</code> if the command executed successfully, and <code>false</code> otherwise
	 */
	public boolean execute(Player player) {
		if (!plugin.getClient().isConnected()) {
			plugin.getMessenger().sendErrorMessage(player, "failed.client-disconnected");
			return false;
		}
		
		String connectUrl = plugin.getPlayerConnectUrl(player.getUniqueId());
		
		plugin.getMessenger().sendMessage(player, "misc.top-bar");
		plugin.getMessenger().sendImportantMessage(player, "misc.connect-details", connectUrl);
		plugin.getMessenger().sendMessage(player, "misc.bottom-bar");
		return true;
	}
	
}
