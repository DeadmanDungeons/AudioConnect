package com.deadmandungeons.audioconnect.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.Config;
import com.deadmandungeons.audioconnect.Config.ConfigNum;
import com.deadmandungeons.audioconnect.messages.AudioRegionMessage;
import com.deadmandungeons.connect.messenger.Messenger;
import com.deadmandungeons.connect.messenger.Messenger.Message;
import com.deadmandungeons.connect.messenger.StatusMessage;
import com.deadmandungeons.connect.messenger.StatusMessage.Status;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.Future;

public class AudioConnectClient {
	
	private static final AudioConnect plugin = AudioConnect.getInstance();
	
	private static final URI URI;
	
	static {
		try {
			URI = new URI("ws://deadmandungeons.com:8080/supplier");
		} catch (URISyntaxException e) {
			throw new ExceptionInInitializerError(e);
		}
	}
	
	private static AudioConnectClient instance;
	
	public static AudioConnectClient getInstance() {
		if (instance == null) {
			instance = new AudioConnectClient();
		}
		return instance;
	}
	
	
	private final Messenger messenger;
	private Connection connection;
	private int reconnectAttempts;
	
	private AudioConnectClient() {
		messenger = Messenger.builder().registerMessageType(AudioRegionMessage.class, AudioRegionMessage.CREATOR).build();
	}
	
	
	public boolean isConnected() {
		return connection != null;
	}
	
	public boolean isPlayerTracked(UUID playerId) {
		return connection != null && connection.handler.getTrackedPlayers().contains(playerId);
	}
	
	public void notifyPlayerJoin(UUID playerId) {
		if (connection != null && connection.handler.getOfflinePlayers().remove(playerId)) {
			writeAndFlush(new StatusMessage(playerId, Status.CONNECTED));
		}
	}
	
	public void notifyPlayerQuit(UUID playerId) {
		if (connection != null && isPlayerTracked(playerId) && connection.handler.getOfflinePlayers().add(playerId)) {
			writeAndFlush(new StatusMessage(playerId, Status.DISCONNECTED));
		}
	}
	
	public void writeAndFlush(Message... messages) {
		if (connection == null) {
			throw new IllegalStateException("not connected to AudioConnect server");
		}
		if (!connection.channel.isWritable()) {
			String msg = "Attempted to write message to AudioConnect server but the channel is not writable! "
					+ "This may be a sign of a slow network connection or a slow server";
			plugin.getLogger().warning(msg);
			return;
		}
		
		String json = messenger.serialize(messages);
		connection.channel.writeAndFlush(new TextWebSocketFrame(json), connection.channel.voidPromise());
	}
	
	public void connect() {
		connect(false);
	}
	
	private void connect(boolean reconnectAttempt) {
		if (connection != null) {
			throw new IllegalStateException("already connected");
		}
		if (!reconnectAttempt) {
			reconnectAttempts = 0;
		}
		plugin.getLogger().info("Connecting to AudioConnect server [" + URI.toString() + "] ...");
		
		ConnectFuture connectFuture = Connection.connect(URI, messenger);
		connection = connectFuture.connection;
		
		connection.channel.closeFuture().addListener(new ChannelFutureListener() {
			
			@Override
			public void operationComplete(ChannelFuture future) {
				if (connection == null) {
					return;
				}
				Boolean connectionWasRefused = connection.channel.attr(AudioConnectClientHandler.CONNECTION_REFUSED).get();
				if (connectionWasRefused != null && connectionWasRefused) {
					plugin.getLogger().info("Shutting down client event loop");
					shutdown();
					return;
				}
				int maxReconnectAttempts = Config.value(ConfigNum.RECONNECT_MAX_ATTEMPTS).intValue();
				if (maxReconnectAttempts > 0 && reconnectAttempts >= maxReconnectAttempts) {
					plugin.getLogger().info("The maximum amount of reconnect attempts have been made. Shutting down client event loop");
					shutdown();
					return;
				}
				
				int interval = Config.value(ConfigNum.RECONNECT_INTERVAL).intValue();
				double delayRate = Config.value(ConfigNum.RECONNECT_DELAY).doubleValue();
				int delay = (int) (interval * Math.pow(delayRate, reconnectAttempts));
				int maxInterval = Config.value(ConfigNum.RECONNECT_MAX_INTERVAL).intValue();
				if (delay > maxInterval) {
					delay = maxInterval;
				}
				plugin.getLogger().warning("Disconnected from AudioConnect server. Reattempting connection in " + (delay / 1000) + " seconds");
				future.channel().eventLoop().schedule(new Runnable() {
					
					@Override
					public void run() {
						reconnectAttempts++;
						shutdown();
						connect(true);
					}
				}, delay, TimeUnit.MILLISECONDS);
			}
		});
		connectFuture.awaitConnection();
	}
	
	public void disconnect() {
		if (connection != null) {
			shutdown().awaitUninterruptibly();
		}
	}
	
	private Future<?> shutdown() {
		Connection connection = this.connection;
		if (connection != null) {
			this.connection = null;
			Future<?> future = connection.group.shutdownGracefully();
			connection.handler.getTrackedPlayers().clear();
			connection.handler.getOfflinePlayers().clear();
			return future;
		}
		return null;
	}
	
	private static class Connection {
		
		private final AudioConnectClientHandler handler;
		private final EventLoopGroup group;
		private final Channel channel;
		
		private Connection(AudioConnectClientHandler handler, EventLoopGroup group, Channel channel) {
			this.handler = handler;
			this.group = group;
			this.channel = channel;
		}
		
		private static ConnectFuture connect(URI uri, Messenger messenger) {
			final AudioConnectClientHandler handler = new AudioConnectClientHandler(uri, messenger);
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
					pipeline.addLast(new HttpClientCodec());
					pipeline.addLast(new HttpObjectAggregator(8192));
					pipeline.addLast(handler);
				}
			});
			
			ChannelFuture connectFuture = bootstrap.connect(uri.getHost(), uri.getPort());
			Channel channel = connectFuture.channel();
			Connection connection = new Connection(handler, group, channel);
			connectFuture.addListener(new ChannelFutureListener() {
				
				@Override
				public void operationComplete(ChannelFuture future) throws Exception {
					if (future.isSuccess()) {
						instance.reconnectAttempts = 0;
					}
				}
			});
			return new ConnectFuture(connectFuture, connection);
		}
	}
	
	private static class ConnectFuture {
		
		private final ChannelFuture connectFuture;
		private final Connection connection;
		
		private ConnectFuture(ChannelFuture connectFuture, Connection connection) {
			this.connectFuture = connectFuture;
			this.connection = connection;
		}
		
		private void awaitConnection() {
			connectFuture.awaitUninterruptibly();
		}
		
	}
	
}
