package com.deadmandungeons.audioconnect.command;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.command.verify.VerifyCommand;
import com.deadmandungeons.audioconnect.flags.AudioTrack;
import com.deadmandungeons.audioconnect.flags.AudioTrackFlag;
import com.deadmandungeons.audioconnect.messages.AudioCommandMessage;
import com.deadmandungeons.audioconnect.messages.AudioCommandMessage.AudioCommand;
import com.deadmandungeons.deadmanplugin.Messenger;
import com.deadmandungeons.deadmanplugin.Result;
import com.deadmandungeons.deadmanplugin.command.ArgumentConverter;
import com.deadmandungeons.deadmanplugin.command.Arguments;
import com.deadmandungeons.deadmanplugin.command.Command;
import com.deadmandungeons.deadmanplugin.command.CommandInfo;
import com.deadmandungeons.deadmanplugin.command.DeadmanExecutor;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

public class CommandHandler extends DeadmanExecutor {

    private final AudioConnect plugin;
    private final PluginCommand audioCommand;
    private final PluginCommand muteCommand;
    private final PluginCommand unmuteCommand;

    public CommandHandler(AudioConnect plugin, Messenger messenger, int commandCooldown) {
        super(plugin, messenger, commandCooldown);
        this.plugin = plugin;

        registerCommand(ConnectCommand.class);
        registerCommand(ListCommand.class);
        registerCommand(SendCommand.class);
        registerCommand(RegionCommand.class);
        registerCommand(ReloadCommand.class);
        registerCommand(RegisterCommand.class);
        registerCommand(VerifyCommand.class);
        registerCommand(ImportCommand.class);

        registerCommand(new MuteCommand());
        registerCommand(new UnmuteCommand());

        CommandExecutor aliasExecutor = new AliasCommandExecutor();
        audioCommand = plugin.getCommand("audio");
        audioCommand.setExecutor(aliasExecutor);

        muteCommand = plugin.getCommand("mute");
        muteCommand.setExecutor(aliasExecutor);

        unmuteCommand = plugin.getCommand("unmute");
        unmuteCommand.setExecutor(aliasExecutor);

        registerConverter(AudioTrack.class, new ArgumentConverter<AudioTrack>() {

            private final AudioTrackFlag audioTrackFlag = new AudioTrackFlag();

            @Override
            public Result<AudioTrack> convertCommandArg(String argName, String arg) {
                try {
                    return Result.success(audioTrackFlag.parseInput(arg));
                } catch (InvalidFlagFormat e) {
                    return Result.fail(e.getMessage());
                }
            }
        });
    }


    private boolean controlVolume(Player player, boolean mute) {
        if (!plugin.getClient().isPlayerConnected(player.getUniqueId())) {
            plugin.getMessenger().sendErrorMessage(player, "failed.not-connected");
            return false;
        }

        AudioCommand cmd = (mute ? AudioCommand.MUTE : AudioCommand.UNMUTE);
        AudioCommandMessage message = new AudioCommandMessage(player.getUniqueId(), cmd);
        plugin.getClient().writeAndFlush(message);

        plugin.getMessenger().sendMessage(player, "succeeded.volume-set", (mute ? "muted" : "unmuted"));
        return true;
    }

    // @formatter:off
    @CommandInfo(
        name = "Mute",
        permissions = {"audioconnect.user.mute"},
        description = "Mute all currently playing audio in your audio stream for this server",
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
        permissions = {"audioconnect.user.unmute"},
        description = "Unmute the currently muted audio in your audio stream to the previous volume",
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
            if (command.equals(audioCommand)) {
                return getCommand(ConnectCommand.class).execute(player);
            } else if (command.equals(muteCommand)) {
                return controlVolume(player, true);
            } else if (command.equals(unmuteCommand)) {
                return controlVolume(player, false);
            }
            return false;
        }

    }

}
