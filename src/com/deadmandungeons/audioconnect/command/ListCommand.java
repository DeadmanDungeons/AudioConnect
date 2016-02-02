package com.deadmandungeons.audioconnect.command;

import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.deadmandungeons.deadmanplugin.DeadmanUtils;
import org.deadmandungeons.deadmanplugin.command.ArgumentInfo;
import org.deadmandungeons.deadmanplugin.command.ArgumentInfo.ArgType;
import org.deadmandungeons.deadmanplugin.command.Arguments;
import org.deadmandungeons.deadmanplugin.command.Command;
import org.deadmandungeons.deadmanplugin.command.CommandInfo;
import org.deadmandungeons.deadmanplugin.command.SubCommandInfo;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.AudioConnectClient.TrackingInfo;

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
		
		if (!plugin.getClient().isConnected()) {
			plugin.getMessenger().sendErrorMessage(sender, "failed.not-connected-server");
			return false;
		}
		
		int pageNum = (args.getArgs().length == 1 ? (Integer) args.getArgs()[0] : 1);
		
		Set<TrackingInfo> trackedPlayers = plugin.getClient().getTrackedPlayers();
		TrackingInfo[] list = trackedPlayers.toArray(new TrackingInfo[trackedPlayers.size()]);
		
		int itemsPerPage = 10;
		int maxPage = list.length / itemsPerPage + (list.length % itemsPerPage > 0 ? 1 : 0);
		if (pageNum * itemsPerPage > list.length + (itemsPerPage - 1)) {
			pageNum = maxPage;
		}
		
		ChatColor color1 = plugin.getMessenger().getPrimaryColor();
		ChatColor color2 = plugin.getMessenger().getSecondaryColor();
		ChatColor color3 = plugin.getMessenger().getTertiaryColor();
		
		String reset = ChatColor.RESET.toString();
		String barLeft = color3 + "<" + ChatColor.STRIKETHROUGH;
		String barRight = reset + color3 + ">";
		
		String paging = (list.length > itemsPerPage ? "[pg. " + pageNum + "/" + maxPage + "] " : "");
		String barSpace = (paging.isEmpty() ? "----" : "");
		String title = reset + color2 + " Connected Players " + paging + color3;
		String topBar = "-------------" + barSpace + title + ChatColor.STRIKETHROUGH + barSpace + "-------------";
		sender.sendMessage(barLeft + topBar + barRight);
		
		if (list.length > 0) {
			sender.sendMessage(color2 + "  KEY: " + color1 + color1.name() + color2 + " = Online, " + ChatColor.RED + "RED" + color2 + " = Offline");
			sender.sendMessage("");
		} else {
			sender.sendMessage(ChatColor.RED + "  * NONE *");
		}
		
		for (int i = 0; i < list.length && i < (pageNum * itemsPerPage); i++) {
			if (i >= (pageNum - 1) * itemsPerPage) {
				long duration = System.currentTimeMillis() - list[i].getTimeConnected();
				String connectedDuration = ChatColor.DARK_GRAY + " (" + DeadmanUtils.formatDuration(duration) + ")";
				
				OfflinePlayer connectedPlayer = list[i].getPlayer();
				if (connectedPlayer.isOnline()) {
					sender.sendMessage("  " + color1 + connectedPlayer.getName() + connectedDuration);
				} else {
					String name = connectedPlayer.getName();
					sender.sendMessage("  " + ChatColor.RED + (name != null ? name : connectedPlayer.getUniqueId()) + connectedDuration);
				}
			}
		}
		
		sender.sendMessage(barLeft + "---------------------------------------------------" + barRight);
		return true;
	}
	
}
