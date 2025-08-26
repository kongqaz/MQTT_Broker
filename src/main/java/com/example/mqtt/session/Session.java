package com.example.mqtt.session;

import com.example.mqtt.message.PublishMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Session {
    private final String clientId;
    private boolean cleanSession;
    private int keepAlive;
    private Map<Integer, PublishMessage> outboundMessages;
    private Map<Integer, PublishMessage> inboundMessages;

    public Session(String clientId, boolean cleanSession, int keepAlive) {
        this.clientId = clientId;
        this.cleanSession = cleanSession;
        this.keepAlive = keepAlive;
        this.outboundMessages = new ConcurrentHashMap<>();
        this.inboundMessages = new ConcurrentHashMap<>();
    }

    public String getClientId() {
        return clientId;
    }

    public boolean isCleanSession() {
        return cleanSession;
    }

    public int getKeepAlive() {
        return keepAlive;
    }

    public void addOutboundMessage(int packetId, PublishMessage message) {
        outboundMessages.put(packetId, message);
    }

    public PublishMessage removeOutboundMessage(int packetId) {
        return outboundMessages.remove(packetId);
    }

    public void addInboundMessage(int packetId, PublishMessage message) {
        inboundMessages.put(packetId, message);
    }

    public PublishMessage removeInboundMessage(int packetId) {
        return inboundMessages.remove(packetId);
    }
}
