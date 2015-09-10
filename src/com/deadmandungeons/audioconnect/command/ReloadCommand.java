package com.deadmandungeons.audioconnect.command;

import org.bukkit.command.CommandSender;
import org.deadmandungeons.deadmanplugin.command.Arguments;
import org.deadmandungeons.deadmanplugin.command.Command;
import org.deadmandungeons.deadmanplugin.command.CommandInfo;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.Config;

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
		
		Config.reload();
		plugin.getLangFile().reloadConfig();
		plugin.getMessenger().clearCache();
		
		plugin.getMessenger().sendMessage(sender, "succeeded.reloaded");
		return true;
	}
	
}
