package com.example.mqtt.handler;

import com.example.mqtt.config.MqttBrokerProperties;
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
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class MqttMessageHandler extends SimpleChannelInboundHandler<MqttMessage> {
    private static final Logger logger = LoggerFactory.getLogger(MqttMessageHandler.class);
    private static final Logger loggerDebug = LoggerFactory.getLogger("logger.DEBUG_MSG");

    private final SessionManager sessionManager;
    private final Map<String, Channel> clientChannels;
    private final AtomicInteger packetIdGenerator = new AtomicInteger(1);

    private String clientId;
    private Session session;

    private MqttBrokerProperties mqttBrokerProperties;

    public MqttMessageHandler(SessionManager sessionManager, Map<String, Channel> clientChannels, MqttBrokerProperties mqttBrokerPropertie) {
        this.sessionManager = sessionManager;
        this.clientChannels = clientChannels;
        this.mqttBrokerProperties = mqttBrokerPropertie;
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
                handlePubAck(ctx, (PacketIdMessage)msg);
                break;
            case PUBREC:
                handlePubRec(ctx, (PacketIdMessage)msg);
                break;
            case PUBREL:
                handlePubRel(ctx, (PacketIdMessage)msg);
                break;
            case PUBCOMP:
                handlePubComp(ctx, (PacketIdMessage)msg);
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

        // 用户名密码验证
        if (msg.isHasUsername() && msg.getUsername() != null) {
            // 进行用户名密码验证
            boolean authenticated = authenticateUser(msg.getUsername(), msg.getPassword());
            if (!authenticated) {
                ConnAckMessage connAck = new ConnAckMessage();
                connAck.setReturnCode(4); // 用户名或密码错误
                ctx.writeAndFlush(connAck);
                ctx.close();
                return;
            }
        } else if (isAuthenticationRequired()) {
            // 如果服务器要求认证但客户端未提供用户名密码
            ConnAckMessage connAck = new ConnAckMessage();
            connAck.setReturnCode(4); // 用户名或密码错误
            ctx.writeAndFlush(connAck);
            ctx.close();
            return;
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

        String msgPayload = new String(msg.getPayload(), java.nio.charset.StandardCharsets.UTF_8);
//        logger.info("Recv publish msg from client id={}, topic={}, payload={}", clientId, msg.getTopicName(), msgPayload);
        logger.info("Recv publish msg from client id={}, topic={}", clientId, msg.getTopicName());
        loggerDebug.info("Recv publish msg from client id={}, topic={}, payload={}", clientId, msg.getTopicName(), msgPayload);
        // 处理QoS
        switch (msg.getQosLevel()) {
            case 0: // At most once
                deliverMessageToSubscribers(msg);
                break;
            case 1: // At least once
                deliverMessageToSubscribers(msg);
                // 发送PUBACK
                PacketIdMessage pubAck = new PacketIdMessage(MqttMessageType.PUBACK);
                pubAck.setQosLevel(0);
                pubAck.setPacketId(msg.getPacketId());
                ctx.writeAndFlush(pubAck);
                break;
            case 2: // Exactly once
                if (session != null) {
                    session.addInboundMessage(msg.getPacketId(), msg);
                }
                // 发送PUBREC
                PacketIdMessage pubRec = new PacketIdMessage(MqttMessageType.PUBREC);
                pubRec.setQosLevel(0);
                pubRec.setPacketId(msg.getPacketId());
                ctx.writeAndFlush(pubRec);
                break;
        }
    }

    private void handlePubAck(ChannelHandlerContext ctx, PacketIdMessage msg) {
        if (session != null) {
            session.removeOutboundMessage(msg.getPacketId());
        }
    }

    private void handlePubRec(ChannelHandlerContext ctx, PacketIdMessage msg) {
        // 发送PUBREL
        PacketIdMessage pubRel = new PacketIdMessage(MqttMessageType.PUBREL);
        pubRel.setQosLevel(1);
        pubRel.setPacketId(msg.getPacketId());
        ctx.writeAndFlush(pubRel);
    }

    private void handlePubRel(ChannelHandlerContext ctx, PacketIdMessage msg) {
        if (session != null) {
            // 从会话中获取原始消息
            PublishMessage originalMessage = session.removeInboundMessage(msg.getPacketId());
            if (originalMessage != null) {
                // 在QoS 2握手完成后，转发消息给订阅者
                deliverMessageToSubscribers(originalMessage);
            }
        }
        // 发送PUBCOMP
        PacketIdMessage pubComp = new PacketIdMessage(MqttMessageType.PUBCOMP);
        pubComp.setQosLevel(0);
        pubComp.setPacketId(msg.getPacketId());
        ctx.writeAndFlush(pubComp);
    }

    private void handlePubComp(ChannelHandlerContext ctx, PacketIdMessage msg) {
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
        if (clientId == null) {
            ctx.close();
            return;
        }

        // 从订阅列表中移除订阅
        for (String topic : msg.getTopics()) {
            sessionManager.removeSubscription(topic, clientId);
        }

        // 发送 UNSUBACK
        PacketIdMessage unsubAck = new PacketIdMessage(MqttMessageType.UNSUBACK);
        unsubAck.setPacketId(msg.getPacketId());
        ctx.writeAndFlush(unsubAck);
    }

    private void handlePingReq(ChannelHandlerContext ctx) {
        MqttMessage pingResp = new MqttMessage(MqttMessageType.PINGRESP);
        ctx.writeAndFlush(pingResp);
    }

    private void handleDisconnect(ChannelHandlerContext ctx) {
        if (clientId != null) {
            logger.info("Client {} disconnected", clientId);
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

    private void deliverMessageToSubscribers(PublishMessage originalMessage) {
        Set<Subscription> subscribers = sessionManager.getSubscribers(originalMessage.getTopicName());
        for (Subscription subscription : subscribers) {
            Channel channel = clientChannels.get(subscription.getClientId());
            if (channel != null && channel.isActive()) {
                // 为每个订阅者创建新的消息实例
                PublishMessage messageToSend = new PublishMessage();
                messageToSend.setMessageType(MqttMessageType.PUBLISH);
                messageToSend.setDup(false);
                messageToSend.setTopicName(originalMessage.getTopicName());
                messageToSend.setPayload(originalMessage.getPayload());
                messageToSend.setRetain(originalMessage.isRetain());

                // 根据订阅QoS设置消息QoS
                int effectiveQos = Math.min(originalMessage.getQosLevel(), subscription.getQos().value());
                messageToSend.setQosLevel(effectiveQos);

                // 为每个订阅者分配新的PacketId（如果QoS > 0）
                if (effectiveQos > 0) {
                    // 应该从订阅者的会话中获取新的PacketId
                    int packetId = generatePacketIdForClient(subscription.getClientId());
                    messageToSend.setPacketId(packetId);
                    // 记录发送给客户端的outbound消息，用于QoS确认处理
                    Session clientSession = sessionManager.getSession(subscription.getClientId());
                    if (clientSession != null) {
                        clientSession.addOutboundMessage(packetId, messageToSend);
                    }
                }

                channel.writeAndFlush(messageToSend);
            }
        }
    }

    private int generatePacketIdForClient(String clientId) {
        // 获取客户端的会话
        Session clientSession = sessionManager.getSession(clientId);
        if (clientSession != null) {
            // 使用会话中的Packet ID生成器
            return clientSession.generatePacketId();
        } else {
            // 如果没有会话，使用全局生成器
            int packetId = packetIdGenerator.getAndIncrement();
            if (packetId > 65535 || packetId <= 0) {
                packetIdGenerator.compareAndSet(packetId, 1);
                packetId = 1;
            }
            return packetId;
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

    private boolean authenticateUser(String username, byte[] password) {
        // 实现具体的认证逻辑
        // 例如查询数据库、验证LDAP等
        if (username == null) return false;
        if (password == null) return false;

        String passwordStr = new String(password, java.nio.charset.StandardCharsets.UTF_8);

        // 遍历配置文件中的用户列表进行验证
        List<MqttBrokerProperties.Authentication.User> users = mqttBrokerProperties.getAuthentication().getUsers();
        if (users != null) {
            for (MqttBrokerProperties.Authentication.User user : users) {
                if (username.equals(user.getUsername()) && passwordStr.equals(user.getPassword())) {
                    return true;
                }
            }
        }

        return false; // 示例中暂时返回true
    }

    private boolean isAuthenticationRequired() {
        return mqttBrokerProperties.getAuthentication().isEnabled();
    }
}
