package com.deadmandungeons.audioconnect;

import com.deadmandungeons.audioconnect.AudioConnectConfig.AudioTrackSettings;
import com.deadmandungeons.audioconnect.PlayerScheduler.PlayerDataWriter;
import com.deadmandungeons.audioconnect.messages.AudioListMessage;
import com.deadmandungeons.audioconnect.messages.AudioMessage;
import com.deadmandungeons.audioconnect.messages.AudioTrackMessage;
import com.deadmandungeons.connect.commons.HeartbeatMessage;
import com.deadmandungeons.connect.commons.Messenger;
import com.deadmandungeons.connect.commons.Messenger.Message;
import com.deadmandungeons.connect.commons.Messenger.MessageParseException;
import com.deadmandungeons.connect.commons.StatusMessage;
import com.deadmandungeons.connect.commons.StatusMessage.Status;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
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
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import javax.net.ssl.SSLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The class which operates as the data supplier client to an AudioConnect server.<br>
 * <b>Note:</b> this class and its operations are thread safe.
 * @author Jon
 */
public class AudioConnectClient {

    private static final int WEBSOCKET_CLOSE_CODE_GOING_AWAY = 1001;

    private final Plugin plugin;

    // objects accessed on main server thread only
    private final PlayerStatusListener playerStatusListener = new PlayerStatusListener();
    private final PlayerAudioDataWriter playerDataWriter;
    private final PlayerScheduler playerScheduler; // thread safe but best used on main thread

    // thread safe objects accessed on multiple threads
    private final AudioConnectConfig config;
    private final AudioList audioList;
    private final Messenger messenger;
    private final Logger logger;

    private final Bootstrap bootstrap = new Bootstrap();
    private final ChannelFutureListener channelCloseListener = new ConnectionCloseListener();
    private final AtomicInteger reconnectAttempts = new AtomicInteger();
    private final AtomicBoolean connecting = new AtomicBoolean(); // Guard against connect() re-entrance
    private final Object connectionLock = new Object();

    private volatile Connection connection;


    public AudioConnectClient(Plugin plugin, AudioConnectConfig config, AudioList audioList, PlayerAudioDataWriter playerDataWriter) {
        this.plugin = plugin;
        this.config = config;
        this.audioList = audioList;
        this.playerDataWriter = playerDataWriter;

        // execute every 20 ticks (1 second) with max of 20 displaced scheduler tasks
        playerScheduler = new PlayerScheduler(plugin, playerDataWriter, 20, 20);
        messenger = Messenger.builder().registerMessageType(AudioMessage.CREATOR).registerMessageType(AudioListMessage.CREATOR)
                .registerMessageType(AudioTrackMessage.CREATOR).build();
        logger = plugin.getLogger();

        bootstrap.group(new NioEventLoopGroup());
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.handler(new ConnectionChannelInitializer());

        // Used to handle the buffer limits for when network latency causes an increasing write buffer size.
        // In the case of an overflow, an OutOfMemoryError would normally occur, but this client will instead
        // drop non-critical messages and warn the logs that the channel is not writable due to the buffer limit.
        bootstrap.option(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, 32 * 1024);
        bootstrap.option(ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, 8 * 1024);
    }


    /**
     * @return <code>true</code> if this client is fully connected to the AudioConnect server
     */
    public boolean isConnected() {
        Connection connection = this.connection;
        return connection != null && connection.handshakeFuture.isSuccess();
    }

    /**
     * @param playerId - The UUID of the player to check
     * @return <code>true</code> if a player identified with the given playerId is connected to the web client endpoint
     */
    public boolean isPlayerConnected(UUID playerId) {
        Connection connection = this.connection;
        return connection != null && connection.playerConnections.containsKey(playerId);
    }

    /**
     * @return an unmodifiable thread safe collection of {@link PlayerConnection}'s
     */
    public Collection<PlayerConnection> getPlayerConnections() {
        Connection connection = this.connection;
        if (connection != null) {
            return Collections.unmodifiableCollection(connection.playerConnections.values());
        }
        return Collections.emptyList();
    }

    /**
     * If a connection is not established with the AudioConnect server, this will do nothing.
     * @param messages - The Messages to be sent to the AudioConnect server
     */
    public void writeAndFlush(Message... messages) {
        Connection connection = this.connection;
        if (connection != null && connection.handshakeFuture.isSuccess()) {
            if (validateWritability(connection.channel)) {
                writeAndFlush(connection.channel, messages);
            }
        }
    }

    /**
     * Establish a connection with the configured AudioConnect server.<br>
     * If a connection is already established or is currently being established, this will do nothing.
     * @return a Future for when a valid connection has been established with the AudioConnect server
     * @throws IllegalStateException if called after {@link #shutdown()}
     */
    public Future<?> connect() throws IllegalStateException {
        playerStatusListener.register();

        return connect(false);
    }

