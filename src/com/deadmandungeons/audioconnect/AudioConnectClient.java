package com.deadmandungeons.audioconnect;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLException;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import com.deadmandungeons.audioconnect.AudioConnectConfig.AudioTrackSettings;
import com.deadmandungeons.audioconnect.PlayerScheduler.PlayerDataWriter;
import com.deadmandungeons.audioconnect.messages.AudioListMessage;
import com.deadmandungeons.audioconnect.messages.AudioMessage;
import com.deadmandungeons.audioconnect.messages.AudioTrackMessage;
import com.deadmandungeons.connect.commons.ConnectUtils;
import com.deadmandungeons.connect.commons.HeartbeatMessage;
import com.deadmandungeons.connect.commons.Messenger;
import com.deadmandungeons.connect.commons.Messenger.Message;
import com.deadmandungeons.connect.commons.Messenger.MessageParseException;
import com.deadmandungeons.connect.commons.StatusMessage;
import com.deadmandungeons.connect.commons.StatusMessage.Status;
import com.google.common.collect.Sets;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.AttributeKey;

public class AudioConnectClient {
	
	private static final AttributeKey<String> CONNECTION_SUPPLIER_ID = AttributeKey.valueOf("connection-supplier-id");
	private static final AttributeKey<Boolean> CONNECTION_REFUSED = AttributeKey.valueOf("connection-refused");
	
	private final Plugin plugin;
	private final AudioConnectConfig config;
	private final AudioList audioList;
	private final PlayerAudioDataWriter dataWriter;
	
	private final PlayerScheduler scheduler; // only accessed on main MC thread
	private final Messenger messenger;
	private final Logger logger;
	
	private final AtomicInteger reconnectAttempts = new AtomicInteger();
	private volatile Connection connection;
	
	public AudioConnectClient(Plugin plugin, AudioConnectConfig config, AudioList audioList, PlayerAudioDataWriter dataWriter) {
		this.plugin = plugin;
		this.config = config;
		this.audioList = audioList;
		this.dataWriter = dataWriter;
		
		// execute every 20 ticks (1 second) with max of 5 displaced scheduler tasks
		scheduler = new PlayerScheduler(plugin, dataWriter, 20, 5);
		messenger = Messenger.builder().registerMessageType(AudioMessage.CREATOR).registerMessageType(AudioListMessage.CREATOR)
				.registerMessageType(AudioTrackMessage.CREATOR).build();
		logger = plugin.getLogger();
	}
	
	
	public synchronized boolean isConnected() {
		return connection != null;
	}
	
	public synchronized boolean isPlayerConnected(UUID playerId) {
		return connection != null && connection.handler.connectedPlayers.containsKey(playerId);
	}
	
	public synchronized String getSupplierId() {
		return (connection != null ? connection.channel.attr(CONNECTION_SUPPLIER_ID).get() : null);
	}
	
	public synchronized Set<ConnectedPlayer> getConnectedPlayers() {
		Set<ConnectedPlayer> connectedPlayers = new HashSet<>();
		if (connection != null) {
			for (ConnectedPlayer connectedPlayer : connection.handler.connectedPlayers.values()) {
				connectedPlayers.add(connectedPlayer);
			}
		}
		return connectedPlayers;
	}
	
	/**
	 * @param playerId - The unique ID of the player to include as the tracked player in the returned connect URL
	 * @return the URL to the AudioConnect client for the given player or <code>null</code> if {@link #isConnected()} returns false
	 */
	public synchronized String getPlayerConnectUrl(UUID playerId) {
		String supplierId = getSupplierId();
		if (supplierId != null) {
			String encodedPlayerId = ConnectUtils.encodeUuidBase64(playerId);
			String protocol = (config.isConnectionSecure() ? "https" : "http");
			String host = config.getConnectionHost();
			String path = "/audio-connect/connect";
			String query = "?sid=" + supplierId + "&cid=" + encodedPlayerId;
			
			return protocol + "://" + host + path + query;
		}
		return null;
	}
	
	public synchronized void notifyPlayerJoin(Player player) {
		UUID playerId = player.getUniqueId();
		if (connection != null && connection.handler.offlinePlayers.remove(playerId)) {
			scheduler.addPlayer(playerId);
			
			writeAndFlush(createInitialPayload(player));
			
			Bukkit.getPluginManager().callEvent(new PlayerAudioStatusEvent(player, Status.CONNECTED));
		}
	}
	
