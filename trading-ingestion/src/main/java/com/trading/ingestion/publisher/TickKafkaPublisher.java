package com.trading.ingestion.publisher;

import com.trading.common.constants.KafkaTopics;
import com.trading.common.dto.TickDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class TickKafkaPublisher {

    private final KafkaTemplate<String, TickDto> kafkaTemplate;

    public void publishBatch(List<TickDto> ticks) {
        for (TickDto tick : ticks) {
            publish(tick);
        }
    }

    public void publish(TickDto tick) {
        String key = String.valueOf(tick.getInstrumentToken());

        CompletableFuture<SendResult<String, TickDto>> future =
            kafkaTemplate.send(KafkaTopics.TICKS, key, tick);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish tick for token {}: {}",
                    tick.getInstrumentToken(), ex.getMessage());
            } else {
                log.trace("Tick published → topic={} partition={} offset={}",
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            }
        });
    }
}