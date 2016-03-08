package com.deadmandungeons.audioconnect.command;

import org.bukkit.command.CommandSender;
import org.deadmandungeons.deadmanplugin.command.Arguments;
import org.deadmandungeons.deadmanplugin.command.Command;
import org.deadmandungeons.deadmanplugin.command.CommandInfo;

//@formatter:off
@CommandInfo(
	name = "Register",
	description = "Initiate the registration of an AudioConnect admin account at ",
	inGameOnly = true
)//@formatter:on
public class RegisterCommand implements Command {
	
	@Override
	public boolean execute(CommandSender sender, Arguments args) {
		Arguments.validateType(args, getClass());
		
		
		return false;
	}
	
}
