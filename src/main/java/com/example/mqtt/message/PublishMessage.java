package com.example.mqtt.message;

import com.example.mqtt.protocol.MqttMessageType;

public class PublishMessage extends com.example.mqtt.message.MqttMessage {
    private String topicName;
    private int packetId;
    private byte[] payload;

    public PublishMessage() {
        super(MqttMessageType.PUBLISH);
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public int getPacketId() {
        return packetId;
    }

    public void setPacketId(int packetId) {
        this.packetId = packetId;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }
}
