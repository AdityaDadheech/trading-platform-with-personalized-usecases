package com.trading.aggregator.config;

import com.trading.common.dto.TickDto;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configures the Kafka consumer for reading TickDtos.
 *
 * CONSUMER GROUP EXPLAINED:
 * ──────────────────────────────────────────────────────────────
 * groupId = "trading-aggregator"
 *
 * Kafka tracks the last read offset per consumer group.
 * This means:
 * 1. If the aggregator restarts, it resumes from the last
 *    committed offset — no ticks are re-processed or lost.
 * 2. If you add a second aggregator instance, Kafka splits
 *    partitions between them — automatic load balancing.
 * 3. The ingestion service uses a different group
 *    ("trading-market-api") so both services read ALL ticks
 *    independently — one group's offset doesn't affect the other.
 * ──────────────────────────────────────────────────────────────
 *
 * AUTO OFFSET RESET:
 * "latest" — if this consumer group has no stored offset
 * (first time running), start from the newest messages.
 * We don't want to reprocess potentially millions of old ticks.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, TickDto> tickConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "trading-aggregator");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // Tell the deserializer to produce TickDto objects
        // This matches the producer's ADD_TYPE_INFO_HEADERS = false setting
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, TickDto.class.getName());
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.trading.common.dto");
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(config);
    }

    /**
     * Container factory creates the listener thread pool.
     * Spring's @KafkaListener uses this factory by name —
     * that's why KafkaTickConsumer references "tickKafkaListenerContainerFactory".
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TickDto>
        tickKafkaListenerContainerFactory(ConsumerFactory<String, TickDto> tickConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, TickDto> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(tickConsumerFactory);

        // 3 concurrent consumers = matches our 3 Kafka partitions
        // Each partition gets its own consumer thread
        factory.setConcurrency(3);

        return factory;
    }
}