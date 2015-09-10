package com.deadmandungeons.audioconnect;

import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.deadmandungeons.deadmanplugin.DeadmanPlugin;
import org.deadmandungeons.deadmanplugin.DeadmanUtils;
import org.deadmandungeons.deadmanplugin.Messenger;
import org.deadmandungeons.deadmanplugin.filedata.PluginFile;

import com.deadmandungeons.audioconnect.client.AudioConnectClient;
import com.deadmandungeons.audioconnect.command.CommandHandler;
import com.deadmandungeons.audioconnect.messages.AudioMessage;
import com.google.common.collect.Sets;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.util.Locations;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;


public class AudioConnect extends DeadmanPlugin implements Listener {
	
	private static final String TRACKING_DATA_METADATA = "AC:tracking-data";
	private static final String AUDIO_REGION_ID_PREFIX = "audio-";
	private static final int REGION_CHECK_DELAY = 3000;
	
	private PluginFile langFile;
	
	private Messenger messenger;
	
	public static AudioConnect getInstance() {
		return getDeadmanPlugin(AudioConnect.class);
	}
	
	
	@Override
	protected void onPluginEnable() {
		saveDefaultConfig();
		langFile = new PluginFile(this, LANG_DIRECTORY + "english.yml", "english.yml");
		messenger = new Messenger(this, langFile);
		
		getServer().getPluginManager().registerEvents(this, this);
		getCommand("ac").setExecutor(new CommandHandler(this, messenger));
		
		AudioConnectClient.getInstance().connect();
	}
	
	@Override
	protected void onPluginDisable() {
		AudioConnectClient.getInstance().disconnect();
	}
	
	
	public PluginFile getLangFile() {
		return langFile;
	}
	
	public Messenger getMessenger() {
		return messenger;
	}
	
	
	@EventHandler
	private void onPlayerJoin(PlayerJoinEvent event) {
		UUID playerId = event.getPlayer().getUniqueId();
		AudioConnectClient.getInstance().notifyPlayerJoin(playerId);
	}
	
	@EventHandler
	private void onPlayerQuit(PlayerQuitEvent event) {
		UUID playerId = event.getPlayer().getUniqueId();
		AudioConnectClient.getInstance().notifyPlayerQuit(playerId);
	}
	
	@EventHandler
	private void onPlayerChangeAudioRegion(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		if (AudioConnectClient.getInstance().isPlayerTracked(player.getUniqueId())) {
			Location to = event.getTo();
			PlayerTrackingData trackingData = DeadmanUtils.getMetadata(this, player, TRACKING_DATA_METADATA, PlayerTrackingData.class);
			if (trackingData == null) {
				ApplicableRegionSet regionSet = WorldGuardPlugin.inst().getRegionManager(player.getWorld()).getApplicableRegions(to);
				trackingData = new PlayerTrackingData(to, regionSet.getRegions());
				player.setMetadata(TRACKING_DATA_METADATA, new FixedMetadataValue(this, trackingData));
				return;
			}
			
			// TODO if a player moves to a new audio region but does not move again while
			// during the delay time, the new audio wont play until they move again.
			long now = System.currentTimeMillis();
			if (trackingData.lastCheckTimestamp + REGION_CHECK_DELAY < now) {
				trackingData.lastCheckTimestamp = now;
				
				if (Locations.isDifferentBlock(trackingData.lastLocation, to)) {
					ApplicableRegionSet toSet = WorldGuardPlugin.inst().getRegionManager(player.getWorld()).getApplicableRegions(to);
					
					Set<ProtectedRegion> exitedRegions = Sets.difference(trackingData.lastRegionSet, toSet.getRegions());
					Set<ProtectedRegion> enteredRegions = Sets.difference(toSet.getRegions(), trackingData.lastRegionSet);
					
					ProtectedRegion previous = trackingData.currentAudioRegion;
					if (exitedRegions.contains(trackingData.currentAudioRegion)) {
						trackingData.currentAudioRegion = getAudioRegion(toSet.getRegions());
					}
					ProtectedRegion entered = getAudioRegion(enteredRegions);
					ProtectedRegion current = trackingData.currentAudioRegion;
					if (entered != null && (current == null || entered.getPriority() > current.getPriority())) {
						trackingData.currentAudioRegion = entered;
					}
					
					if (trackingData.currentAudioRegion != previous) {
						String audioId = null;
						if (trackingData.currentAudioRegion != null) {
							audioId = trackingData.currentAudioRegion.getId().substring(AUDIO_REGION_ID_PREFIX.length());
						}
						
						AudioMessage message = AudioMessage.create(player.getUniqueId(), audioId);
						AudioConnectClient.getInstance().writeAndFlush(message);
					}
					
					trackingData.lastLocation = to;
					trackingData.lastRegionSet = toSet.getRegions();
				}
			}
		}
	}
	
	private ProtectedRegion getAudioRegion(Set<ProtectedRegion> regions) {
		ProtectedRegion audioRegion = null;
		for (ProtectedRegion region : regions) {
			if (region.getId().startsWith(AUDIO_REGION_ID_PREFIX)) {
				if (audioRegion == null || region.getPriority() > audioRegion.getPriority()) {
					audioRegion = region;
				}
			}
		}
		return audioRegion;
	}
	
	
	private static class PlayerTrackingData {
		
		private long lastCheckTimestamp;
		private Location lastLocation;
		private Set<ProtectedRegion> lastRegionSet;
		private ProtectedRegion currentAudioRegion;
		
		private PlayerTrackingData(Location lastLocation, Set<ProtectedRegion> lastRegionSet) {
			this.lastLocation = lastLocation;
			this.lastRegionSet = lastRegionSet;
		}
	}
	
}
