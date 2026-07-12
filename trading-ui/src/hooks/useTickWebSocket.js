import { useEffect, useRef, useCallback, useState } from 'react'

/**
 * Manages the WebSocket connection to the backend tick stream.
 *
 * Usage:
 *   const { subscribe, unsubscribe, connected } = useTickWebSocket(onTick)
 *
 *   // Subscribe to instruments
 *   subscribe([256265, 408065])
 *
 *   // onTick fires every time a tick arrives for any subscribed token
 *   function onTick(tick) {
 *     console.log(tick.instrumentToken, tick.lastPrice)
 *   }
 */
export function useTickWebSocket(onTick) {
  const ws = useRef(null)
  const [connected, setConnected] = useState(false)
  const onTickRef = useRef(onTick)

  // Keep onTick ref current without re-creating the WebSocket
  useEffect(() => {
    onTickRef.current = onTick
  }, [onTick])

  useEffect(() => {
    let reconnectTimer = null

    function connect() {
      // Use ws:// directly since we're in the browser
      // Vite proxy handles the forwarding to localhost:8080
      const socket = new WebSocket(`ws://${window.location.host}/ws/ticks`)
      ws.current = socket

      socket.onopen = () => {
        console.log('WebSocket connected')
        setConnected(true)
      }

      socket.onmessage = (event) => {
        const data = JSON.parse(event.data)

        // Ignore control messages (connected, subscribed, error)
        if (data.type) return

        // It's a tick — fire the callback
        onTickRef.current(data)
      }

      socket.onclose = () => {
        setConnected(false)
        console.log('WebSocket disconnected — reconnecting in 3s...')
        // Auto-reconnect after 3 seconds
        reconnectTimer = setTimeout(connect, 3000)
      }

      socket.onerror = (err) => {
        console.error('WebSocket error:', err)
      }
    }

    connect()

    return () => {
      clearTimeout(reconnectTimer)
      if (ws.current) ws.current.close()
    }
  }, []) // only runs once on mount

  const subscribe = useCallback((tokens) => {
    if (ws.current?.readyState === WebSocket.OPEN) {
      ws.current.send(JSON.stringify({ action: 'subscribe', tokens }))
    }
  }, [])

  const unsubscribe = useCallback((tokens) => {
    if (ws.current?.readyState === WebSocket.OPEN) {
      ws.current.send(JSON.stringify({ action: 'unsubscribe', tokens }))
    }
  }, [])

  return { connected, subscribe, unsubscribe }
}