import { useState, useEffect } from 'react'
import { fetchWatchlist, removeFromWatchlist } from '../../services/api'
import { InstrumentSearch } from '../Search/InstrumentSearch'
import './Watchlist.css'

export function Watchlist({ selectedToken, onSelect, livePrices }) {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [deleting, setDeleting] = useState(null)

  useEffect(() => {
    fetchWatchlist()
      .then(setItems)
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  const handleAdd = (instrument) => {
    setItems(prev => [...prev, {
      instrument_token: instrument.instrumentToken,
      trading_symbol: instrument.tradingSymbol,
      exchange: instrument.exchange?.name || 'NSE',
      display_order: prev.length + 1
    }])
  }

  const handleDelete = async (e, token) => {
    e.stopPropagation() // prevent selecting the item when deleting
    setDeleting(token)
    try {
      await removeFromWatchlist(token)
      setItems(prev => prev.filter(i => i.instrument_token !== token))
    } catch (err) {
      console.error('Failed to remove:', err)
    } finally {
      setDeleting(null)
    }
  }

  if (loading) return <div className="watchlist-loading">Loading...</div>

  return (
    <div className="watchlist">
      <div className="watchlist-header">Watchlist</div>
      <InstrumentSearch onAdd={handleAdd} existingTokens={items.map(i => i.instrument_token)} />
      <div className="watchlist-items">
        {items.map(item => {
          const token = item.instrument_token
          const livePrice = livePrices[token]
          const isSelected = token === selectedToken

          return (
            <div
              key={token}
              className={`watchlist-item ${isSelected ? 'selected' : ''}`}
              onClick={() => onSelect(token, item.trading_symbol)}
            >
              <div className="watchlist-left">
                <span className="watchlist-symbol">{item.trading_symbol}</span>
                <span className="watchlist-exchange">{item.exchange}</span>
              </div>
              <div className="watchlist-right">
                <span className="watchlist-price">
                  {livePrice ? livePrice.toFixed(2) : '—'}
                </span>
                <button
                  className={`watchlist-delete ${deleting === token ? 'deleting' : ''}`}
                  onClick={(e) => handleDelete(e, token)}
                  title="Remove from watchlist"
                >
                  ×
                </button>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}