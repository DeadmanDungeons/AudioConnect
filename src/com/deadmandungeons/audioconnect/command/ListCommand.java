package com.deadmandungeons.audioconnect.command;

import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.deadmandungeons.deadmanplugin.PlayerID;
import org.deadmandungeons.deadmanplugin.command.ArgumentInfo;
import org.deadmandungeons.deadmanplugin.command.ArgumentInfo.ArgType;
import org.deadmandungeons.deadmanplugin.command.Arguments;
import org.deadmandungeons.deadmanplugin.command.Command;
import org.deadmandungeons.deadmanplugin.command.CommandInfo;
import org.deadmandungeons.deadmanplugin.command.SubCommandInfo;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.client.AudioConnectClient;


//@formatter:off
@CommandInfo(
	name = "List",
	permissions = {"audioconnect.admin.list"},
	inGameOnly = false,
	subCommands = {
		@SubCommandInfo(
			arguments = {
				@ArgumentInfo(argName = "page", argType = ArgType.OPT_VARIABLE, varType = Integer.class)
			},
			description = "List all online players that are connected to AudioConnect"
		)
	}
)//@formatter:on
public class ListCommand implements Command {
	
	private final AudioConnect plugin = AudioConnect.getInstance();
	
	@Override
	public boolean execute(CommandSender sender, Arguments args) {
		Arguments.validateType(args, getClass());
		
		int pageNum = (args.getArgs().length == 1 ? (Integer) args.getArgs()[0] : 1);
		
		Set<PlayerID> trackedPlayers = AudioConnectClient.getInstance().getTrackedPlayers();
		PlayerID[] list = trackedPlayers.toArray(new PlayerID[trackedPlayers.size()]);
		
		int itemsPerPage = 10;
		int maxPage = list.length / itemsPerPage + (list.length % itemsPerPage > 0 ? 1 : 0);
		if (pageNum * itemsPerPage > list.length + (itemsPerPage - 1)) {
			pageNum = maxPage;
		}
		
		ChatColor primaryColor = plugin.getMessenger().getPrimaryColor();
		ChatColor tertiaryColor = plugin.getMessenger().getTertiaryColor();
		
		String paging = (list.length > itemsPerPage ? " [pg. " + pageNum + "/" + maxPage + "]" : "");
		String listTitle = primaryColor + "Connected Players" + paging + tertiaryColor;
		sender.sendMessage(tertiaryColor + "<=============== " + listTitle + " ===============>");
		for (int i = 0; i < list.length && i < (pageNum * itemsPerPage); i++) {
			if (i >= (pageNum - 1) * itemsPerPage) {
				sender.sendMessage("  " + list[i].toString());
			}
		}
		sender.sendMessage(tertiaryColor + "<==================================================>");
		return true;
	}
	
}
