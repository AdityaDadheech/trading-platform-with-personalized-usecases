import { useState, useEffect } from 'react'
import { fetchCandles } from '../services/api'

// Smart limits per interval — how many candles makes sense to show
const INTERVAL_LIMITS = {
  'minute':    3000,   // one full trading day (375 mins of NSE)
  '5minute':   1500,   // ~13 days
  '15minute':  1500,   // ~40 days
  'day':       2400,   // one full year
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
          time:  c.t,
          open:  parseFloat(c.o),
          high:  parseFloat(c.h),
          low:   parseFloat(c.l),
          close: parseFloat(c.c),
          value: c.v
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