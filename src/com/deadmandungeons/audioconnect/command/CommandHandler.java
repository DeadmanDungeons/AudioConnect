package com.deadmandungeons.audioconnect.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.deadmandungeons.deadmanplugin.Messenger;
import org.deadmandungeons.deadmanplugin.command.Arguments;
import org.deadmandungeons.deadmanplugin.command.Command;
import org.deadmandungeons.deadmanplugin.command.CommandInfo;
import org.deadmandungeons.deadmanplugin.command.DeadmanExecutor;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.AudioConnect.Config;
import com.deadmandungeons.connect.commons.CommandMessage;

public class CommandHandler extends DeadmanExecutor {
	
	private final AudioConnect plugin;
	
	public CommandHandler(AudioConnect plugin, Messenger messenger) {
		super(plugin, messenger, Config.COMMAND_COOLDOWN.value().intValue());
		this.plugin = plugin;
		
		registerCommand(ConnectCommand.class);
		registerCommand(ListCommand.class);
		registerCommand(SendCommand.class);
		registerCommand(ReloadCommand.class);
		registerCommand(RegisterCommand.class);
		
		registerCommand(new MuteCommand());
		registerCommand(new UnmuteCommand());
		
		CommandExecutor aliasExecutor = new AliasCommandExecutor();
		plugin.getCommand("music").setExecutor(aliasExecutor);
		plugin.getCommand("mute").setExecutor(aliasExecutor);
		plugin.getCommand("unmute").setExecutor(aliasExecutor);
	}
	
	
	private boolean controlVolume(Player player, boolean mute) {
		if (!plugin.getClient().isPlayerTracked(player.getUniqueId())) {
			plugin.getMessenger().sendErrorMessage(player, "failed.not-connected");
			return false;
		}
		
		CommandMessage.Command cmd = (mute ? CommandMessage.Command.MUTE : CommandMessage.Command.UNMUTE);
		CommandMessage message = new CommandMessage(player.getUniqueId(), cmd);
		plugin.getClient().writeAndFlush(message);
		
		plugin.getMessenger().sendMessage(player, "succeeded.volume-set", (mute ? "muted" : "unmuted"));
		return true;
	}
	
	// @formatter:off
	@CommandInfo(
		name = "Mute",
		description = "Mute the currently playing audio in your AudioConnect session",
		inGameOnly = true
	)// @formatter:on
	private class MuteCommand implements Command {
		
		@Override
		public boolean execute(CommandSender sender, Arguments args) {
			Arguments.validateType(args, getClass());
			
			return controlVolume((Player) sender, true);
		}
		
	}
	
	// @formatter:off
	@CommandInfo(
		name = "Unmute",
		description = "Unmute the currently muted audio in your AudioConnect session to the previous volume",
		inGameOnly = true
	)// @formatter:on
	private class UnmuteCommand implements Command {
		
		@Override
		public boolean execute(CommandSender sender, Arguments args) {
			Arguments.validateType(args, getClass());
			
			return controlVolume((Player) sender, false);
		}
		
	}
	
	private class AliasCommandExecutor implements CommandExecutor {
		
		@Override
		public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
			if (!(sender instanceof Player)) {
				sender.sendMessage(ChatColor.RED + "This command can only be used in game.");
				return false;
			}
			
			Player player = (Player) sender;
			if (label.equalsIgnoreCase("music")) {
				return getCommand(ConnectCommand.class).execute(player);
			} else if (label.equalsIgnoreCase("mute")) {
				return controlVolume(player, true);
			} else if (label.equalsIgnoreCase("unmute")) {
				return controlVolume(player, false);
			}
			return false;
		}
		
	}
	
}
