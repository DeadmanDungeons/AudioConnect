package com.deadmandungeons.audioconnect.command;

import org.deadmandungeons.deadmanplugin.DeadmanPlugin;
import org.deadmandungeons.deadmanplugin.Messenger;
import org.deadmandungeons.deadmanplugin.command.DeadmanExecutor;

public class CommandHandler extends DeadmanExecutor {
	
	public CommandHandler(DeadmanPlugin plugin, Messenger messenger) {
		super(plugin, messenger);
		
		registerCommand(ListCommand.class);
		registerCommand(SendCommand.class);
		registerCommand(ReloadCommand.class);
	}
	
}
