package com.deadmandungeons.audioconnect;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.deadmandungeons.connect.commons.StatusMessage.Status;

/**
 * Called when a the connection status for a player between the AudioConnect client, Minecraft client,
 * and the AudioConnect server changes. {@link #getStatus()} will be {@link Status#CONNECTED} if the player
 * connected to the AudioConnect server through both the AudioConnect client and Minecraft client, and thus
 * the player is online. {@link #getStatus()} will be {@link Status#DISCONNECTED} if the player disconnected
 * from the AudioConnect server through either the AudioConnect client or Minecraft client or both.
 * @author Jon
 */
public class PlayerAudioStatusEvent extends Event {
	
	private static final HandlerList handlers = new HandlerList();
	
	private final OfflinePlayer player;
	private final Status status;
	
	public PlayerAudioStatusEvent(OfflinePlayer player, Status status) {
		this.player = player;
		this.status = status;
	}
	
	public OfflinePlayer getPlayer() {
		return player;
	}
	
	public Status getStatus() {
		return status;
	}
	
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}
	
	public static HandlerList getHandlerList() {
		return handlers;
	}
	
}
