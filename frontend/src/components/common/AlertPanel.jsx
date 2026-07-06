import { useEffect, useRef, useState } from 'react'
import { getAlerts, markAllRead, markRead } from '../../api/alertApi'

function timeAgo(dateStr) {
  const diff = (Date.now() - new Date(dateStr)) / 1000
  if (diff < 60) return '방금 전'
  if (diff < 3600) return `${Math.floor(diff / 60)}분 전`
  if (diff < 86400) return `${Math.floor(diff / 3600)}시간 전`
  return `${Math.floor(diff / 86400)}일 전`
}

export default function AlertPanel({ onClose, onReadAll }) {
  const [alerts, setAlerts] = useState([])
  const panelRef = useRef(null)

  useEffect(() => {
    getAlerts().then(setAlerts).catch(() => {})
  }, [])

  useEffect(() => {
    const handler = (e) => {
      if (panelRef.current && !panelRef.current.contains(e.target)) onClose()
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [onClose])

  const handleRead = async (id) => {
    await markRead(id)
    setAlerts((prev) => prev.map((a) => a.id === id ? { ...a, read: true } : a))
  }

  const handleReadAll = async () => {
    await markAllRead()
    setAlerts((prev) => prev.map((a) => ({ ...a, read: true })))
    onReadAll()
  }

  const unread = alerts.filter((a) => !a.read)

  return (
    <div
      ref={panelRef}
      className="absolute right-0 top-10 z-50 w-80 rounded-xl border border-slate-200 bg-white shadow-xl"
    >
      <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
        <h3 className="text-sm font-semibold text-slate-700">
          목표가 알림 {unread.length > 0 && (
            <span className="ml-1 rounded-full bg-red-500 px-1.5 py-0.5 text-xs text-white">
              {unread.length}
            </span>
          )}
        </h3>
        {unread.length > 0 && (
          <button onClick={handleReadAll} className="text-xs text-indigo-500 hover:underline">
            모두 읽음
          </button>
        )}
      </div>

      <div className="max-h-80 overflow-y-auto">
        {alerts.length === 0 ? (
          <p className="py-8 text-center text-sm text-slate-400">알림이 없습니다</p>
        ) : (
          alerts.slice(0, 20).map((alert) => (
            <div
              key={alert.id}
              onClick={() => !alert.read && handleRead(alert.id)}
              className={`flex items-start gap-3 border-b border-slate-50 px-4 py-3 transition-colors
                ${alert.read ? 'opacity-50' : 'cursor-pointer hover:bg-slate-50'}`}
            >
              <span className="mt-0.5 text-lg">{alert.targetAbove ? '📈' : '📉'}</span>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-slate-800">
                  {alert.stockName}
                  <span className={`ml-1.5 text-xs font-semibold ${alert.targetAbove ? 'text-red-500' : 'text-blue-500'}`}>
                    목표가 {alert.targetAbove ? '이상' : '이하'} 도달
                  </span>
                </p>
                <p className="text-xs text-slate-500 mt-0.5">
                  목표가 ₩{Number(alert.targetPrice).toLocaleString('ko-KR')}
                  {' → '}
                  현재 ₩{Number(alert.triggeredPrice).toLocaleString('ko-KR')}
                </p>
                <p className="text-xs text-slate-400 mt-0.5">{timeAgo(alert.createdAt)}</p>
              </div>
              {!alert.read && (
                <span className="mt-1.5 h-2 w-2 rounded-full bg-red-400 flex-shrink-0" />
              )}
            </div>
          ))
        )}
      </div>
    </div>
  )
}
