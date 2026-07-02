import { useEffect, useState, useCallback } from 'react'
import { getMarketIndices } from '../../api/stockApi'

function IndexCard({ index, loading }) {
  if (loading) {
    return (
      <div className="flex-1 rounded-xl border border-slate-200 bg-white p-4 animate-pulse">
        <div className="h-3 w-16 bg-slate-100 rounded mb-2" />
        <div className="h-7 w-28 bg-slate-100 rounded mb-1" />
        <div className="h-3 w-20 bg-slate-100 rounded" />
      </div>
    )
  }

  const isUp = index.change > 0
  const isDown = index.change < 0
  const changeColor = isUp ? 'text-red-500' : isDown ? 'text-blue-500' : 'text-slate-400'
  const badgeBg = isUp ? 'bg-red-50' : isDown ? 'bg-blue-50' : 'bg-slate-50'

  return (
    <div className="flex-1 rounded-xl border border-slate-200 bg-white p-4">
      <div className="flex items-center justify-between mb-1">
        <span className="text-xs font-semibold text-slate-500 tracking-wide">{index.name}</span>
        <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${badgeBg} ${changeColor}`}>
          {isUp ? '▲' : isDown ? '▼' : '─'} {Math.abs(Number(index.changeRate)).toFixed(2)}%
        </span>
      </div>
      <p className="text-2xl font-bold text-slate-900 tabular-nums">
        {Number(index.currentValue).toLocaleString('ko-KR', { maximumFractionDigits: 2 })}
      </p>
      <p className={`text-xs mt-0.5 tabular-nums ${changeColor}`}>
        {isUp ? '+' : ''}{Number(index.change).toLocaleString('ko-KR', { maximumFractionDigits: 2 })} 전일 대비
      </p>
    </div>
  )
}

export default function MarketIndexWidget() {
  const [indices, setIndices] = useState([])
  const [loading, setLoading] = useState(true)
  const [lastUpdated, setLastUpdated] = useState(null)

  const fetchIndices = useCallback(() => {
    getMarketIndices()
      .then((data) => {
        setIndices(data)
        setLastUpdated(new Date())
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    fetchIndices()
    // 1분마다 갱신
    const timer = setInterval(fetchIndices, 60_000)
    return () => clearInterval(timer)
  }, [fetchIndices])

  const PLACEHOLDERS = [{ name: 'KOSPI' }, { name: 'KOSDAQ' }]
  const displayItems = loading ? PLACEHOLDERS : indices.length > 0 ? indices : PLACEHOLDERS

  return (
    <div>
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-sm font-semibold text-slate-700">국내 증시</h2>
        {lastUpdated && (
          <span className="text-xs text-slate-400">
            {lastUpdated.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })} 기준
          </span>
        )}
      </div>
      <div className="flex gap-3">
        {displayItems.map((idx, i) => (
          <IndexCard key={idx.name ?? i} index={idx} loading={loading} />
        ))}
      </div>
    </div>
  )
}
