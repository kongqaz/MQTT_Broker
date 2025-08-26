package com.example.mqtt.message;

import com.example.mqtt.protocol.MqttMessageType;

public class ConnAckMessage extends com.example.mqtt.message.MqttMessage {
    private int returnCode;
    private boolean sessionPresent;

    public ConnAckMessage() {
        super(MqttMessageType.CONNACK);
    }

    public int getReturnCode() {
        return returnCode;
    }

    public void setReturnCode(int returnCode) {
        this.returnCode = returnCode;
    }

    public boolean isSessionPresent() {
        return sessionPresent;
    }

    public void setSessionPresent(boolean sessionPresent) {
        this.sessionPresent = sessionPresent;
    }
}
