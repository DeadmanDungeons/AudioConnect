package com.deadmandungeons.audioconnect.client;


import java.net.URI;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.connect.messenger.CommandMessage;
import com.deadmandungeons.connect.messenger.CommandMessage.Command;
import com.deadmandungeons.connect.messenger.Messenger;
import com.deadmandungeons.connect.messenger.Messenger.Message;
import com.deadmandungeons.connect.messenger.Messenger.MessageParseException;
import com.deadmandungeons.connect.messenger.StatusMessage;
import com.deadmandungeons.connect.messenger.StatusMessage.Status;
import com.google.common.collect.Sets;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;

public class AudioConnectClientHandler extends SimpleChannelInboundHandler<Object> {
	
	private static final AudioConnect plugin = AudioConnect.getInstance();
	
	static final AttributeKey<Boolean> CONNECTION_REFUSED = AttributeKey.valueOf("connection-refused");
	
	private final Set<UUID> trackedPlayers = Sets.newConcurrentHashSet();
	private final Set<UUID> offlinePlayers = Sets.newConcurrentHashSet();
	
	private final Messenger messenger;
	private final WebSocketClientHandshaker handshaker;
	
	AudioConnectClientHandler(URI uri, Messenger messenger) {
		this.messenger = messenger;
		// Connect with V13 (RFC 6455 aka HyBi-17)
		handshaker = WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders());
	}
	
	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		handshaker.handshake(ctx.channel());
	}
	
	@Override
	public void channelRead0(final ChannelHandlerContext ctx, Object msg) {
		if (!handshaker.isHandshakeComplete()) {
			FullHttpResponse response = (FullHttpResponse) msg;
			try {
				handshaker.finishHandshake(ctx.channel(), response);
				plugin.getLogger().info("Successfully connected to AudioConnect server!");
			} catch (Exception e) {
				String responseMsg = response.content().toString(CharsetUtil.UTF_8);
				plugin.getLogger().severe("Connection with AudioConnect server refused: " + responseMsg);
				ctx.channel().attr(CONNECTION_REFUSED).set(true);
				ctx.close();
			}
			return;
		}
		if (!(msg instanceof WebSocketFrame)) {
			plugin.getLogger().warning("Recieved unexpected raw message from AudioConnect server: " + msg);
			return;
		}
		
		WebSocketFrame frame = (WebSocketFrame) msg;
		if (frame instanceof PongWebSocketFrame) {
			plugin.getLogger().info("Recieved pong from AudioConnect server");
		} else if (frame instanceof CloseWebSocketFrame) {
			CloseWebSocketFrame closeFrame = (CloseWebSocketFrame) frame;
			plugin.getLogger().info("Recieved closing from AudioConnect server: [" + closeFrame.statusCode() + "] " + closeFrame.reasonText());
			
			trackedPlayers.clear();
			offlinePlayers.clear();
			ctx.close();
		} else if (frame instanceof TextWebSocketFrame) {
			TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
			System.out.println("textFrame: " + textFrame.text());
			Message[] messages;
			try {
				messages = messenger.deserialize(textFrame.text());
			} catch (MessageParseException e) {
				plugin.getLogger().warning("Failed to parse Message recieved from AudioConnect server: " + e.getMessage());
				return;
			}
			
			for (Message message : messages) {
				if (message instanceof CommandMessage) {
					CommandMessage commandMessage = (CommandMessage) message;
					
					if (commandMessage.getData() == Command.ADD) {
						final UUID playerId = commandMessage.getId();
						trackedPlayers.add(playerId);
						
						Bukkit.getScheduler().runTask(plugin, new Runnable() {
							
							@Override
							public void run() {
								Status status = (Bukkit.getPlayer(playerId) != null ? Status.CONNECTED : Status.DISCONNECTED);
								if (status == Status.DISCONNECTED) {
									offlinePlayers.add(playerId);
								}
								String json = messenger.serialize(new StatusMessage(playerId, status));
								ctx.writeAndFlush(new TextWebSocketFrame(json));
							}
						});
					} else if (commandMessage.getData() == Command.REMOVE) {
						trackedPlayers.remove(commandMessage.getId());
						offlinePlayers.remove(commandMessage.getId());
						System.out.println("removed player: " + commandMessage.getId());
					}
				} else {
					plugin.getLogger().warning("Recieved unexpected Message type from AudioConnect server '" + message.getType() + "'");
				}
			}
		}
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		plugin.getLogger().log(Level.WARNING, "Reconnecting to AudioConnect server due to an unexpected exception", cause);
		ctx.close();
	}
	
	Set<UUID> getTrackedPlayers() {
		return trackedPlayers;
	}
	
	Set<UUID> getOfflinePlayers() {
		return offlinePlayers;
	}
	
}
