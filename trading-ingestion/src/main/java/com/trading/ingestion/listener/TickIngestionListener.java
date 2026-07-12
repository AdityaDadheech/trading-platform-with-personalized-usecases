package com.trading.ingestion.listener;

import com.zerodhatech.models.Tick;
import com.trading.common.dto.TickDto;
import com.trading.ingestion.publisher.TickKafkaPublisher;
import com.trading.ingestion.service.TickNormalizerService;
import com.trading.kite.listener.KiteTickListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component("tickIngestionListener")
@RequiredArgsConstructor
public class TickIngestionListener implements KiteTickListener {

    private final TickNormalizerService normalizerService;
    private final TickKafkaPublisher kafkaPublisher;
    private final AtomicLong tickCount = new AtomicLong(0);

    @Override
    public void onTicks(ArrayList<Tick> ticks) {
        if (ticks == null || ticks.isEmpty()) return;

        List<TickDto> normalized = normalizerService.normalize(ticks);
        if (normalized.isEmpty()) return;

        kafkaPublisher.publishBatch(normalized);

        long total = tickCount.addAndGet(normalized.size());
        if (total % 1000 == 0) {
            log.info("Tick pipeline healthy — {} ticks processed since startup", total);
        }
    }

    @Override
    public void onConnected() {
        log.info("✅ TickIngestionListener connected — tick pipeline is LIVE");
        tickCount.set(0);
    }

    @Override
    public void onDisconnected() {
        log.warn("⚠️ TickIngestionListener disconnected — ticks paused until reconnect");
    }

    @Override
    public void onError(Exception exception) {
        log.error("❌ TickIngestionListener error: {}", exception.getMessage(), exception);
    }
}