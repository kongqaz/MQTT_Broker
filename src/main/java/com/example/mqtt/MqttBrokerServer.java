package com.example.mqtt;

import com.example.mqtt.codec.MqttDecoder;
import com.example.mqtt.codec.MqttEncoder;
import com.example.mqtt.config.MqttBrokerProperties;
import com.example.mqtt.handler.MqttMessageHandler;
import com.example.mqtt.session.SessionManager;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class MqttBrokerServer {
    private static final Logger logger = LoggerFactory.getLogger(MqttBrokerServer.class);

    @Value("${mqtt.broker.port:1883}")
    private int port;

    private final SessionManager sessionManager;
    private final Map<String, Channel> clientChannels;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    @Autowired
    private MqttBrokerProperties mqttBrokerProperties;

    public MqttBrokerServer() {
        this.sessionManager = new SessionManager();
        this.clientChannels = new ConcurrentHashMap<>();
    }

    // 使用@PostConstruct注解的方法在依赖注入完成后执行
    @PostConstruct
    public void init() {
        logger.info("MQTT Broker port={}", port); // 此时port已被注入
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();

                            // 添加编解码器
                            pipeline.addLast("decoder", new MqttDecoder());
                            pipeline.addLast("encoder", new MqttEncoder());

                            // 添加心跳检测
                            pipeline.addLast("idleStateHandler",
                                    new IdleStateHandler(0, 0, 60, TimeUnit.SECONDS));

                            // 添加业务处理器
                            pipeline.addLast("mqttHandler",
                                    new MqttMessageHandler(sessionManager, clientChannels, mqttBrokerProperties));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);

            ChannelFuture future = bootstrap.bind(port).sync();
            logger.info("MQTT Broker started on port {}", port);

            future.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public void stop() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    public int getPort() {
        return port;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public int getConnectionCount() {
        return clientChannels.size();
    }
}
