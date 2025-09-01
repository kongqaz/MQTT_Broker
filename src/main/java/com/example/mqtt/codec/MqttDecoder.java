package com.example.mqtt.codec;

import com.example.mqtt.message.*;
import com.example.mqtt.protocol.MqttMessageType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class MqttDecoder extends ReplayingDecoder<Void> {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 读取固定头部
        byte header = in.readByte();
        MqttMessageType messageType = MqttMessageType.valueOf((header >> 4) & 0x0F);

        boolean isDup = (header & 0x08) != 0;
        int qosLevel = (header & 0x06) >> 1;
        boolean isRetain = (header & 0x01) != 0;

        // 读取剩余长度
        int remainingLength = decodeVariableLengthInt(in);

        // 根据消息类型解析具体消息体
        MqttMessage message;
        switch (messageType) {
            case CONNECT:
                message = decodeConnectMessage(in);
                break;
            case CONNACK:
                message = decodeConnAckMessage(in);
                break;
            case PUBLISH:
                message = decodePublishMessage(in, remainingLength, qosLevel);
                break;
            case PUBACK:
            case PUBREC:
            case PUBREL:
            case PUBCOMP:
                message = decodePacketIdMessage(messageType, in);
                break;
            case SUBSCRIBE:
                message = decodeSubscribeMessage(in, remainingLength);
                break;
            case SUBACK:
                message = decodeSubAckMessage(in);
                break;
            case UNSUBSCRIBE:
                message = decodeUnsubscribeMessage(in, remainingLength);
                break;
            case UNSUBACK:
                message = decodePacketIdMessage(messageType, in);
                break;
            case PINGREQ:
            case PINGRESP:
            case DISCONNECT:
                message = new MqttMessage(messageType);
                break;
            default:
                throw new IllegalArgumentException("Unknown message type: " + messageType);
        }

        message.setDup(isDup);
        message.setQosLevel(qosLevel);
        message.setRetain(isRetain);

        out.add(message);
    }

    private int decodeVariableLengthInt(ByteBuf buffer) {
        int result = 0;
        int multiplier = 1;
        short digit;
        int loops = 0;
        do {
            digit = buffer.readUnsignedByte();
            result += (digit & 0x7F) * multiplier;
            multiplier *= 128;
            loops++;
        } while ((digit & 0x80) != 0 && loops < 4);

        return result;
    }

    private String decodeString(ByteBuf buffer) {
        int length = buffer.readUnsignedShort();
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] decodeBytes(ByteBuf buffer) {
        int length = buffer.readUnsignedShort();
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return bytes;
    }

    private ConnectMessage decodeConnectMessage(ByteBuf buffer) {
        ConnectMessage message = new ConnectMessage();

        // 协议名
        message.setProtocolName(decodeString(buffer));

        // 协议版本
        message.setProtocolVersion(buffer.readByte());

        // 连接标志
        byte connectFlags = buffer.readByte();
        message.setHasUsername((connectFlags & 0x80) != 0);
        message.setHasPassword((connectFlags & 0x40) != 0);
        message.setWillRetain((connectFlags & 0x20) != 0);
        message.setWillQos((connectFlags & 0x18) >> 3);
        message.setWillFlag((connectFlags & 0x04) != 0);
        message.setCleanSession((connectFlags & 0x02) != 0);

        // 保持连接
        message.setKeepAlive(buffer.readUnsignedShort());

        // 客户端ID
        message.setClientId(decodeString(buffer));

        // 遗嘱消息
        if (message.isWillFlag()) {
            message.setWillTopic(decodeString(buffer));
            message.setWillMessage(decodeBytes(buffer));
        }

        // 用户名和密码
        if (message.isHasUsername()) {
            message.setUsername(decodeString(buffer));
        }
        if (message.isHasPassword()) {
            message.setPassword(decodeBytes(buffer));
        }

        return message;
    }

    private ConnAckMessage decodeConnAckMessage(ByteBuf buffer) {
        ConnAckMessage message = new ConnAckMessage();
        byte ackFlags = buffer.readByte();
        message.setSessionPresent((ackFlags & 0x01) != 0);
        message.setReturnCode(buffer.readUnsignedByte());
        return message;
    }

    private PublishMessage decodePublishMessage(ByteBuf buffer, int remainingLength, int qosLevel) {
        PublishMessage message = new PublishMessage();

        // 主题名
        message.setTopicName(decodeString(buffer));

        // Packet ID (QoS > 0时存在)
        if (qosLevel > 0) {
            message.setPacketId(buffer.readUnsignedShort());
        }

        // 负载
        int payloadLength = remainingLength - (message.getTopicName().length() + 2);
        if (qosLevel > 0) {
            payloadLength -= 2; // Packet ID长度
        }

        if (payloadLength > 0) {
            byte[] payload = new byte[payloadLength];
            buffer.readBytes(payload);
            message.setPayload(payload);
        }

        return message;
    }

    private PacketIdMessage decodePacketIdMessage(MqttMessageType type, ByteBuf buffer) {
        PacketIdMessage message = new PacketIdMessage(type);
        message.setPacketId(buffer.readUnsignedShort());
        return message;
    }

    private SubscribeMessage decodeSubscribeMessage(ByteBuf buffer, int remainingLength) {
        SubscribeMessage message = new SubscribeMessage();
        message.setPacketId(buffer.readUnsignedShort());

        int readBytes = 2; // 已读取packetId的字节数
        while (readBytes < remainingLength && buffer.isReadable()) {
            // 确保至少还有3个字节可读（2字节长度 + 1字节QoS）
            if (buffer.readableBytes() < 3) {
                break;
            }

            String topic = decodeString(buffer);
            if (buffer.readableBytes() < 1) {
                break;
            }
            int qos = buffer.readUnsignedByte();
            message.addTopicSubscription(topic, qos);

            readBytes += 2 + topic.length() + 1; // topic长度(2) + topic + qos(1)
        }

        return message;
    }

    private SubAckMessage decodeSubAckMessage(ByteBuf buffer) {
        SubAckMessage message = new SubAckMessage();
        message.setPacketId(buffer.readUnsignedShort());

        // 读取返回码
        while (buffer.isReadable()) {
            message.addReturnCode(buffer.readUnsignedByte());
        }

        return message;
    }

    private MqttMessage decodeUnsubscribeMessage(ByteBuf buffer, int remainingLength) {
        // 简化实现
        MqttMessage message = new MqttMessage(MqttMessageType.UNSUBSCRIBE);
        return message;
    }
}
