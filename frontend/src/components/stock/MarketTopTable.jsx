import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getTopStocks } from '../../api/stockApi'
import { useMarketPrices } from '../../hooks/useMarketPrices'
import LoadingSpinner from '../common/LoadingSpinner'

const MARKETS = [
  { label: '전체', value: '' },
  { label: 'KOSPI', value: 'KOSPI' },
  { label: 'KOSDAQ', value: 'KOSDAQ' },
]

export function formatMarketCap(value) {
  if (!value || value === 0) return '-'
  // value 단위: 억원
  if (value >= 10000) return `${(value / 10000).toFixed(1)}조`
  return `${Number(value).toLocaleString('ko-KR')}억`
}

export function priceColor(change) {
  if (change > 0) return 'text-red-500'
  if (change < 0) return 'text-blue-500'
  return 'text-slate-400'
}

export function priceBg(change) {
  if (change > 0) return 'bg-red-50 text-red-600'
  if (change < 0) return 'bg-blue-50 text-blue-600'
  return 'bg-slate-100 text-slate-500'
}

export default function MarketTopTable({ limit = 50, onSelect, selectedCode, linkToDetail = false }) {
  const navigate = useNavigate()
  const [stocks, setStocks] = useState([])
  const [market, setMarket] = useState('')
  const [loading, setLoading] = useState(true)
  const { prices: livePrices, connected } = useMarketPrices()

  useEffect(() => {
    setLoading(true)
    getTopStocks(market, limit)
      .then(setStocks)
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [market, limit])

  return (
    <div className="flex h-full flex-col">
      {/* 시장 탭 */}
      <div className="mb-3 flex gap-1.5">
        {MARKETS.map((m) => (
          <button
            key={m.value}
            onClick={() => setMarket(m.value)}
            className={`rounded px-3 py-1 text-sm font-medium transition-colors ${
              market === m.value
                ? 'bg-slate-800 text-white'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            {m.label}
          </button>
        ))}
        <span className="ml-auto flex items-center gap-2 text-xs text-slate-400 self-center">
          {stocks.length > 0 && `${stocks.length}종목`}
          <span className={`flex items-center gap-1 ${connected ? 'text-green-500' : 'text-slate-300'}`}>
            <span className={`inline-block h-1.5 w-1.5 rounded-full ${connected ? 'bg-green-500 animate-pulse' : 'bg-slate-300'}`} />
            {connected ? '실시간' : '연결 중'}
          </span>
        </span>
      </div>

      {loading ? (
        <LoadingSpinner />
      ) : (
        <div className="overflow-y-auto flex-1">
          <table className="w-full text-sm">
            <thead className="sticky top-0 bg-slate-50 z-10">
              <tr className="border-b border-slate-200 text-xs text-slate-400">
                <th className="py-2 pl-2 text-center font-medium w-8">순위</th>
                <th className="py-2 text-left font-medium">종목</th>
                <th className="py-2 text-right font-medium pr-2">현재가</th>
                <th className="py-2 text-right font-medium pr-2">등락률</th>
                <th className="py-2 text-right font-medium pr-2 hidden lg:table-cell">거래량</th>
                <th className="py-2 text-right font-medium pr-2 hidden xl:table-cell">시가총액</th>
              </tr>
            </thead>
            <tbody>
              {stocks.map((stock, i) => {
                // 실시간 가격으로 오버레이 (있으면 사용)
                const live = livePrices.get(stock.stockCode)
                const price = live?.closePrice ?? stock.closePrice
                const change = live?.priceChange ?? stock.priceChange
                const rate = live?.changeRate ?? stock.changeRate
                const volume = live?.volume ?? stock.volume

                const isUp = change > 0
                const isDown = change < 0
                const isSelected = selectedCode === stock.stockCode
                return (
                  <tr
                    key={stock.stockCode}
                    onClick={() => {
                      if (linkToDetail) navigate(`/stocks/${stock.stockCode}`)
                      else onSelect?.(stock.stockCode)
                    }}
                    className={`border-b border-slate-50 cursor-pointer transition-colors ${
                      isSelected
                        ? 'bg-indigo-50 border-indigo-100'
                        : 'hover:bg-slate-50'
                    }`}
                  >
                    <td className="py-2 pl-2 text-center text-xs text-slate-400">{i + 1}</td>
                    <td className="py-2">
                      <div className="font-medium text-slate-800 leading-tight">{stock.stockName}</div>
                      <div className="flex items-center gap-1 mt-0.5">
                        <span className="text-xs text-slate-400 font-mono">{stock.stockCode}</span>
                        <span className={`text-xs px-1 rounded ${
                          stock.market === 'KOSPI'
                            ? 'text-blue-500'
                            : 'text-green-600'
                        }`}>{stock.market}</span>
                      </div>
                    </td>
                    <td className={`py-2 pr-2 text-right font-semibold tabular-nums transition-colors ${
                      isUp ? 'text-red-500' : isDown ? 'text-blue-500' : 'text-slate-800'
                    }`}>
                      {Number(price).toLocaleString('ko-KR')}
                    </td>
                    <td className={`py-2 pr-2 text-right text-xs tabular-nums ${
                      isUp ? 'text-red-500' : isDown ? 'text-blue-500' : 'text-slate-400'
                    }`}>
                      <div>{isUp ? '+' : ''}{Number(change).toLocaleString()}</div>
                      <div className="font-medium">
                        {isUp ? '+' : ''}{Number(rate).toFixed(2)}%
                      </div>
                    </td>
                    <td className="py-2 pr-2 text-right text-xs text-slate-500 tabular-nums hidden lg:table-cell">
                      {Number(volume).toLocaleString()}
                    </td>
                    <td className="py-2 pr-2 text-right text-xs text-slate-600 font-medium hidden xl:table-cell">
                      {formatMarketCap(live?.marketCap ?? stock.marketCap)}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