	public synchronized void notifyPlayerQuit(Player player) {
		UUID playerId = player.getUniqueId();
		if (isPlayerConnected(playerId) && connection.handler.offlinePlayers.add(playerId)) {
			Status status = Status.DISCONNECTED;
			writeAndFlush(new StatusMessage(playerId, status));
			scheduler.removePlayer(playerId);
			
			Bukkit.getPluginManager().callEvent(new PlayerAudioStatusEvent(player, status));
		}
	}
	
	public synchronized void writeAndFlush(Message... messages) {
		if (connection == null) {
			throw new IllegalStateException("not connected to AudioConnect server");
		}
		
		writeAndFlush(connection.channel, messages);
	}
	
	public synchronized void connect() {
		URI uri = config.getConnectionEndpointUri();
		UUID userId = config.getConnectionUserId();
		
		connect(uri, userId, false);
	}
	
	private void connect(final URI uri, final UUID userId, boolean reconnectAttempt) {
		if (connection != null) {
			throw new IllegalStateException("already connected");
		}
		if (!reconnectAttempt) {
			reconnectAttempts.set(0);
		}
		
		logger.info("Connecting to AudioConnect server [" + uri + "] ...");
		
		SslContext sslContext = null;
		if (config.isConnectionSecure()) {
			try {
				sslContext = SslContext.newClientContext(InsecureTrustManagerFactory.INSTANCE);
			} catch (SSLException e) {
				logger.log(Level.SEVERE, "Shutting down client event loop due to unexpected failure to create SSL context", e);
				return;
			}
		}
		
		connection = createConnection(uri, userId, sslContext);
		
		connection.channel.closeFuture().addListener(new ChannelFutureListener() {
			
			@Override
			public void operationComplete(ChannelFuture future) {
				synchronized (AudioConnectClient.this) {
					if (connection == null) {
						return;
					}
					Boolean connectionWasRefused = connection.channel.attr(CONNECTION_REFUSED).get();
					if (connectionWasRefused != null && connectionWasRefused) {
						logger.info("Shutting down client event loop due to unexpected errors while connecting with AudioConnect server");
						shutdown();
						return;
					}
					int maxReconnectAttempts = config.getReconnectMaxAttempts();
					if (maxReconnectAttempts > 0 && reconnectAttempts.get() >= maxReconnectAttempts) {
						logger.info("The maximum amount of reconnect attempts have been made. Shutting down client event loop");
						shutdown();
						return;
					}
					
					clearPlayers();
					connection = null;
					
					int interval = config.getReconnectInterval();
					double delayRate = config.getReconnectDelay();
					int delay = (int) (interval * Math.pow(delayRate, reconnectAttempts.get()));
					int maxInterval = config.getReconnectMaxInterval();
					if (delay > maxInterval) {
						delay = maxInterval;
					}
					logger.warning("Disconnected from AudioConnect server. Reattempting connection in " + (delay / 1000) + " seconds");
					future.channel().eventLoop().schedule(new Runnable() {
						
						@Override
						public void run() {
							synchronized (AudioConnectClient.this) {
								reconnectAttempts.incrementAndGet();
								shutdown();
								connect(uri, userId, true);
							}
						}
					}, delay, TimeUnit.MILLISECONDS);
				}
			}
		});
	}
	
	public synchronized void disconnect() {
		shutdown();
	}
	
	private void shutdown() {
		Connection connection = this.connection;
		if (connection != null) {
			this.connection = null;
			connection.group.shutdownGracefully();
			clearPlayers();
		}
	}
	
	private void clearPlayers() {
		connection.handler.connectedPlayers.clear();
		connection.handler.offlinePlayers.clear();
		scheduler.clear();
	}
	
	private Connection createConnection(URI uri, UUID userId, final SslContext sslContext) {
		DefaultHttpHeaders headers = new DefaultHttpHeaders();
		headers.add(Messenger.USER_ID_HEADER, userId.toString());
		
		// Connect with V13 (RFC 6455 aka HyBi-17)
		WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, false, headers);
		final AudioConnectClientHandler handler = new AudioConnectClientHandler(handshaker);
		
		EventLoopGroup group = new NioEventLoopGroup();
		Bootstrap bootstrap = new Bootstrap();
		bootstrap.group(group);
		bootstrap.channel(NioSocketChannel.class);
		
		// Used to handle the buffer limits for when network latency causes an increasing write buffer size.
		// In the case of an overflow, an OutOfMemoryError would normally occur, but this client will instead
		// drop messages and warn the logs that the channel is not writable due to the buffer limit.
		bootstrap.option(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, 32 * 1024);
		bootstrap.option(ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, 8 * 1024);
		
