package com.deadmandungeons.audioconnect.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.scheduler.BukkitTask;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.AudioConnectConfig;
import com.deadmandungeons.audioconnect.messages.AudioConnectUtils;
import com.deadmandungeons.connect.commons.ConnectUtils;
import com.deadmandungeons.deadmanplugin.DeadmanUtils;
import com.deadmandungeons.deadmanplugin.command.Arguments;
import com.deadmandungeons.deadmanplugin.command.Command;
import com.deadmandungeons.deadmanplugin.command.CommandInfo;
import com.google.common.io.BaseEncoding;

//@formatter:off
@CommandInfo(
	name = "Verify",
	permissions = {"audioconnect.admin.verify"},
	description = "Initiate ownership verification for this server at <connection.endpoint.host>"
)//@formatter:on
public class VerifyCommand implements Command, Listener {
	
	private static final String SERVER_VERIFY_PATH = "/admin/servers/%s/verify";
	private static final long SERVER_VERIFY_EXPIRE_MILLIS = TimeUnit.MINUTES.toMillis(10);
	private static final BaseEncoding CREDENTIALS_ENCODING = BaseEncoding.base64().omitPadding();
	
	private final AudioConnect plugin = AudioConnect.getInstance();
	
	private boolean registeredListener;
	private String encodedVerifyCode;
	private BukkitTask verifyExpireTask;
	
	@Override
	public boolean execute(CommandSender sender, Arguments args) {
		Arguments.validateType(args, getClass());
		
		AudioConnectConfig config = plugin.getConfiguration();
		if (!config.validate()) {
			plugin.getMessenger().sendErrorMessage(sender, "failed.verify-invalid-config");
			return false;
		}
		
		if (!registeredListener) {
			Bukkit.getPluginManager().registerEvents(this, plugin);
			registeredListener = true;
		}
		
		Bukkit.getScheduler().runTaskAsynchronously(plugin, new VerifyTask(sender, config));
		return true;
	}
	
	
	@EventHandler
	private void onServerPing(ServerListPingEvent event) {
		if (encodedVerifyCode != null) {
			event.setMotd(encodedVerifyCode + ChatColor.RESET + event.getMotd());
		}
	}
	
	
	private void setVerifyCode(String verifyCode, long expireDelayMillis) {
		if (verifyExpireTask != null) {
			verifyExpireTask.cancel();
		}
		
		// Encode the verify code using the Minecraft formatting codes (ChatColor)
		// so that it is invisible when injected into the MOTD during server ping
		encodedVerifyCode = AudioConnectUtils.encodeFormattingCodes(verifyCode);
		verifyExpireTask = Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
			
			@Override
			public void run() {
				encodedVerifyCode = null;
				verifyExpireTask = null;
			}
		}, expireDelayMillis / 50); // 50 milliseconds per tick
	}
	
	
	private class VerifyTask implements Runnable {
		
		private static final String USER_AGENT = "AudioConnect";
		
		private final UUID playerId;
		
		private final String credentials;
		private final URL verifyUrl;
		
		private VerifyTask(CommandSender sender, AudioConnectConfig config) {
			this.playerId = (sender instanceof Player ? ((Player) sender).getUniqueId() : null);
			
			String rawCredentials = config.getConnectionUserId() + ":" + config.getConnectionUserPassword();
			credentials = CREDENTIALS_ENCODING.encode(rawCredentials.getBytes(StandardCharsets.UTF_8));
			
			try {
				URL baseUrl = config.getConnectionWebappUrl();
				UUID serverId = config.getConnectionServerId();
				
				verifyUrl = new URL(baseUrl + String.format(SERVER_VERIFY_PATH, ConnectUtils.encodeUuidBase64(serverId)));
			} catch (MalformedURLException e) {
				throw new IllegalStateException("Invalid server verify URL", e);
			}
		}
		
		@Override
		public void run() {
			try {
				HttpURLConnection connection = (HttpURLConnection) verifyUrl.openConnection();
				
				connection.setRequestMethod("POST");
				connection.setRequestProperty("User-Agent", USER_AGENT);
				connection.setRequestProperty("Authorization", "Basic " + credentials);
				connection.setUseCaches(false);
				connection.setDoOutput(true);
				
				int responseCode = connection.getResponseCode();
				if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
					sendErrorMessageSync("failed.verify-response-404");
					return;
				} else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
					sendErrorMessageSync("failed.verify-response-401");
					return;
				} else if (responseCode != HttpURLConnection.HTTP_OK) {
					sendErrorMessageSync("failed.verify-response", responseCode);
					return;
				}
				
				String response;
				try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
					// Read only the first line (server should only respond with an error message or the verify code)
					response = in.readLine();
				}
				
				long expireTimeDefault = System.currentTimeMillis() + SERVER_VERIFY_EXPIRE_MILLIS;
				final long expireTime = connection.getHeaderFieldDate("Expires", expireTimeDefault);
				
				final String verifyCode = response;
				
				// Set verifyCode and message player on main thread
				Bukkit.getScheduler().runTask(plugin, new Runnable() {
					
					@Override
					public void run() {
						long expireDelay = expireTime - System.currentTimeMillis();
						
						setVerifyCode(verifyCode, expireDelay);
						
						CommandSender sender = getSender();
						String expireDelayFormatted = DeadmanUtils.formatDuration(expireDelay);
						if (sender instanceof Player) {
							plugin.getMessenger().sendMessage(sender, "misc.top-bar");
							plugin.getMessenger().sendImportantMessage(sender, "misc.verify-details", verifyUrl, expireDelayFormatted);
							plugin.getMessenger().sendMessage(sender, "misc.bottom-bar");
						} else {
							plugin.getMessenger().sendImportantMessage(sender, "misc.verify-details", verifyUrl, expireDelayFormatted);
						}
					}
				});
			} catch (IOException e) {
				sendErrorMessageSync("failed.verify-error");
				String errorMsg = "Failed to verify server at " + verifyUrl;
				plugin.getLogger().log(Level.SEVERE, errorMsg, e);
			}
		}
		
		private void sendErrorMessageSync(final String path, final Object... vars) {
			// Handle sending messages to the player on the main thread
			Bukkit.getScheduler().runTask(plugin, new Runnable() {
				
				@Override
				public void run() {
					plugin.getMessenger().sendErrorMessage(getSender(), path, vars);
				}
			});
		}
		
		private CommandSender getSender() {
			if (playerId != null) {
				Player player = Bukkit.getPlayer(playerId);
				if (player != null && player.isOnline()) {
					return player;
				}
			}
			return Bukkit.getConsoleSender();
		}
		
	}
	
}
