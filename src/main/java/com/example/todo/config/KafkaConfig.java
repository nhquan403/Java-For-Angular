package com.example.todo.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Khai báo topic Kafka. Chỉ nạp khi app.kafka.enabled=true. Spring Kafka tự tạo topic lúc khởi động nếu chưa có.
 * Production thật thường để đội hạ tầng tạo topic và tắt việc tự tạo, ở đây giữ tự tạo cho tiện học.
 */
@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    @Bean
    public NewTopic todoEventsTopic(@Value("${app.kafka.topic}") String topic,
                                    @Value("${app.kafka.partitions:3}") int partitions,
                                    @Value("${app.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(replicas)
                // Sự kiện chỉ là thông báo tức thời, giữ một ngày là thừa, tránh tốn đĩa.
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(24 * 60 * 60 * 1000L))
                .build();
    }
}
