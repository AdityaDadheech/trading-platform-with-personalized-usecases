import './QuotePanel.css'

export function QuotePanel({ quote, symbol, hoveredCandle }) {

  // Use hovered candle data if available, otherwise live quote
  const display = hoveredCandle ? {
    lastPrice: hoveredCandle.close,
    openPrice: hoveredCandle.open,
    highPrice: hoveredCandle.high,
    lowPrice:  hoveredCandle.low,
    closePrice: hoveredCandle.close,
    volumeTraded: hoveredCandle.volume,
  } : quote

  if (!display && !hoveredCandle) {
    return (
      <div className="quote-panel">
        <div className="quote-empty">
          Hover over a candle to see OHLC values
        </div>
      </div>
    )
  }

  const isHovered = !!hoveredCandle
  const change = display.lastPrice && display.closePrice
    ? display.lastPrice - display.closePrice
    : null
  const changePct = change && display.closePrice
    ? ((change / display.closePrice) * 100).toFixed(2)
    : null
  const isPositive = change >= 0

  return (
    <div className="quote-panel">
      <div className="quote-header">
        <div className="quote-symbol">{symbol}</div>
        {isHovered && (
          <div className="quote-mode-badge">Candle</div>
        )}
        <div className={`quote-price ${isPositive ? 'up' : 'down'}`}>
          {display.lastPrice?.toFixed(2)}
        </div>
        {change !== null && (
          <div className={`quote-change ${isPositive ? 'up' : 'down'}`}>
            {isPositive ? '+' : ''}{change.toFixed(2)} ({changePct}%)
          </div>
        )}
      </div>

      <div className="quote-grid">
        <div className="quote-row">
          <span className="quote-label">Open</span>
          <span className="quote-value">{display.openPrice?.toFixed(2)}</span>
        </div>
        <div className="quote-row">
          <span className="quote-label">High</span>
          <span className="quote-value up">{display.highPrice?.toFixed(2)}</span>
        </div>
        <div className="quote-row">
          <span className="quote-label">Low</span>
          <span className="quote-value down">{display.lowPrice?.toFixed(2)}</span>
        </div>
        {display.closePrice && (
          <div className="quote-row">
            <span className="quote-label">Prev Close</span>
            <span className="quote-value">{display.closePrice?.toFixed(2)}</span>
          </div>
        )}
        {display.volumeTraded !== null && display.volumeTraded !== undefined && (
          <div className="quote-row">
            <span className="quote-label">Volume</span>
            <span className="quote-value">
              {display.volumeTraded?.toLocaleString('en-IN')}
            </span>
          </div>
        )}
      </div>
    </div>
  )
}