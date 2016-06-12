package com.deadmandungeons.audioconnect.command;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import com.deadmandungeons.deadmanplugin.command.Arguments;
import com.deadmandungeons.deadmanplugin.command.Command;
import com.deadmandungeons.deadmanplugin.command.CommandInfo;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.AudioConnect.Config;

//@formatter:off
@CommandInfo(
	name = "Reload",
	permissions = {"audioconnect.admin.reload"},
	inGameOnly = false,
	description = "Reload this plugin from file"
)//@formatter:on
public class ReloadCommand implements Command {
	
	private final AudioConnect plugin = AudioConnect.getInstance();
	
	@Override
	public boolean execute(CommandSender sender, Arguments args) {
		Arguments.validateType(args, getClass());
		
		plugin.reloadConfig();
		plugin.getMessenger().reload();
		
		asyncReconnect();
		
		plugin.getMessenger().sendMessage(sender, "succeeded.reloaded");
		return true;
	}
	
	private void asyncReconnect() {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
			
			@Override
			public void run() {
				plugin.getClient().disconnect();
				
				UUID userId = Config.getConnectionUserId();
				if (userId == null) {
					String property = Config.CONNECTION_USER_ID.getPath();
					plugin.getLogger().severe("The required" + property + " config property is missing or invalid! Client cannot be started...");
					return;
				}
				
				plugin.getClient().connect(userId);
			}
		});
	}
	
}
