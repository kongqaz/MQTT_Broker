package com.example.mqtt.handler;

import com.example.mqtt.message.*;
import com.example.mqtt.protocol.MqttMessageType;
import com.example.mqtt.protocol.MqttQoS;
import com.example.mqtt.session.Session;
import com.example.mqtt.session.SessionManager;
import com.example.mqtt.session.Subscription;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class MqttMessageHandler extends SimpleChannelInboundHandler<MqttMessage> {
    private static final Logger logger = LoggerFactory.getLogger(MqttMessageHandler.class);

    private final SessionManager sessionManager;
    private final Map<String, Channel> clientChannels;
    private final AtomicInteger packetIdGenerator = new AtomicInteger(1);

    private String clientId;
    private Session session;

    public MqttMessageHandler(SessionManager sessionManager, Map<String, Channel> clientChannels) {
        this.sessionManager = sessionManager;
        this.clientChannels = clientChannels;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Client connected: {}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("Client disconnected: {}", clientId != null ? clientId : ctx.channel().remoteAddress());
        if (clientId != null) {
            clientChannels.remove(clientId);
            if (session != null && session.isCleanSession()) {
                sessionManager.removeSession(clientId);
            }
        }
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) throws Exception {
        switch (msg.getMessageType()) {
            case CONNECT:
                handleConnect(ctx, (ConnectMessage) msg);
                break;
            case PUBLISH:
                handlePublish(ctx, (PublishMessage) msg);
                break;
            case PUBACK:
                handlePubAck(ctx, msg);
                break;
            case PUBREC:
                handlePubRec(ctx, msg);
                break;
            case PUBREL:
                handlePubRel(ctx, msg);
                break;
            case PUBCOMP:
                handlePubComp(ctx, msg);
                break;
            case SUBSCRIBE:
                handleSubscribe(ctx, (SubscribeMessage) msg);
                break;
            case UNSUBSCRIBE:
                handleUnsubscribe(ctx, (UnsubscribeMessage) msg);
                break;
            case PINGREQ:
                handlePingReq(ctx);
                break;
            case DISCONNECT:
                handleDisconnect(ctx);
                break;
        }
    }

    private void handleConnect(ChannelHandlerContext ctx, ConnectMessage msg) {
        clientId = msg.getClientId();

        // 检查协议版本
        if (msg.getProtocolVersion() != 3 && msg.getProtocolVersion() != 4) {
            // 不支持的协议版本
            ConnAckMessage connAck = new ConnAckMessage();
            connAck.setReturnCode(1); // 不接受的协议版本
            ctx.writeAndFlush(connAck);
            ctx.close();
            return;
        }

        // 检查客户端ID
        if (clientId == null || clientId.isEmpty()) {
            if (msg.getProtocolVersion() == 3) {
                // MQTT 3.1要求客户端ID不能为空
                ctx.close();
                return;
            } else {
                // MQTT 3.1.1可以生成随机客户端ID
                clientId = "mqtt_" + System.currentTimeMillis();
            }
        }

        // 检查是否已有连接
        Channel existingChannel = clientChannels.put(clientId, ctx.channel());
        if (existingChannel != null && existingChannel.isActive()) {
            existingChannel.close();
        }

        // 获取或创建会话
        session = sessionManager.getSession(clientId);
        if (session == null || msg.isCleanSession()) {
            session = new Session(clientId, msg.isCleanSession(), msg.getKeepAlive());
            sessionManager.addSession(session);
        }

        // 发送CONNACK
        ConnAckMessage connAck = new ConnAckMessage();
        connAck.setReturnCode(0); // 连接接受
        connAck.setSessionPresent(!msg.isCleanSession() && sessionManager.getSession(clientId) != null);
        ctx.writeAndFlush(connAck);

        logger.info("Client {} connected with clean session: {}", clientId, msg.isCleanSession());
    }

    private void handlePublish(ChannelHandlerContext ctx, PublishMessage msg) {
        if (clientId == null) {
            ctx.close();
            return;
        }

        // 处理QoS
        switch (msg.getQosLevel()) {
            case 0: // At most once
                deliverMessage(msg);
                break;
            case 1: // At least once
                deliverMessage(msg);
                // 发送PUBACK
                MqttMessage pubAck = new MqttMessage(MqttMessageType.PUBACK);
                pubAck.setQosLevel(0);
                ctx.writeAndFlush(pubAck);
                break;
            case 2: // Exactly once
                if (session != null) {
                    session.addInboundMessage(msg.getPacketId(), msg);
                }
                // 发送PUBREC
                MqttMessage pubRec = new MqttMessage(MqttMessageType.PUBREC);
                pubRec.setQosLevel(0);
                pubRec.setPacketId(msg.getPacketId());
                ctx.writeAndFlush(pubRec);
                break;
        }
    }

    private void handlePubAck(ChannelHandlerContext ctx, MqttMessage msg) {
        if (session != null) {
            session.removeOutboundMessage(msg.getPacketId());
        }
    }

    private void handlePubRec(ChannelHandlerContext ctx, MqttMessage msg) {
        // 发送PUBREL
        MqttMessage pubRel = new MqttMessage(MqttMessageType.PUBREL);
        pubRel.setQosLevel(1);
        pubRel.setPacketId(msg.getPacketId());
        ctx.writeAndFlush(pubRel);
    }

    private void handlePubRel(ChannelHandlerContext ctx, MqttMessage msg) {
        if (session != null) {
            session.removeInboundMessage(msg.getPacketId());
        }
        // 发送PUBCOMP
        MqttMessage pubComp = new MqttMessage(MqttMessageType.PUBCOMP);
        pubComp.setQosLevel(0);
        pubComp.setPacketId(msg.getPacketId());
        ctx.writeAndFlush(pubComp);
    }

    private void handlePubComp(ChannelHandlerContext ctx, MqttMessage msg) {
        if (session != null) {
            session.removeOutboundMessage(msg.getPacketId());
        }
    }

    private void handleSubscribe(ChannelHandlerContext ctx, SubscribeMessage msg) {
        if (clientId == null) {
            ctx.close();
            return;
        }

        SubAckMessage subAck = new SubAckMessage();
        subAck.setPacketId(msg.getPacketId());

        for (SubscribeMessage.TopicSubscription topic : msg.getTopics()) {
            Subscription subscription = new Subscription(clientId, topic.getTopic(), MqttQoS.valueOf(topic.getQos()));
            sessionManager.addSubscription(topic.getTopic(), subscription);
            subAck.addReturnCode(topic.getQos()); // 接受订阅
        }

        ctx.writeAndFlush(subAck);
    }

    private void handleUnsubscribe(ChannelHandlerContext ctx, UnsubscribeMessage msg) {
        // 简化实现
        MqttMessage unsubAck = new MqttMessage(MqttMessageType.UNSUBACK);
        unsubAck.setPacketId(msg.getPacketId());
        ctx.writeAndFlush(unsubAck);
    }

    private void handlePingReq(ChannelHandlerContext ctx) {
        MqttMessage pingResp = new MqttMessage(MqttMessageType.PINGRESP);
        ctx.writeAndFlush(pingResp);
    }

    private void handleDisconnect(ChannelHandlerContext ctx) {
        if (clientId != null) {
            clientChannels.remove(clientId);
            if (session != null && session.isCleanSession()) {
                sessionManager.removeSession(clientId);
            }
        }
        ctx.close();
    }

    private void deliverMessage(PublishMessage message) {
        Set<Subscription> subscribers = sessionManager.getSubscribers(message.getTopicName());
        for (Subscription subscription : subscribers) {
            Channel channel = clientChannels.get(subscription.getClientId());
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(message);
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.ALL_IDLE) {
                logger.info("Client {} timeout, closing connection", clientId);
                ctx.close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Exception in MQTT handler", cause);
        ctx.close();
    }
}
