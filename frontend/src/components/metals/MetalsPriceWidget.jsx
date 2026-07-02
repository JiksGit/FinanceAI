import { useEffect, useState, useCallback } from 'react'
import { getMetalPrices } from '../../api/metalsApi'

const METAL_ICON = { GOLD: '🥇', SILVER: '🥈' }

function MetalCard({ metal, loading }) {
  if (loading) {
    return (
      <div className="flex-1 rounded-xl border border-slate-200 bg-white p-4 animate-pulse">
        <div className="h-3 w-12 bg-slate-100 rounded mb-2" />
        <div className="h-6 w-28 bg-slate-100 rounded mb-1" />
        <div className="h-3 w-20 bg-slate-100 rounded" />
      </div>
    )
  }

  const isUp = Number(metal.changeUsd) > 0
  const isDown = Number(metal.changeUsd) < 0
  const changeColor = isUp ? 'text-red-500' : isDown ? 'text-blue-500' : 'text-slate-400'

  return (
    <div className="flex-1 rounded-xl border border-slate-200 bg-white p-4">
      <div className="flex items-center justify-between mb-1">
        <span className="text-xs font-semibold text-slate-500 flex items-center gap-1">
          {METAL_ICON[metal.name]} {metal.nameKr}
          <span className="text-slate-300 font-normal">/ oz</span>
        </span>
        <span className={`text-xs font-medium ${changeColor}`}>
          {isUp ? '▲' : isDown ? '▼' : '─'} {Math.abs(Number(metal.changeRate)).toFixed(2)}%
        </span>
      </div>

      {/* USD 가격 */}
      <p className="text-xl font-bold text-slate-900 tabular-nums">
        ${Number(metal.priceUsd).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
      </p>

      {/* KRW 환산 */}
      <p className="text-xs text-slate-400 tabular-nums mt-0.5">
        ≈ ₩{Number(metal.priceKrw).toLocaleString('ko-KR')}
      </p>

      {/* 등락 */}
      <p className={`text-xs mt-1 tabular-nums ${changeColor}`}>
        {isUp ? '+' : ''}{Number(metal.changeUsd).toFixed(2)} USD 전일 대비
      </p>

      {/* 그램당 가격 (금만) */}
      {metal.name === 'GOLD' && Number(metal.priceGram24k) > 0 && (
        <p className="text-xs text-slate-400 mt-1">
          24K 1g = ${Number(metal.priceGram24k).toFixed(2)}
        </p>
      )}
    </div>
  )
}

export default function MetalsPriceWidget() {
  const [metals, setMetals] = useState([])
  const [loading, setLoading] = useState(true)
  const [lastUpdated, setLastUpdated] = useState(null)

  const fetch = useCallback(() => {
    getMetalPrices()
      .then((data) => {
        setMetals(data)
        setLastUpdated(new Date())
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    fetch()
    const timer = setInterval(fetch, 60_000)
    return () => clearInterval(timer)
  }, [fetch])

  const PLACEHOLDERS = [{ name: 'GOLD', nameKr: '금' }, { name: 'SILVER', nameKr: '은' }]
  const items = loading ? PLACEHOLDERS : metals.length > 0 ? metals : PLACEHOLDERS

  return (
    <div>
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-sm font-semibold text-slate-700">귀금속 시세</h2>
        {lastUpdated && (
          <span className="text-xs text-slate-400">
            {lastUpdated.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })} 기준
          </span>
        )}
      </div>
      <div className="flex gap-3">
        {items.map((m, i) => (
          <MetalCard key={m.name ?? i} metal={m} loading={loading} />
        ))}
      </div>
    </div>
  )
}
