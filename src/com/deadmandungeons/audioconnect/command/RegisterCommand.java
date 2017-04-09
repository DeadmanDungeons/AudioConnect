package com.deadmandungeons.audioconnect.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.deadmanplugin.command.Arguments;
import com.deadmandungeons.deadmanplugin.command.Command;
import com.deadmandungeons.deadmanplugin.command.CommandInfo;

//@formatter:off
@CommandInfo(
	name = "Register",
	permissions = {"audioconnect.user.register"},
	description = "Initiate the registration of an AudioConnect account at <connection.endpoint.host>",
	inGameOnly = true
)//@formatter:on
public class RegisterCommand implements Command {
	
	private final AudioConnect plugin = AudioConnect.getInstance();
	
	@Override
	public boolean execute(CommandSender sender, Arguments args) {
		Arguments.validateType(args, getClass());
		
		Player player = (Player) sender;
		
		return registerAccount(player);
	}
	
	protected boolean registerAccount(Player player) {
		plugin.getMessenger().sendMessage(player, "misc.top-bar");
		plugin.getMessenger().sendImportantMessage(player, "misc.register-details");
		plugin.getMessenger().sendMessage(player, "misc.bottom-bar");
		return true;
	}
	
}