    private Future<?> connect(boolean reconnect) throws IllegalStateException {
        if (bootstrap.group().isShuttingDown()) {
            throw new IllegalStateException("this client instance has been or is being terminated");
        }

        if (!connecting.getAndSet(true)) {
            try {
                synchronized (connectionLock) {
                    if (connection == null) {
                        if (reconnect) {
                            reconnectAttempts.incrementAndGet();
                        } else {
                            reconnectAttempts.set(0);
                        }

                        URI uri = config.getConnectionWebsocketUri();
                        logger.info("Connecting to AudioConnect server [" + uri + "] ...");

                        ChannelFuture connectFuture = bootstrap.connect(uri.getHost(), uri.getPort());
                        Channel channel = connectFuture.channel();

                        connection = new Connection(channel);

                        channel.closeFuture().addListener(channelCloseListener);

                        final ChannelPromise handshakeFuture = connection.handshakeFuture;
                        connectFuture.addListener(new ChannelFutureListener() {

                            @Override
                            public void operationComplete(ChannelFuture future) {
                                if (future.isSuccess()) {
                                    reconnectAttempts.set(0);
                                } else {
                                    handshakeFuture.setFailure(future.cause());
                                }
                            }
                        });

                        return handshakeFuture;
                    }
                }
            } finally {
                connecting.set(false);
            }
        }
        return bootstrap.group().next().newSucceededFuture(null);
    }

