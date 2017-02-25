package com.deadmandungeons.audioconnect.command;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.AudioConnectClient.PlayerConnection;
import com.deadmandungeons.deadmanplugin.DeadmanUtils;
import com.deadmandungeons.deadmanplugin.command.ArgumentInfo;
import com.deadmandungeons.deadmanplugin.command.ArgumentInfo.ArgType;
import com.deadmandungeons.deadmanplugin.command.Arguments;
import com.deadmandungeons.deadmanplugin.command.Command;
import com.deadmandungeons.deadmanplugin.command.CommandInfo;
import com.deadmandungeons.deadmanplugin.command.SubCommandInfo;

//@formatter:off
@CommandInfo(
	name = "List",
	permissions = {"audioconnect.admin.list"},
	inGameOnly = false,
	subCommands = {
		@SubCommandInfo(
			arguments = {
				@ArgumentInfo(argName = "players", argType = ArgType.NON_VARIABLE),
				@ArgumentInfo(argName = "page", argType = ArgType.OPT_VARIABLE, varType = Integer.class)
			},
			description = "List all players that are connected from the web client"
		),
		@SubCommandInfo(
			arguments = {
				@ArgumentInfo(argName = "audio", argType = ArgType.NON_VARIABLE),
				@ArgumentInfo(argName = "page", argType = ArgType.OPT_VARIABLE, varType = Integer.class)
			},
			description = "List all the available audio IDs for files that have been uploaded to your AudioConnect account"
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
		
		int pageNum = (args.getArgs().length == 2 ? (Integer) args.getArgs()[1] : 1);
		
		if (args.getSubCmdIndex() == 0) {
			listPlayers(sender, pageNum);
			return true;
		} else if (args.getSubCmdIndex() == 1) {
			listAudio(sender, pageNum);
			return true;
		}
		return false;
	}
	
	
	private void listPlayers(CommandSender sender, int pageNum) {
		Collection<PlayerConnection> connectedPlayers = plugin.getClient().getPlayerConnections();
		PlayerConnection[] list = connectedPlayers.toArray(new PlayerConnection[connectedPlayers.size()]);
		Arrays.sort(list, new Comparator<PlayerConnection>() {
			// Sort players alphabetically. Players with a known username appear first.
			@Override
			public int compare(PlayerConnection a, PlayerConnection b) {
				String aName = a.getPlayer().getName(), bName = b.getPlayer().getName();
				if (aName != null && bName != null) {
					return aName.compareTo(bName);
				} else if (aName == null && bName == null) {
					return a.getPlayer().getUniqueId().compareTo(b.getPlayer().getUniqueId());
				} else if (aName == null) {
					return 1;
				} else {
					return -1;
				}
			}
		});
		
		int itemsPerPage = 10;
		int maxPage = list.length / itemsPerPage + (list.length % itemsPerPage > 0 ? 1 : 0);
		if (pageNum * itemsPerPage > list.length + (itemsPerPage - 1)) {
			pageNum = maxPage;
		}
		
		ChatColor color1 = plugin.getMessenger().getPrimaryColor();
		ChatColor color2 = plugin.getMessenger().getSecondaryColor();
		ChatColor color3 = plugin.getMessenger().getTertiaryColor();
		String reset = ChatColor.RESET.toString();
		
		String paging = (list.length > itemsPerPage ? "[pg. " + pageNum + "/" + maxPage + "] " : "");
		String barSpace = (paging.isEmpty() ? "----" : "");
		String title = reset + color2 + " Connected Players " + paging + color3;
		String topBar = "-------------" + barSpace + title + ChatColor.STRIKETHROUGH + barSpace + "-------------";
		sender.sendMessage(color3 + "<" + ChatColor.STRIKETHROUGH + topBar + reset + color3 + ">");
		
		if (list.length > 0) {
			sender.sendMessage(color2 + "  KEY: " + color1 + color1.name() + color2 + " = Online, " + ChatColor.RED + "RED" + color2 + " = Offline");
			sender.sendMessage("");
		} else {
			sender.sendMessage(ChatColor.RED + "  * NONE *");
		}
		
		for (int i = 0; i < list.length && i < (pageNum * itemsPerPage); i++) {
			if (i >= (pageNum - 1) * itemsPerPage) {
				long duration = System.currentTimeMillis() - list[i].getConnectionTimestamp();
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
		
		plugin.getMessenger().sendMessage(sender, "misc.bottom-bar");
	}
	
	private void listAudio(CommandSender sender, int pageNum) {
		Set<String> audioIds = plugin.getAudioList().getAudioIds();
		String[] list = audioIds.toArray(new String[audioIds.size()]);
		Arrays.sort(list);
		
		int itemsPerPage = 10;
		int maxPage = list.length / itemsPerPage + (list.length % itemsPerPage > 0 ? 1 : 0);
		if (pageNum * itemsPerPage > list.length + (itemsPerPage - 1)) {
			pageNum = maxPage;
		}
		
		ChatColor color1 = plugin.getMessenger().getPrimaryColor();
		ChatColor color2 = plugin.getMessenger().getSecondaryColor();
		ChatColor color3 = plugin.getMessenger().getTertiaryColor();
		String reset = ChatColor.RESET.toString();
		
		String paging = (list.length > itemsPerPage ? "[pg. " + pageNum + "/" + maxPage + "] " : "");
		String barSpace = (paging.isEmpty() ? "-----" : "");
		String title = reset + color2 + " Audio List " + paging + color3;
		String topBar = "----------------" + barSpace + title + ChatColor.STRIKETHROUGH + barSpace + "----------------";
		sender.sendMessage(color3 + "<" + ChatColor.STRIKETHROUGH + topBar + reset + color3 + ">");
		
		if (list.length == 0) {
			sender.sendMessage(ChatColor.RED + "  * NONE *");
		}
		
		for (int i = 0; i < list.length && i < (pageNum * itemsPerPage); i++) {
			if (i >= (pageNum - 1) * itemsPerPage) {
				sender.sendMessage("  " + color1 + list[i]);
			}
		}
		
		plugin.getMessenger().sendMessage(sender, "misc.bottom-bar");
	}
	
}
