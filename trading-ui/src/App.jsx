import { useState, useRef, useCallback, useEffect } from 'react'
import { Watchlist } from './components/Watchlist/Watchlist'
import { CandlestickChart } from './components/Chart/CandlestickChart'
import { QuotePanel } from './components/QuotePanel/QuotePanel'
import { useTickWebSocket } from './hooks/useTickWebSocket'
import { useOhlcv } from './hooks/useOhlcv'
import './App.css'

// Add this helper above the App component
function isMarketOpen() {
  const now = new Date()
  
  // Check if weekday (0=Sunday, 6=Saturday)
  const day = now.getDay()
  if (day === 0 || day === 6) return false
  
  // Convert to IST
  const istOffset = 5.5 * 60 * 60 * 1000 // IST is UTC+5:30
  const ist = new Date(now.getTime() + istOffset + now.getTimezoneOffset() * 60 * 1000)
  
  const hours = ist.getHours()
  const minutes = ist.getMinutes()
  const timeInMinutes = hours * 60 + minutes
  
  const marketOpen  = 9  * 60 + 15  // 09:15
  const marketClose = 15 * 60 + 30  // 15:30
  
  return timeInMinutes >= marketOpen && timeInMinutes <= marketClose
}

export default function App() {

  // Add this useEffect inside App component
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    if (params.get('auth') === 'success') {
      // Clean the URL without reloading
      window.history.replaceState({}, '', '/')
      // You'll see this in console — we'll add a proper toast later
      console.log('Authentication successful!')
    }
  }, [])

  // Add interval state
  const [interval, setChartInterval] = useState('minute')

  // Currently selected instrument
  const [selectedToken, setSelectedToken] = useState(256265)
  const [selectedSymbol, setSelectedSymbol] = useState('NIFTY 50')

  // Live prices map: { instrumentToken: lastPrice }
  const [livePrices, setLivePrices] = useState({})

  // Latest full tick for the selected instrument (for QuotePanel)
  const [selectedQuote, setSelectedQuote] = useState(null)

  // Ref to push live tick updates directly into the chart
  const pushTickToChart = useRef(null)

  // Fetch historical candles for selected instrument
  const { candles, loading, error } = useOhlcv(selectedToken, interval, 500)

  const [hoveredCandle, setHoveredCandle] = useState(null)

  // Handle incoming ticks from WebSocket
  const handleTick = useCallback((tick) => {
    const token = tick.instrumentToken
    const price = tick.lastPrice

    // Update live prices for watchlist
    setLivePrices(prev => ({ ...prev, [token]: price }))

    // If this tick is for the selected instrument, update quote panel
    if (token === selectedToken) {
      setSelectedQuote(tick)

      // Push live update to chart (updates the current candle)
      if (pushTickToChart.current) {
        pushTickToChart.current(tick)
      }
    }
  }, [selectedToken])

  // WebSocket connection
  const { connected, subscribe } = useTickWebSocket(handleTick)

  // When user selects a symbol from watchlist
  const handleSelectInstrument = useCallback((token, symbol) => {
    setSelectedToken(token)
    setSelectedSymbol(symbol)
    setSelectedQuote(null)

    // Subscribe to this instrument's ticks
    subscribe([token])
  }, [subscribe])

  return (
    <div className="app">
      {/* Connection status indicator */}
      <div className={`ws-status ${connected && isMarketOpen() ? 'connected' : 'disconnected'}`}>
        {connected && isMarketOpen() ? '● Live' : isMarketOpen() ? '○ Connecting...' : '○ Market Closed'}
      </div>

      {/* Left panel — watchlist */}
      <Watchlist
        selectedToken={selectedToken}
        onSelect={handleSelectInstrument}
        livePrices={livePrices}
      />

      {/* Center — candlestick chart */}
      <CandlestickChart
        candles={candles}
        loading={loading}
        error={error}
        symbol={selectedSymbol}
        onNewTick={pushTickToChart}
        interval={interval}
        onIntervalChange={setChartInterval}
        onCrosshairMove={setHoveredCandle}
      />

      {/* Right panel — quote details */}
      <QuotePanel
        quote={selectedQuote}
        symbol={selectedSymbol}
        hoveredCandle={hoveredCandle}
      />
    </div>
  )
}