    /**
     * Disconnect from the AudioConnect server and reset.<br>
     * If a connection is not established or being established, this will do nothing.
     * @return a Future for when the connection has been fully disconnected and closed
     */
    public Future<?> disconnect() {
        Connection connection;
        synchronized (connectionLock) {
            connection = this.connection;
            this.connection = null;
        }

        if (connection != null) {
            playerScheduler.clear();
            connection.playerConnections.clear();

            // Remove channelCloseListener to not reconnect
            connection.channel.closeFuture().removeListener(channelCloseListener);

            if (connection.channel.isActive()) {
                final Promise<Object> disconnectPromise = bootstrap.group().next().newPromise();

                Object closeFrame = new CloseWebSocketFrame(WEBSOCKET_CLOSE_CODE_GOING_AWAY, "Going offline");
                connection.channel.writeAndFlush(closeFrame).addListener(new ChannelFutureListener() {

                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        future.channel().close().addListener(new PromiseNotifier<>(disconnectPromise));
                    }
                });
                return disconnectPromise;
            }
        }
        return bootstrap.group().next().newSucceededFuture(null);
    }

    /**
     * Disconnect from the AudioConnect server and shutdown this client.<br>
     * If this client has already been or is being shutdown, this will do nothing.<br>
     * <b>Note:</b> This client instance will no longer be able to connect after this is called.
     * @return a Future for when this client has completely disconnected and been shutdown.
     */
    public Future<?> shutdown() {
        if (bootstrap.group().isShuttingDown()) {
            return GlobalEventExecutor.INSTANCE.newSucceededFuture(null);
        }

        final Promise<Object> shutdownPromise = bootstrap.group().next().newPromise();

        disconnect().addListener(new FutureListener<Object>() {

            @Override
            public void operationComplete(Future<Object> future) {
                bootstrap.group().shutdownGracefully().addListener(new PromiseNotifier<>(shutdownPromise));
            }
        });

        return shutdownPromise;
    }


    private void writeAndFlush(Channel channel, Message... messages) {
        String json = messenger.serialize(messages);
        channel.writeAndFlush(new TextWebSocketFrame(json), channel.voidPromise());
    }

    private boolean validateWritability(Channel channel) {
        if (!channel.isWritable()) {
            logger.warning("Attempted to write message to AudioConnect server but the channel is not writable! " +
                    "This may be a sign of a slow network connection or a slow server");
            return false;
        }
        return true;
    }


    // Accessed on main server thread only
    private class PayloadBuilder {

        private final List<Message> messages = new ArrayList<>();
        private final UUID playerId;

        private PayloadBuilder(UUID playerId) {
            this.playerId = playerId;
        }

        private PayloadBuilder status(Status status) {
            messages.add(new StatusMessage(playerId, status));
            return this;
        }

        private PayloadBuilder tracks() {
            for (Map.Entry<String, AudioTrackSettings> entry : config.getAudioTracks().entrySet()) {
                AudioTrackMessage.Builder messageBuilder = AudioTrackMessage.builder(playerId, entry.getKey());
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
            return this;
        }

        private PayloadBuilder audio() {
            playerDataWriter.writeAudioMessages(Bukkit.getPlayer(playerId), messages);
            return this;
        }

        private Message[] build() {
            return messages.toArray(new Message[messages.size()]);
        }

    }

    private class PlayerStatusListener implements Runnable, Listener {

        private final AtomicBoolean registered = new AtomicBoolean();

        private void register() {
            if (!registered.getAndSet(true)) {
                Bukkit.getScheduler().runTask(plugin, this);
            }
        }

        @Override
        public void run() {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }

        @EventHandler
        private void onPlayerJoin(PlayerJoinEvent event) {
            Connection connection = AudioConnectClient.this.connection;
            if (connection != null) {
                UUID playerId = event.getPlayer().getUniqueId();
                PlayerConnection playerConnection = connection.playerConnections.get(playerId);
                if (playerConnection != null && !playerConnection.online.getAndSet(true)) {
                    playerScheduler.addPlayer(playerId);

                    Status status = Status.ONLINE;
                    writeAndFlush(new PayloadBuilder(playerId).status(status).audio().build());

                    Bukkit.getPluginManager().callEvent(new PlayerAudioStatusEvent(event.getPlayer(), status));
                }
            }
        }

        @EventHandler
        private void onPlayerQuit(PlayerQuitEvent event) {
            Connection connection = AudioConnectClient.this.connection;
            if (connection != null) {
                UUID playerId = event.getPlayer().getUniqueId();
                PlayerConnection playerConnection = connection.playerConnections.get(playerId);
                if (playerConnection != null && playerConnection.online.getAndSet(false)) {
                    playerScheduler.removePlayer(playerId);

                    Status status = Status.OFFLINE;
                    writeAndFlush(new PayloadBuilder(playerId).status(status).build());

                    Bukkit.getPluginManager().callEvent(new PlayerAudioStatusEvent(event.getPlayer(), status));
                }
            }
        }

    }


    private class ConnectionChannelInitializer extends ChannelInitializer<SocketChannel> {

        // Connect with V13 (RFC 6455 aka HyBi-17)
        private final WebSocketVersion WS_VERSION = WebSocketVersion.V13;

        @Override
        protected void initChannel(SocketChannel channel) throws SSLException {
            URI uri = config.getConnectionWebsocketUri();

            DefaultHttpHeaders headers = new DefaultHttpHeaders();
            headers.add(Messenger.USER_ID_HEADER, config.getConnectionUserId().toString());
            headers.add(Messenger.USER_PASSWORD_HEADER, config.getConnectionUserPassword());
            headers.add(Messenger.SUPPLIER_ID_HEADER, config.getConnectionServerId());

            WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(uri, WS_VERSION, null, false, headers);

            ChannelPipeline pipeline = channel.pipeline();
            if (config.isConnectionSecure()) {
                try {
                    SslContext sslContext = SslContext.newClientContext(InsecureTrustManagerFactory.INSTANCE);
                    pipeline.addLast(sslContext.newHandler(channel.alloc()));
                } catch (SSLException e) {
                    logger.log(Level.SEVERE, "Shutting down client due to unexpected failure to create SSL context", e);
                    throw e;
                }
            }
            pipeline.addLast(new HttpClientCodec());
            pipeline.addLast(new HttpObjectAggregator(8192));
            pipeline.addLast(new AudioConnectClientHandler(handshaker));
        }
    }

    private class ConnectionCloseListener implements ChannelFutureListener {

        @Override
        public void operationComplete(ChannelFuture future) {
            // Fully disconnect to reset client state
            disconnect();

            int currentReconnectAttempts = reconnectAttempts.get();
            int maxReconnectAttempts = config.getReconnectMaxAttempts();
            if (maxReconnectAttempts > 0 && currentReconnectAttempts >= maxReconnectAttempts) {
                logger.warning("Stopping client event loop due to reaching the maximum amount of reconnect attempts");
                return;
            }

            int interval = config.getReconnectInterval();
            double delayRate = config.getReconnectDelay();
            int delay = (int) (interval * Math.pow(delayRate, currentReconnectAttempts));
            int maxInterval = config.getReconnectMaxInterval();
            if (delay > maxInterval) {
                delay = maxInterval;
            }

            logger.warning("Disconnected from AudioConnect server. Reattempting connection in " + (delay / 1000) + " seconds");
            future.channel().eventLoop().schedule(new Runnable() {

                @Override
                public void run() {
                    connect(true);
                }
            }, delay, TimeUnit.MILLISECONDS);
        }

    }

    private class AudioConnectClientHandler extends SimpleChannelInboundHandler<Object> {

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
                finishHandshake(ctx, (FullHttpResponse) msg);
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
            if (!connection.handshakeFuture.isDone()) {
                connection.handshakeFuture.setFailure(cause);
            }
            ctx.close();
        }

        private void finishHandshake(ChannelHandlerContext ctx, FullHttpResponse response) {
            if (response.getStatus().equals(HttpResponseStatus.SWITCHING_PROTOCOLS)) {
                try {
                    handshaker.finishHandshake(ctx.channel(), response);
                    connection.handshakeFuture.setSuccess();

                    logger.info("Successfully connected to AudioConnect server!");
                    return;
                } catch (Exception e) {
                    connection.handshakeFuture.setFailure(e);
                }
            } else {
                connection.handshakeFuture.setFailure(new InvalidConfigurationException());
            }

            String responseMsg = response.content().toString(StandardCharsets.UTF_8);
            logger.severe("Failed to Connect with AudioConnect server: " + responseMsg);
            logger.severe("Stopping client event loop due to failure to establish connection with AudioConnect server");

            disconnect();
        }

        private void handleFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
            if (frame instanceof PingWebSocketFrame) {
                ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()), ctx.voidPromise());
            } else if (frame instanceof PongWebSocketFrame) {
                logger.info("Recieved pong from AudioConnect server");
            } else if (frame instanceof CloseWebSocketFrame) {
                CloseWebSocketFrame closeFrame = (CloseWebSocketFrame) frame;
                logger.info("Recieved closing from AudioConnect server: [" + closeFrame.statusCode() + "] " + closeFrame.reasonText());

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

                if (statusMessage.getStatus() == Status.ONLINE) {
                    final UUID playerId = statusMessage.getId();
                    final PlayerConnection playerConnection = new PlayerConnection(playerId, System.currentTimeMillis());
                    connection.playerConnections.put(playerId, playerConnection);

                    // Handle connected player on main thread
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {

                        @Override
                        public void run() {
                            OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
                            if (player.isOnline()) {
                                playerConnection.online.set(true);
                                playerScheduler.addPlayer(playerId);

                                writeAndFlush(ctx.channel(), new PayloadBuilder(playerId).status(Status.ONLINE).tracks().audio().build());

                                Bukkit.getPluginManager().callEvent(new PlayerAudioStatusEvent(player, Status.ONLINE));
                            } else {
                                writeAndFlush(ctx.channel(), new PayloadBuilder(playerId).status(Status.OFFLINE).tracks().build());
                            }
                        }
                    });
                } else if (statusMessage.getStatus() == Status.OFFLINE) {
                    final UUID playerId = statusMessage.getId();
                    connection.playerConnections.remove(playerId);

                    // Handle disconnected player on main thread
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {

                        @Override
                        public void run() {
                            if (playerScheduler.removePlayer(playerId)) {
                                Event statusEvent = new PlayerAudioStatusEvent(Bukkit.getOfflinePlayer(playerId), Status.OFFLINE);
                                Bukkit.getPluginManager().callEvent(statusEvent);
                            }
                        }
                    });
                }
            } else if (message instanceof HeartbeatMessage) {
                // If for some reason the server doesn't receive the pong frame, it will send a heartbeat message
                if (validateWritability(ctx.channel())) {
                    writeAndFlush(ctx.channel(), message);
                }
            } else {
                logger.warning("Recieved unexpected Message type from AudioConnect server '" + message.getType() + "'");
            }
        }

    }


    private static class PromiseNotifier<V> implements FutureListener<V> {

        private final Promise<? super V> promise;

        private PromiseNotifier(Promise<? super V> promise) {
            this.promise = promise;
        }

        @Override
        public void operationComplete(Future<V> future) throws Exception {
            if (future.isSuccess()) {
                promise.trySuccess(future.get());
            } else if (future.isCancelled()) {
                promise.cancel(false);
            } else {
                promise.setFailure(future.cause());
            }
        }

    }


    private static class Connection {

        private final ConcurrentHashMap<UUID, PlayerConnection> playerConnections = new ConcurrentHashMap<>();
        private final ChannelPromise handshakeFuture;
        private final Channel channel;

        private Connection(Channel channel) {
            handshakeFuture = channel.newPromise();
            this.channel = channel;
        }

    }

    public static class PlayerConnection {

        private final UUID playerId;
        private final long connectionTimestamp;
        // Whether or not the player is online to the server
        private final AtomicBoolean online = new AtomicBoolean();

        private PlayerConnection(UUID playerId, long connectionTimestamp) {
            this.playerId = playerId;
            this.connectionTimestamp = connectionTimestamp;
        }

        public OfflinePlayer getPlayer() {
            return Bukkit.getOfflinePlayer(playerId);
        }

        public long getConnectionTimestamp() {
            return connectionTimestamp;
        }

    }

    public interface PlayerAudioDataWriter extends PlayerDataWriter {

        /**
         * This method will only be called on the main server thread.
         * @param player - The player to write the audio messages for
         * @param messageBuffer - the list to add the audio messages to
         */
        void writeAudioMessages(Player player, List<Message> messageBuffer);

    }

}