		bootstrap.handler(new ChannelInitializer<SocketChannel>() {
			
			@Override
			protected void initChannel(SocketChannel channel) {
				ChannelPipeline pipeline = channel.pipeline();
				if (sslContext != null) {
					pipeline.addLast(sslContext.newHandler(channel.alloc()));
				}
				pipeline.addLast(new HttpClientCodec());
				pipeline.addLast(new HttpObjectAggregator(8192));
				pipeline.addLast(handler);
			}
		});
		
		ChannelFuture connectFuture = bootstrap.connect(uri.getHost(), uri.getPort());
		Channel channel = connectFuture.channel();
		final Connection connection = new Connection(handler, group, channel);
		connectFuture.addListener(new ChannelFutureListener() {
			
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				if (future.isSuccess()) {
					reconnectAttempts.set(0);
				}
			}
		});
		return connection;
	}
	
	private void writeAndFlush(Channel channel, Message... messages) {
		if (!channel.isWritable()) {
			logger.warning("Attempted to write message to AudioConnect server but the channel is not writable! "
					+ "This may be a sign of a slow network connection or a slow server");
			return;
		}
		
		String json = messenger.serialize(messages);
		channel.writeAndFlush(new TextWebSocketFrame(json), channel.voidPromise());
	}
	
	private Message[] createInitialPayload(Player player) {
		List<Message> messages = new ArrayList<>();
		
		Status status = Status.CONNECTED;
		messages.add(new StatusMessage(player.getUniqueId(), status));
		
		for (Map.Entry<String, AudioTrackSettings> entry : config.getAudioTracks().entrySet()) {
			AudioTrackMessage.Builder messageBuilder = AudioTrackMessage.builder(player.getUniqueId(), entry.getKey());
			if (entry.getValue().isDefaultTrack()) {
				messageBuilder.defaultTrack();
			}
			if (entry.getValue().isRepeating()) {
				messageBuilder.repeating();
			}
			if (entry.getValue().isRandom()) {
				messageBuilder.random();
			}
			if (entry.getValue().isFading()) {
				messageBuilder.fading();
			}
			messages.add(messageBuilder.build());
		}
		
		dataWriter.writeAudioMessages(player, messages);
		
		return messages.toArray(new Message[messages.size()]);
	}
	
	
	private class Connection {
		
		private final AudioConnectClientHandler handler;
		private final EventLoopGroup group;
		private final Channel channel;
		
		private Connection(AudioConnectClientHandler handler, EventLoopGroup group, Channel channel) {
			this.handler = handler;
			this.group = group;
			this.channel = channel;
		}
		
	}
	
	
	private class AudioConnectClientHandler extends SimpleChannelInboundHandler<Object> {
		
		private final Map<UUID, ConnectedPlayer> connectedPlayers = new ConcurrentHashMap<>();
		private final Set<UUID> offlinePlayers = Sets.newConcurrentHashSet();
		
		private final WebSocketClientHandshaker handshaker;
		
		private AudioConnectClientHandler(WebSocketClientHandshaker handshaker) {
			this.handshaker = handshaker;
		}
		
		@Override
		public void channelActive(ChannelHandlerContext ctx) {
			handshaker.handshake(ctx.channel());
		}
		
		@Override
		public void channelRead0(final ChannelHandlerContext ctx, Object msg) {
			if (!handshaker.isHandshakeComplete()) {
				handleHandshakeResponse(ctx, (FullHttpResponse) msg);
			} else {
				if (!(msg instanceof WebSocketFrame)) {
					logger.warning("Recieved unexpected raw message from AudioConnect server: " + msg);
					return;
				}
				
				handleFrame(ctx, (WebSocketFrame) msg);
			}
		}
		
		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
			logger.log(Level.WARNING, "Reconnecting to AudioConnect server due to an unexpected exception", cause);
			ctx.close();
		}
		
		private void handleHandshakeResponse(ChannelHandlerContext ctx, FullHttpResponse response) {
			if (response.getStatus().equals(HttpResponseStatus.SWITCHING_PROTOCOLS)) {
				String supplierId = response.headers().get(Messenger.SUPPLIER_ID_HEADER);
				if (!StringUtils.isEmpty(supplierId)) {
					try {
						handshaker.finishHandshake(ctx.channel(), response);
						
						ctx.channel().attr(CONNECTION_SUPPLIER_ID).set(supplierId);
						logger.info("Successfully connected to AudioConnect server!");
						return;
					} catch (Exception e) {
						String responseMsg = response.content().toString(StandardCharsets.UTF_8);
						logger.severe("Failed to Connect with AudioConnect server: " + responseMsg);
					}
				} else {
					logger.severe("Websocket handshake response from AudioConnect server is missing " + Messenger.SUPPLIER_ID_HEADER + " header!");
				}
			} else {
				String responseMsg = response.content().toString(StandardCharsets.UTF_8);
				logger.severe("Failed to Connect with AudioConnect server: " + responseMsg);
			}
			
			ctx.channel().attr(CONNECTION_REFUSED).set(true);
			ctx.close();
		}
		
		private void handleFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
			if (frame instanceof PingWebSocketFrame) {
				ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()), ctx.voidPromise());
			} else if (frame instanceof PongWebSocketFrame) {
				logger.info("Recieved pong from AudioConnect server");
			} else if (frame instanceof CloseWebSocketFrame) {
				CloseWebSocketFrame closeFrame = (CloseWebSocketFrame) frame;
				logger.info("Recieved closing from AudioConnect server: [" + closeFrame.statusCode() + "] " + closeFrame.reasonText());
				
				connectedPlayers.clear();
				offlinePlayers.clear();
				ctx.close();
			} else if (frame instanceof TextWebSocketFrame) {
				TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
				
				Message[] messages;
				try {
					messages = messenger.deserialize(textFrame.text());
				} catch (MessageParseException e) {
					logger.warning("Failed to parse Message recieved from AudioConnect server: " + e.getMessage());
					return;
				}
				
				for (Message message : messages) {
					handleMessage(ctx, message);
				}
			} else {
				logger.warning("Recieved unexpected websocket frame from AudioConnect server: " + frame);
			}
		}
		
		private void handleMessage(final ChannelHandlerContext ctx, Message message) {
			if (message instanceof AudioListMessage) {
				AudioListMessage audioListMessage = (AudioListMessage) message;
				switch (audioListMessage.getAction()) {
					case ADD: {
						audioList.addAll(audioListMessage.getAudioIds());
						break;
					}
					case REMOVE: {
						audioList.removeAll(audioListMessage.getAudioIds());
						break;
					}
				}
			} else if (message instanceof StatusMessage) {
				StatusMessage statusMessage = (StatusMessage) message;
				
				if (statusMessage.getStatus() == Status.CONNECTED) {
					final UUID playerId = statusMessage.getId();
					connectedPlayers.put(playerId, new ConnectedPlayer(playerId, System.currentTimeMillis()));
					
					// Handle connected player on main thread
					Bukkit.getScheduler().runTask(plugin, new Runnable() {
						
						@Override
						public void run() {
							OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
							if (player.isOnline()) {
								scheduler.addPlayer(playerId);
								
								writeAndFlush(ctx.channel(), createInitialPayload((Player) player));
								
								Bukkit.getPluginManager().callEvent(new PlayerAudioStatusEvent(player, Status.CONNECTED));
							} else {
								offlinePlayers.add(playerId);
								
								StatusMessage statusMessage = new StatusMessage(playerId, Status.DISCONNECTED);
								writeAndFlush(ctx.channel(), statusMessage);
							}
						}
						
					});
				} else if (statusMessage.getStatus() == Status.DISCONNECTED) {
					final UUID playerId = statusMessage.getId();
					connectedPlayers.remove(playerId);
					offlinePlayers.remove(playerId);
					
					// Handle disconnected player on main thread
					Bukkit.getScheduler().runTask(plugin, new Runnable() {
						
						@Override
						public void run() {
							if (scheduler.removePlayer(playerId)) {
								Event statusEvent = new PlayerAudioStatusEvent(Bukkit.getOfflinePlayer(playerId), Status.DISCONNECTED);
								Bukkit.getPluginManager().callEvent(statusEvent);
							}
						}
					});
				}
			} else if (message instanceof HeartbeatMessage) {
				// If for some reason the server doesn't receive the pong frame, it will send a heartbeat message
				writeAndFlush(ctx.channel(), message);
			} else {
				logger.warning("Recieved unexpected Message type from AudioConnect server '" + message.getType() + "'");
			}
		}
		
	}
	
	
	public static class ConnectedPlayer {
		
		private final UUID playerId;
		private final long timeConnected;
		
		private ConnectedPlayer(UUID playerId, long timeConnected) {
			this.playerId = playerId;
			this.timeConnected = timeConnected;
		}
		
		public OfflinePlayer getPlayer() {
			return Bukkit.getOfflinePlayer(playerId);
		}
		
		public long getTimeConnected() {
			return timeConnected;
		}
		
	}
	
	public static interface PlayerAudioDataWriter extends PlayerDataWriter {
		
		void writeAudioMessages(Player player, List<Message> messageBuffer);
		
	}
	
}
