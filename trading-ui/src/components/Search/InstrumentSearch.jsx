import { useState, useRef, useEffect } from 'react'
import axios from 'axios'
import './InstrumentSearch.css'

export function InstrumentSearch({ onAdd, existingTokens = [] }) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [adding, setAdding] = useState(null)
  const debounceRef = useRef(null)
  const containerRef = useRef(null)

  // Debounced search
  useEffect(() => {
    if (!query.trim() || query.length < 2) {
      setResults([])
      setOpen(false)
      return
    }

    clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(async () => {
      setLoading(true)
      try {
        const { data } = await axios.get('/api/v1/instruments/search', {
          params: { q: query, exchange: 'NSE', limit: 10 }
        })
        setResults(data.data || [])
        setOpen(true)
      } catch (err) {
        console.error('Search failed:', err)
      } finally {
        setLoading(false)
      }
    }, 300)

    return () => clearTimeout(debounceRef.current)
  }, [query])

  // Close on outside click
  useEffect(() => {
    function handleClick(e) {
      if (!containerRef.current?.contains(e.target)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  const handleAdd = async (instrument) => {
    setAdding(instrument.instrumentToken)
    try {
      await axios.post(
        `/api/v1/instruments/watchlist/${instrument.instrumentToken}`,
        null,
        { params: { syncHistory: true } }
      )
      onAdd(instrument)
      setQuery('')
      setResults([])
      setOpen(false)
    } catch (err) {
      console.error('Failed to add:', err)
    } finally {
      setAdding(null)
    }
  }

  return (
    <div className="search-container" ref={containerRef}>
      <div className="search-input-wrap">
        <span className="search-icon">⌕</span>
        <input
          className="search-input"
          placeholder="Search instruments..."
          value={query}
          onChange={e => setQuery(e.target.value)}
          onFocus={() => results.length > 0 && setOpen(true)}
        />
        {loading && <span className="search-spinner">...</span>}
      </div>

      {open && results.length > 0 && (
        <div className="search-dropdown">
          {results.map(inst => {
            const alreadyAdded = existingTokens.includes(inst.instrumentToken)
            return (
                <div key={inst.instrumentToken} className="search-result">
                <div className="search-result-info">
                    <span className="search-result-symbol">{inst.tradingSymbol}</span>
                    <span className="search-result-name">{inst.name}</span>
                </div>
                <button
                    className={`search-add-btn ${alreadyAdded ? 'added' : ''}`}
                    onClick={() => !alreadyAdded && handleAdd(inst)}
                    disabled={adding === inst.instrumentToken || alreadyAdded}
                >
                    {alreadyAdded ? '✓ Added' : adding === inst.instrumentToken ? '...' : '+ Add'}
                </button>
                </div>
            )
         })}
        </div>
      )}

      {open && query.length >= 2 && results.length === 0 && !loading && (
        <div className="search-dropdown">
          <div className="search-no-results">No results for "{query}"</div>
        </div>
      )}
    </div>
  )
}