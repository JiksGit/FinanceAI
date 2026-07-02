import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const WS_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api')
  .replace('/api', '/ws')

/**
 * WebSocket으로 실시간 시세를 수신.
 * returns: { prices: Map<stockCode, PriceUpdateMessage>, connected }
 */
export function useMarketPrices() {
  const [prices, setPrices] = useState(new Map())
  const [connected, setConnected] = useState(false)
  const clientRef = useRef(null)

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true)
        client.subscribe('/topic/market-prices', (message) => {
          try {
            const updates = JSON.parse(message.body)
            setPrices((prev) => {
              const next = new Map(prev)
              updates.forEach((p) => next.set(p.stockCode, p))
              return next
            })
          } catch {}
        })
      },
      onDisconnect: () => setConnected(false),
      onStompError: () => setConnected(false),
    })

    client.activate()
    clientRef.current = client

    return () => { client.deactivate() }
  }, [])

  return { prices, connected }
}
