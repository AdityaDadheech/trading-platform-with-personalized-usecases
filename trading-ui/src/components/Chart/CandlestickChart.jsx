import { useEffect, useRef, useState } from 'react'
import { createChart, CandlestickSeries } from 'lightweight-charts'
import './CandlestickChart.css'

export function CandlestickChart({ candles, loading, error, symbol, onNewTick, interval, onIntervalChange, onCrosshairMove }) {
  const containerRef = useRef(null)
  const chartRef = useRef(null)
  const seriesRef = useRef(null)
  const [ohlcInfo, setOhlcInfo] = useState(null)

  // Create chart on mount
  useEffect(() => {
    if (!containerRef.current) return

    const chart = createChart(containerRef.current, {
      autoSize: true,           // ← KEY FIX: fills container automatically
      layout: {
        background: { color: '#0f0f0f' },
        textColor: '#d1d4dc',
      },
      grid: {
        vertLines: { color: '#1e1e1e' },
        horzLines: { color: '#1e1e1e' },
      },
      crosshair: {
        mode: 1,
      },
      rightPriceScale: {
        borderColor: '#2a2a2a',
        visible: true,           // ← ensure price scale always visible
        autoScale: true,
      },
      timeScale: {
        borderColor: '#2a2a2a',
        timeVisible: true,
        secondsVisible: false,
        rightOffset: 5,
      },
      crosshair: {
        mode: 0,
      },
    })

    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: '#26a69a',
      downColor: '#ef5350',
      borderVisible: false,
      wickUpColor: '#26a69a',
      wickDownColor: '#ef5350',
    })

    // Subscribe to crosshair move — fires as cursor moves over candles
    chart.subscribeCrosshairMove((param) => {
      if (!param.time || !param.seriesData) {
        setOhlcInfo(null)
        onCrosshairMove?.(null)
        return
      }
      const data = param.seriesData.get(candleSeries)
      if (data) {
        setOhlcInfo(data)
        onCrosshairMove?.(data)
      }
    })

    chartRef.current = chart
    seriesRef.current = candleSeries

    return () => {
      chart.remove()
    }
  }, [])

  // Update chart data when candles change
  useEffect(() => {
    if (!seriesRef.current || !candles.length) return
    liveCandle.current = null // reset live candle when new historical candles arrive
    seriesRef.current.setData(candles)
    chartRef.current.timeScale().fitContent()
    chartRef.current.priceScale('right').applyOptions({ autoScale: true })
  }, [candles])

  // Expose a way for parent to push live ticks
  // Track current live candle state in memory
  const liveCandle = useRef(null)

  useEffect(() => {
    if (onNewTick) {
      onNewTick.current = (tick, currentInterval) => {
        if (!seriesRef.current) return

        const price = tick.lastPrice
        if (!price || isNaN(price)) return

        // Calculate bucket time based on current interval
        const nowUtcSeconds = Math.floor(Date.now() / 1000)
        const nowIstSeconds = nowUtcSeconds + 19800  // IST = UTC + 5:30

        let bucketIst
        switch (currentInterval) {
          case 'minute':
            bucketIst = nowIstSeconds - (nowIstSeconds % 60)
            break
          case '5minute':
            bucketIst = nowIstSeconds - (nowIstSeconds % (5 * 60))
            break
          case '15minute':
            bucketIst = nowIstSeconds - (nowIstSeconds % (15 * 60))
            break
          case 'day':
            // Floor to market open of the current day (09:15 IST)
            const dayStart = nowIstSeconds - (nowIstSeconds % 86400)
            const marketOpen = dayStart + (9 * 3600 + 15 * 60)  // 09:15 IST
            bucketIst = marketOpen
            break
          default:
            bucketIst = nowIstSeconds - (nowIstSeconds % 60)
        }

        const bucketUtc = bucketIst - 19800

        if (!liveCandle.current || liveCandle.current.time !== bucketUtc) {
          liveCandle.current = {
            time:  bucketUtc,
            open:  price,
            high:  price,
            low:   price,
            close: price,
          }
        } else {
          liveCandle.current = {
            ...liveCandle.current,
            high:  Math.max(liveCandle.current.high, price),
            low:   Math.min(liveCandle.current.low, price),
            close: price,
          }
        }

        seriesRef.current.update(liveCandle.current)
      }
    }
  }, [onNewTick])

  const INTERVALS = [
    { label: '1m',  value: 'minute' },
    { label: '5m',  value: '5minute' },
    { label: '15m', value: '15minute' },
    { label: '1D',  value: 'day' },
  ]

  return (
    <div className="chart-container">
      <div className="chart-header">
        <span className="chart-symbol">{symbol || '—'}</span>
        <div className="chart-intervals">
          {INTERVALS.map(iv => (
            <button
              key={iv.value}
              className={`interval-btn ${interval === iv.value ? 'active' : ''}`}
              onClick={() => onIntervalChange(iv.value)}
            >
              {iv.label}
            </button>
          ))}
        </div>
      </div>

      {/* OHLCV info bar — shows on hover */}
      {ohlcInfo && (
        <div className="chart-ohlc-bar">
          <span>O <span className="ohlc-val">{ohlcInfo.open?.toFixed(2)}</span></span>
          <span>H <span className="ohlc-val up">{ohlcInfo.high?.toFixed(2)}</span></span>
          <span>L <span className="ohlc-val down">{ohlcInfo.low?.toFixed(2)}</span></span>
          <span>C <span className="ohlc-val">{ohlcInfo.close?.toFixed(2)}</span></span>
        </div>
      )}

      <div ref={containerRef} className="chart-canvas">
        {loading && (
          <div className="chart-overlay">
            <span>Loading candles...</span>
          </div>
        )}
        {error && !loading && (
          <div className="chart-overlay error">
            <span>{error}</span>
          </div>
        )}
      </div>
    </div>
  )
}