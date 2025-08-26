package com.example.mqtt.message;

import com.example.mqtt.protocol.MqttMessageType;

import java.util.ArrayList;
import java.util.List;

public class SubscribeMessage extends com.example.mqtt.message.MqttMessage {
    private int packetId;
    private List<TopicSubscription> topics;

    public SubscribeMessage() {
        super(MqttMessageType.SUBSCRIBE);
        this.topics = new ArrayList<>();
    }

    public int getPacketId() {
        return packetId;
    }

    public void setPacketId(int packetId) {
        this.packetId = packetId;
    }

    public List<TopicSubscription> getTopics() {
        return topics;
    }

    public void addTopicSubscription(String topic, int qos) {
        topics.add(new TopicSubscription(topic, qos));
    }

    public static class TopicSubscription {
        private String topic;
        private int qos;

        public TopicSubscription(String topic, int qos) {
            this.topic = topic;
            this.qos = qos;
        }

        public String getTopic() {
            return topic;
        }

        public int getQos() {
            return qos;
        }
    }
}
