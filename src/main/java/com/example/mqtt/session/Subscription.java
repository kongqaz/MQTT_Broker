package com.example.mqtt.session;

import com.example.mqtt.protocol.MqttQoS;

public class Subscription {
    private final String clientId;
    private final String topicFilter;
    private final MqttQoS qos;

    public Subscription(String clientId, String topicFilter, MqttQoS qos) {
        this.clientId = clientId;
        this.topicFilter = topicFilter;
        this.qos = qos;
    }

    public String getClientId() {
        return clientId;
    }

    public String getTopicFilter() {
        return topicFilter;
    }

    public MqttQoS getQos() {
        return qos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Subscription that = (Subscription) o;
        return clientId.equals(that.clientId) && topicFilter.equals(that.topicFilter);
    }

    @Override
    public int hashCode() {
        int result = clientId.hashCode();
        result = 31 * result + topicFilter.hashCode();
        return result;
    }
}
