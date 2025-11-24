package com.example.mqtt.session;

import java.util.HashSet;
import java.util.Map;
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

    /**
     * 获取匹配指定主题和通配符类型的订阅者集合
     *
     * @param topicName 要匹配的主题名称
     * @param wildcardType 通配符类型 ("+" 或 "#")
     * @return 匹配的订阅者集合
     */
    public Set<Subscription> getWildcardSubscribers(String topicName, String wildcardType) {
        Set<Subscription> matchingSubscribers = ConcurrentHashMap.newKeySet();

        // 将主题名称拆分为层级
        String[] topicLevels = topicName.split("/");

        for (Map.Entry<String, Set<Subscription>> entry : topicSubscriptions.entrySet()) {
            String topicFilter = entry.getKey();

            // 处理 "+" 单层通配符
            if ("+".equals(wildcardType) && isSingleLevelWildcardMatch(topicFilter, topicLevels)) {
                matchingSubscribers.addAll(entry.getValue());
            }
        }

        return matchingSubscribers;
    }

    /**
     * 判断主题是否与含有单层通配符的过滤器匹配
     *
     * @param topicFilter 含有 "+" 通配符的主题过滤器
     * @param topicLevels 目标主题的层级数组
     * @return 是否匹配
     */
    private boolean isSingleLevelWildcardMatch(String topicFilter, String[] topicLevels) {
        // 将主题过滤器拆分为层级
        String[] filterLevels = topicFilter.split("/");

        // 如果层级数不一致，则不可能匹配
        if (filterLevels.length != topicLevels.length) {
            return false;
        }

        // 逐层比较
        for (int i = 0; i < filterLevels.length; i++) {
            // 如果是通配符 "+"，则跳过这一层的比较
            if ("+".equals(filterLevels[i])) {
                continue;
            }

            // 如果不是通配符且当前层级不匹配，则整体不匹配
            if (!filterLevels[i].equals(topicLevels[i])) {
                return false;
            }
        }

        return true;
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
