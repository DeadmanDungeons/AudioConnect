package com.deadmandungeons.audioconnect.command;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.connect.commons.ConnectUtils;
import com.deadmandungeons.deadmanplugin.DeadmanUtils;
import com.deadmandungeons.deadmanplugin.command.ArgumentInfo;
import com.deadmandungeons.deadmanplugin.command.ArgumentInfo.ArgType;
import com.deadmandungeons.deadmanplugin.command.Arguments;
import com.deadmandungeons.deadmanplugin.command.Command;
import com.deadmandungeons.deadmanplugin.command.CommandInfo;
import com.deadmandungeons.deadmanplugin.command.SubCommandInfo;

//@formatter:off
@CommandInfo(
	name = "Register",
	inGameOnly = true,
	subCommands = {
		@SubCommandInfo(
			permissions = {"audioconnect.user.register"},
			description = "Initiate the registration of an AudioConnect account at <connection.endpoint.host>"
		),
		@SubCommandInfo(
			arguments = {
				@ArgumentInfo(argName = "server", argType = ArgType.NON_VARIABLE)
			},
			permissions = {"audioconnect.admin.register"},
			description = "Initiate the registration of this server with your AudioConnect account at <connection.endpoint.host>"
		)
	}
)//@formatter:on
public class RegisterCommand implements Command {
	
	private static final int REGISTER_COOLDOWN = 300;
	private static final String USER_AGENT = "AudioConnect/Registration";
	
	private final AudioConnect plugin = AudioConnect.getInstance();
	
	@Override
	public boolean execute(CommandSender sender, Arguments args) {
		Arguments.validateType(args, getClass());
		
		Player player = (Player) sender;
		
		switch (args.getSubCmdIndex()) {
			case 0:
				return registerAccount(player);
			case 1:
				return registerServer(player);
		}
		
		return false;
	}
	
	protected boolean registerAccount(Player player) {
		plugin.getMessenger().sendMessage(player, "misc.top-bar");
		plugin.getMessenger().sendImportantMessage(player, "misc.register-details");
		plugin.getMessenger().sendMessage(player, "misc.bottom-bar");
		return true;
	}
	
	protected boolean registerServer(final Player player) {
		Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
			
			@Override
			public void run() {
				try {
					URL registerUrl = new URL(plugin.getConfiguration().getConnectionWebappUrl() + "/admin/supplier/register");
					HttpURLConnection connection = (HttpURLConnection) registerUrl.openConnection();
					
					connection.setRequestMethod("POST");
					connection.setRequestProperty("User-Agent", USER_AGENT);
					connection.setUseCaches(false);
					connection.setDoOutput(true);
					
					String encodedUserId = ConnectUtils.encodeUuidBase64(player.getUniqueId());
					String data = "user-id=" + encodedUserId + "&key=";
					
					try (DataOutputStream output = new DataOutputStream(connection.getOutputStream())) {
						output.writeBytes(data);
					}
					
					int responseCode = connection.getResponseCode();
					
					// If the user is already registered
					if (responseCode == HttpURLConnection.HTTP_CONFLICT) {
						plugin.getMessenger().sendErrorMessage(player, "failed.registration-conflict");
						return;
					}
					if (responseCode != HttpURLConnection.HTTP_OK) {
						plugin.getMessenger().sendErrorMessage(player, "failed.unexpected-response", responseCode, "");
						return;
					}
					
					StringBuilder response = new StringBuilder();
					try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
						String inputLine;
						while ((inputLine = in.readLine()) != null) {
							response.append(inputLine);
						}
					}
					
					long now = System.currentTimeMillis();
					long expireDate = connection.getHeaderFieldDate("Expires", now + (REGISTER_COOLDOWN * 1000));
					UUID token = ConnectUtils.parseUuid(response.toString());
					if (token == null) {
						plugin.getMessenger().sendErrorMessage(player, "failed.unexpected-response", responseCode, response);
						return;
					}
					
					String audioConnectUrl = plugin.getConfiguration().getConnectionWebappUrl().toString();
					String finalStepUrl = audioConnectUrl + "?token=" + ConnectUtils.encodeUuidBase64(token);
					String expireTime = DeadmanUtils.formatDuration(expireDate - now);
					plugin.getMessenger().sendImportantMessage(player, "succeeded.registration-initiated", player.getName(), finalStepUrl,
							expireTime);
				} catch (IOException e) {
					plugin.getMessenger().sendErrorMessage(player, "failed.unexpected-error");
					String errorMsg = "Failed to initiate registration for user '" + player.getName() + "' (" + player.getUniqueId() + ")";
					plugin.getLogger().log(Level.SEVERE, errorMsg, e);
				}
			}
		});
		return true;
	}
	
}
