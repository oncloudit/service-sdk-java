package iservice.sdk.net;

import com.alibaba.fastjson.JSON;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import iservice.sdk.entity.WrappedMessage;
import iservice.sdk.exception.WebSocketConnectException;
import iservice.sdk.core.WebSocketClientObserver;

/**
 * @author : ori
 * @date : 2020/9/21 5:46 下午
 */
public class WebSocketClient {
    private WebSocketClientOptions options;

    private Channel channel = null;

    /**
     * To record if the client has been started.
     */
    private boolean startedFlag = false;

    public WebSocketClient(WebSocketClientOptions options) {
        this.options = options;
    }

    public void start() {
        // if client has been started and not be closed, it should not start again
        if (startedFlag) {
            return;
        }
        // param check
        if (options.getUri() == null) {
            return;
        }
        // prepare for start
        prepareStart();
        NioEventLoopGroup workLoopGroup = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = getBootstrap(workLoopGroup);
            ChannelFuture channelFuture = bootstrap.connect(options.getHost(), options.getPort()).sync();
            // to hold a channel
            channel = channelFuture.channel();
            // init Handler Observer
            initHandlerObserver();
            // insure client will be closed
            Runtime.getRuntime().addShutdownHook(new Thread(this::close));
            channel.closeFuture().sync();
        } catch (InterruptedException e) {
            System.err.println("Connect failed.");
            startedFlag = false;
            e.printStackTrace();
        } finally {
            prepareClose();
            workLoopGroup.shutdownGracefully();
        }
    }

    private void initHandlerObserver() {
        WebSocketMessageHandler.EVENT_OBSERVABLE.addObserver(new WebSocketClientObserver());
    }

    /**
     * prepare for start client
     */
    private void prepareStart() {
        this.startedFlag = true;
    }

    private void prepareClose() {
        startedFlag = false;
    }

    private Bootstrap getBootstrap(NioEventLoopGroup workLoopGroup) {
        return new Bootstrap()
                .group(workLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                                // you can read binary info with this plugin
//                                    new LoggingHandler(LogLevel.INFO),
                                // HttpEncoder & HttpDecoder
                                new HttpClientCodec(),
                                // HttpFile length limiter
                                new HttpObjectAggregator(1024 * 1024 * 10),
                                // custom websocket message handler
                                new WebSocketMessageHandler(options.getUri())
                        );
                    }
                }).option(ChannelOption.SO_KEEPALIVE, true);
    }

    public void close() {
        if (channel == null) {
            System.err.println("Client has not start!");
            return;
        } else if (!channel.isActive()) {
            System.err.println("Client is not active! It may have closed already!");
        }
        prepareClose();
        System.out.println("Channel closed.");
        try {
            channel.close();
        } finally {
            startedFlag = false;
        }
    }

    /**
     * check channel is active
     *
     * @param ifThrow
     * @return
     */
    private boolean checkChannelActive(boolean ifThrow) {
        if (channel == null) {
            if (ifThrow) {
                throw new WebSocketConnectException("Client has not start!");
            } else {
                System.err.println("Client has not start!");
                return false;
            }
        }
        return channel.isActive();
    }

    public boolean isReady() {
        return checkChannelActive(false);
    }

    public <T> void send(T msg) {
        if (!isReady()) {
            throw new WebSocketConnectException("Connect is not ready...");
        }
        channel.writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(createWrappedMessage(msg))));
    }

    private <T> WrappedMessage<T> createWrappedMessage(T msg) {

        return new WrappedMessage<>(msg);
    }

}
