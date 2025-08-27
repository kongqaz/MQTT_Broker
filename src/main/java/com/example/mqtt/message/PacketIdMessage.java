package com.example.mqtt.message;

import com.example.mqtt.protocol.MqttMessageType;

/*
 * 根据 MQTT 3.1.1 协议，PUBACK、PUBREC、PUBREL、PUBCOMP 四种报文的格式基本相同，
 * 都只包含固定头部和包含 Packet Identifier 的可变头部。可以使用一个统一的类来处理
 */
public class PacketIdMessage extends MqttMessage {
    private int packetId;

    public PacketIdMessage(MqttMessageType messageType) {
        super(messageType);
    }

    public int getPacketId() {
        return packetId;
    }

    public void setPacketId(int packetId) {
        this.packetId = packetId;
    }
}

