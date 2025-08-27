package com.example.mqtt.session;

import com.example.mqtt.message.PublishMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Session {
    private final String clientId;
    private boolean cleanSession;
    private int keepAlive;
    private Map<Integer, PublishMessage> outboundMessages;
    private Map<Integer, PublishMessage> inboundMessages;
    private final AtomicInteger packetIdGenerator = new AtomicInteger(1);

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

    public int generatePacketId() {
        // Packet ID范围是1-65535，超过后重新从1开始
        int packetId = packetIdGenerator.getAndIncrement();
        if (packetId > 65535 || packetId <= 0) {
            packetIdGenerator.set(1);
            packetId = 1;
        }
        return packetId;
    }
}
