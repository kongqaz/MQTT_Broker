package com.example.mqtt.message;

import com.example.mqtt.protocol.MqttMessageType;

import java.util.ArrayList;
import java.util.List;

public class UnsubscribeMessage extends com.example.mqtt.message.MqttMessage {
    private int packetId;
    private List<String> topics;

    public UnsubscribeMessage() {
        super(MqttMessageType.UNSUBSCRIBE);
        this.topics = new ArrayList<>();
    }

    public int getPacketId() {
        return packetId;
    }

    public void setPacketId(int packetId) {
        this.packetId = packetId;
    }

    public List<String> getTopics() {
        return topics;
    }

    public void addTopic(String topic) {
        topics.add(topic);
    }
}
