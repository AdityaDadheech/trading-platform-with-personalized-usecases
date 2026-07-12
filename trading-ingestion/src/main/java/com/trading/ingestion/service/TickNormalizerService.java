package com.trading.ingestion.service;

import com.trading.kite.service.InstrumentRegistry;
import com.zerodhatech.models.Depth;
import com.zerodhatech.models.Tick;
import com.trading.common.dto.TickDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TickNormalizerService {

    private final InstrumentRegistry instrumentRegistry;

    public List<TickDto> normalize(ArrayList<Tick> ticks) {
        List<TickDto> result = new ArrayList<>(ticks.size());
        for (Tick tick : ticks) {
            try {
                result.add(toDto(tick));
            } catch (Exception e) {
                log.warn("Failed to normalize tick for token {}: {}",
                    tick.getInstrumentToken(), e.getMessage());
            }
        }
        return result;
    }

    private TickDto toDto(Tick tick) {
        return TickDto.builder()
                .instrumentToken(tick.getInstrumentToken())
                .tradingSymbol(instrumentRegistry.getSymbol(tick.getInstrumentToken()))
                .exchange(instrumentRegistry.getExchange(tick.getInstrumentToken()))
                .lastPrice(toBd(tick.getLastTradedPrice()))
                .openPrice(toBd(tick.getOpenPrice()))
                .highPrice(toBd(tick.getHighPrice()))
                .lowPrice(toBd(tick.getLowPrice()))
                .closePrice(toBd(tick.getClosePrice()))
                .volumeTraded(tick.getVolumeTradedToday())
                .totalBuyQuantity((long) tick.getTotalBuyQuantity())
                .totalSellQuantity((long) tick.getTotalSellQuantity())
                .averageTradePrice(toBd(tick.getAverageTradePrice()))
                .bestBidPrice(getBestBidPrice(tick))
                .bestBidQuantity(getBestBidQty(tick))
                .bestAskPrice(getBestAskPrice(tick))
                .bestAskQuantity(getBestAskQty(tick))
                .openInterest((long) tick.getOi())
                .openInterestDayHigh((long) tick.getOpenInterestDayHigh())
                .openInterestDayLow((long) tick.getOpenInterestDayLow())
                .tickTimestamp(tick.getTickTimestamp() != null
                        ? tick.getTickTimestamp().toInstant()
                        : Instant.now())
                .receivedAt(Instant.now())
                .build();
    }

    private BigDecimal getBestBidPrice(Tick tick) {
        if (tick.getMarketDepth() == null) return BigDecimal.ZERO;
        ArrayList<Depth> bids = tick.getMarketDepth().get("buy");
        if (bids == null || bids.isEmpty()) return BigDecimal.ZERO;
        return toBd(bids.get(0).getPrice());
    }

    private Integer getBestBidQty(Tick tick) {
        if (tick.getMarketDepth() == null) return 0;
        ArrayList<Depth> bids = tick.getMarketDepth().get("buy");
        if (bids == null || bids.isEmpty()) return 0;
        return bids.get(0).getQuantity();
    }

    private BigDecimal getBestAskPrice(Tick tick) {
        if (tick.getMarketDepth() == null) return BigDecimal.ZERO;
        ArrayList<Depth> asks = tick.getMarketDepth().get("sell");
        if (asks == null || asks.isEmpty()) return BigDecimal.ZERO;
        return toBd(asks.get(0).getPrice());
    }

    private Integer getBestAskQty(Tick tick) {
        if (tick.getMarketDepth() == null) return 0;
        ArrayList<Depth> asks = tick.getMarketDepth().get("sell");
        if (asks == null || asks.isEmpty()) return 0;
        return asks.get(0).getQuantity();
    }

    private BigDecimal toBd(double value) {
        return BigDecimal.valueOf(value);
    }
}