import { useState, useEffect } from 'react'
import { fetchCandles } from '../services/api'

// Smart limits per interval — how many candles makes sense to show
const INTERVAL_LIMITS = {
  'minute':    3750,   // one full trading day (375 mins of NSE)
  '5minute':   500,   // ~13 days
  '15minute':  500,   // ~40 days
  'day':       365,   // one full year
}

export function useOhlcv(instrumentToken, interval = 'minute') {
  const [candles, setCandles] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!instrumentToken) return

    setLoading(true)
    setError(null)
    setCandles([]) // clear previous data immediately on change

    const limit = INTERVAL_LIMITS[interval] || 500

    fetchCandles(instrumentToken, interval, limit)
      .then(data => {
        if (!data || data.length === 0) {
          setError('No data available. Try syncing historical data first.')
          return
        }

        const formatted = data.map(c => ({
          time: Math.floor(new Date(c.bucketTime).getTime() / 1000),
          open:  parseFloat(c.open),
          high:  parseFloat(c.high),
          low:   parseFloat(c.low),
          close: parseFloat(c.close),
          value: c.volume
        }))
        setCandles(formatted)
      })
      .catch(err => {
        setError('Failed to load chart data.')
        console.error('Failed to fetch candles:', err)
      })
      .finally(() => setLoading(false))

  }, [instrumentToken, interval])

  return { candles, loading, error, setCandles }
}