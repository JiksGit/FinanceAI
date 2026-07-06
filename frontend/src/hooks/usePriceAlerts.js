import { useEffect, useState, useCallback } from 'react'
import { getUnreadCount } from '../api/alertApi'
import { useAuth } from './useAuth'

export function usePriceAlerts() {
  const { isAuthenticated } = useAuth()
  const [unreadCount, setUnreadCount] = useState(0)

  const refresh = useCallback(() => {
    if (!isAuthenticated) { setUnreadCount(0); return }
    getUnreadCount().then(setUnreadCount).catch(() => {})
  }, [isAuthenticated])

  useEffect(() => {
    refresh()
    const timer = setInterval(refresh, 60_000)
    return () => clearInterval(timer)
  }, [refresh])

  return { unreadCount, refresh }
}
