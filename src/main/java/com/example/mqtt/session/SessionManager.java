package com.example.mqtt.session;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SessionManager {
    private final ConcurrentMap<String, com.example.mqtt.session.Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<com.example.mqtt.session.Subscription>> topicSubscriptions = new ConcurrentHashMap<>();

    public void addSession(com.example.mqtt.session.Session session) {
        sessions.put(session.getClientId(), session);
    }

    public com.example.mqtt.session.Session getSession(String clientId) {
        return sessions.get(clientId);
    }

    public com.example.mqtt.session.Session removeSession(String clientId) {
        return sessions.remove(clientId);
    }

    public void addSubscription(String topic, com.example.mqtt.session.Subscription subscription) {
        topicSubscriptions.computeIfAbsent(topic, k -> ConcurrentHashMap.newKeySet()).add(subscription);
    }

    public Set<com.example.mqtt.session.Subscription> getSubscribers(String topic) {
        return topicSubscriptions.getOrDefault(topic, ConcurrentHashMap.newKeySet());
    }

    public int getSessionCount() {
        return sessions.size();
    }

    public void removeSubscription(String topic, String clientId) {
        // 获取主题的订阅者列表
        Set<Subscription> subscribers = topicSubscriptions.get(topic);
        if (subscribers != null) {
            // 移除指定客户端的订阅
            subscribers.removeIf(subscription -> subscription.getClientId().equals(clientId));

            // 如果该主题没有订阅者了，可以考虑清理
            if (subscribers.isEmpty()) {
                topicSubscriptions.remove(topic);
            }
        }
    }
}
