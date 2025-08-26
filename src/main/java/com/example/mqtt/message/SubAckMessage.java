package com.example.mqtt.message;

import com.example.mqtt.protocol.MqttMessageType;

import java.util.ArrayList;
import java.util.List;

public class SubAckMessage extends com.example.mqtt.message.MqttMessage {
    private int packetId;
    private List<Integer> returnCodes;

    public SubAckMessage() {
        super(MqttMessageType.SUBACK);
        this.returnCodes = new ArrayList<>();
    }

    public int getPacketId() {
        return packetId;
    }

    public void setPacketId(int packetId) {
        this.packetId = packetId;
    }

    public List<Integer> getReturnCodes() {
        return returnCodes;
    }

    public void addReturnCode(int returnCode) {
        returnCodes.add(returnCode);
    }
}
