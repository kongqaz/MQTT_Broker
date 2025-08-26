package com.example.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MqttBrokerApplication {
    private static final Logger logger = LoggerFactory.getLogger(MqttBrokerApplication.class);

    private static com.example.mqtt.MqttBrokerServer server;

    public static void main(String[] args) {
        int port = 1883;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.warn("Invalid port number, using default port 1883");
            }
        }

        server = new com.example.mqtt.MqttBrokerServer(port);

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down MQTT Broker...");
            server.stop();
            logger.info("MQTT Broker stopped");
        }));

        try {
            server.start();
        } catch (InterruptedException e) {
            logger.error("MQTT Broker interrupted", e);
            Thread.currentThread().interrupt();
        }
    }
}
