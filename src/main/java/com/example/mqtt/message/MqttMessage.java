package com.example.mqtt.message;

import com.example.mqtt.protocol.MqttMessageType;

public class MqttMessage {
    protected MqttMessageType messageType;
    protected boolean isDup;
    protected int qosLevel;
    protected boolean isRetain;

    public MqttMessage(MqttMessageType messageType) {
        this.messageType = messageType;
    }

    // Getters and Setters
    public MqttMessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MqttMessageType messageType) {
        this.messageType = messageType;
    }

    public boolean isDup() {
        return isDup;
    }

    public void setDup(boolean dup) {
        isDup = dup;
    }

    public int getQosLevel() {
        return qosLevel;
    }

    public void setQosLevel(int qosLevel) {
        this.qosLevel = qosLevel;
    }

    public boolean isRetain() {
        return isRetain;
    }

    public void setRetain(boolean retain) {
        isRetain = retain;
    }
}
