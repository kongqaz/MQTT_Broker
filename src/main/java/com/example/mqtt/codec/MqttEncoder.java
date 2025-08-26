package com.example.mqtt.codec;

import com.example.mqtt.message.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class MqttEncoder extends MessageToMessageEncoder<MqttMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, MqttMessage msg, List<Object> out) throws Exception {
        ByteBuf buffer = ctx.alloc().buffer();

        switch (msg.getMessageType()) {
            case CONNECT:
                encodeConnectMessage(buffer, (ConnectMessage) msg);
                break;
            case CONNACK:
                encodeConnAckMessage(buffer, (ConnAckMessage) msg);
                break;
            case PUBLISH:
                encodePublishMessage(buffer, (PublishMessage) msg);
                break;
            case PUBACK:
            case PUBREC:
            case PUBREL:
            case PUBCOMP:
                encodePacketIdMessage(buffer, msg);
                break;
            case SUBSCRIBE:
                encodeSubscribeMessage(buffer, (SubscribeMessage) msg);
                break;
            case SUBACK:
                encodeSubAckMessage(buffer, (SubAckMessage) msg);
                break;
            case UNSUBSCRIBE:
            case UNSUBACK:
            case PINGREQ:
            case PINGRESP:
            case DISCONNECT:
                encodeSimpleMessage(buffer, msg);
                break;
        }

        out.add(buffer);
    }

    private void encodeFixedHeader(ByteBuf buffer, MqttMessage msg, int remainingLength) {
        byte header = (byte) ((msg.getMessageType().value() << 4) & 0xF0);
        if (msg.isDup()) {
            header |= 0x08;
        }
        header |= (msg.getQosLevel() << 1) & 0x06;
        if (msg.isRetain()) {
            header |= 0x01;
        }
        buffer.writeByte(header);
        encodeVariableLengthInt(buffer, remainingLength);
    }

    private void encodeVariableLengthInt(ByteBuf buffer, int value) {
        do {
            int digit = value % 128;
            value /= 128;
            if (value > 0) {
                digit |= 0x80;
            }
            buffer.writeByte(digit);
        } while (value > 0);
    }

    private void encodeString(ByteBuf buffer, String string) {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        buffer.writeShort(bytes.length);
        buffer.writeBytes(bytes);
    }

    private void encodeConnectMessage(ByteBuf buffer, ConnectMessage msg) {
        int remainingLength = calculateConnectLength(msg);
        encodeFixedHeader(buffer, msg, remainingLength);

        encodeString(buffer, msg.getProtocolName());
        buffer.writeByte(msg.getProtocolVersion());

        byte connectFlags = 0;
        if (msg.isHasUsername()) connectFlags |= 0x80;
        if (msg.isHasPassword()) connectFlags |= 0x40;
        if (msg.isWillRetain()) connectFlags |= 0x20;
        connectFlags |= (msg.getWillQos() & 0x03) << 3;
        if (msg.isWillFlag()) connectFlags |= 0x04;
        if (msg.isCleanSession()) connectFlags |= 0x02;
        buffer.writeByte(connectFlags);

        buffer.writeShort(msg.getKeepAlive());
        encodeString(buffer, msg.getClientId());

        if (msg.isWillFlag()) {
            encodeString(buffer, msg.getWillTopic());
            byte[] willMessage = msg.getWillMessage();
            buffer.writeShort(willMessage.length);
            buffer.writeBytes(willMessage);
        }

        if (msg.isHasUsername()) {
            encodeString(buffer, msg.getUsername());
        }
        if (msg.isHasPassword()) {
            byte[] password = msg.getPassword();
            buffer.writeShort(password.length);
            buffer.writeBytes(password);
        }
    }

    private int calculateConnectLength(ConnectMessage msg) {
        int length = 0;
        length += 2 + msg.getProtocolName().length(); // Protocol Name
        length += 1; // Protocol Version
        length += 1; // Connect Flags
        length += 2; // Keep Alive
        length += 2 + msg.getClientId().length(); // Client ID

        if (msg.isWillFlag()) {
            length += 2 + msg.getWillTopic().length(); // Will Topic
            length += 2 + msg.getWillMessage().length; // Will Message
        }

        if (msg.isHasUsername()) {
            length += 2 + msg.getUsername().length(); // Username
        }
        if (msg.isHasPassword()) {
            length += 2 + msg.getPassword().length; // Password
        }

        return length;
    }

    private void encodeConnAckMessage(ByteBuf buffer, ConnAckMessage msg) {
        encodeFixedHeader(buffer, msg, 2);
        buffer.writeByte(msg.isSessionPresent() ? 0x01 : 0x00);
        buffer.writeByte(msg.getReturnCode());
    }

    private void encodePublishMessage(ByteBuf buffer, PublishMessage msg) {
        int remainingLength = calculatePublishLength(msg);
        encodeFixedHeader(buffer, msg, remainingLength);

        encodeString(buffer, msg.getTopicName());

        if (msg.getQosLevel() > 0) {
            buffer.writeShort(msg.getPacketId());
        }

        if (msg.getPayload() != null) {
            buffer.writeBytes(msg.getPayload());
        }
    }

    private int calculatePublishLength(PublishMessage msg) {
        int length = 2 + msg.getTopicName().length(); // Topic Name
        if (msg.getQosLevel() > 0) {
            length += 2; // Packet ID
        }
        if (msg.getPayload() != null) {
            length += msg.getPayload().length; // Payload
        }
        return length;
    }

    private void encodePacketIdMessage(ByteBuf buffer, MqttMessage msg) {
        encodeFixedHeader(buffer, msg, 2);
        // Packet ID应从具体消息中获取，这里简化处理
        buffer.writeShort(1);
    }

    private void encodeSubscribeMessage(ByteBuf buffer, SubscribeMessage msg) {
        int remainingLength = calculateSubscribeLength(msg);
        encodeFixedHeader(buffer, msg, remainingLength);

        buffer.writeShort(msg.getPacketId());

        for (SubscribeMessage.TopicSubscription topic : msg.getTopics()) {
            encodeString(buffer, topic.getTopic());
            buffer.writeByte(topic.getQos());
        }
    }

    private int calculateSubscribeLength(SubscribeMessage msg) {
        int length = 2; // Packet ID
        for (SubscribeMessage.TopicSubscription topic : msg.getTopics()) {
            length += 2 + topic.getTopic().length() + 1; // Topic + QoS
        }
        return length;
    }

    private void encodeSubAckMessage(ByteBuf buffer, SubAckMessage msg) {
        int remainingLength = 2 + msg.getReturnCodes().size();
        encodeFixedHeader(buffer, msg, remainingLength);

        buffer.writeShort(msg.getPacketId());
        for (Integer returnCode : msg.getReturnCodes()) {
            buffer.writeByte(returnCode);
        }
    }

    private void encodeSimpleMessage(ByteBuf buffer, MqttMessage msg) {
        encodeFixedHeader(buffer, msg, 0);
    }
}
