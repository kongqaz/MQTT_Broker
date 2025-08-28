package com.example.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MqttBrokerApplication implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(MqttBrokerApplication.class);

    @Autowired
    private MqttBrokerServer server;

    public static void main(String[] args) {
        // 在Spring容器启动之前设置系统属性
        setPortFromCommandLineArgs(args);
        SpringApplication.run(MqttBrokerApplication.class, args);
    }

    private static void setPortFromCommandLineArgs(String[] args) {
        int port = 1883;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                // 使用默认端口
                logger.warn("Invalid port number, using default port 1883");
            }
        }
        System.setProperty("mqtt.broker.port", String.valueOf(port));
    }

    public void run(String... args) throws Exception {
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
