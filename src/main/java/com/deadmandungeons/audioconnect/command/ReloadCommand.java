package com.deadmandungeons.audioconnect.command;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.deadmanplugin.command.Arguments;
import com.deadmandungeons.deadmanplugin.command.Command;
import com.deadmandungeons.deadmanplugin.command.CommandInfo;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

//@formatter:off
@CommandInfo(
    name = "Reload",
    permissions = {"audioconnect.admin.reload"},
    description = "Reload plugin files and reconnect to the AudioConnect server"
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
                plugin.getClient().disconnect().awaitUninterruptibly();

                if (!plugin.getConfiguration().validate()) {
                    return;
                }

                plugin.getClient().connect();
            }
        });
    }

}
