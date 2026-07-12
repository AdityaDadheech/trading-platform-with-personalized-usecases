import axios from 'axios'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' }
})

// ── OHLCV ────────────────────────────────────────────────────────
export const fetchCandles = async (instrumentToken, interval = 'minute', limit = 500) => {
  const { data } = await api.get(`/ohlcv/${instrumentToken}`, {
    params: { interval, limit }
  })
  return data.data // unwrap ApiResponse envelope
}

export const fetchCandlesInRange = async (instrumentToken, interval, from, to) => {
  const { data } = await api.get(`/ohlcv/${instrumentToken}/range`, {
    params: { interval, from, to }
  })
  return data.data
}

export const fetchSummary = async (instrumentToken) => {
  const { data } = await api.get(`/ohlcv/${instrumentToken}/summary`)
  return data.data
}

// ── Watchlist ─────────────────────────────────────────────────────
export const fetchWatchlist = async () => {
  const { data } = await api.get('/watchlist')
  return data.data
}

export const addToWatchlist = async (instrumentToken) => {
  const { data } = await api.post(`/watchlist/${instrumentToken}`)
  return data.data
}

export const removeFromWatchlist = async (instrumentToken) => {
  const { data } = await api.delete(`/watchlist/${instrumentToken}`)
  return data.data
}

// ── Quote ─────────────────────────────────────────────────────────
export const fetchQuote = async (instrumentToken) => {
  const { data } = await api.get(`/quote/${instrumentToken}`)
  return data.data
}