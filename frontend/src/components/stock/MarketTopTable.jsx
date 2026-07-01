import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getTopStocks } from '../../api/stockApi'
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
        <span className="ml-auto text-xs text-slate-400 self-center">
          {stocks.length > 0 && `${stocks.length}종목`}
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
                const isUp = stock.priceChange > 0
                const isDown = stock.priceChange < 0
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
                    <td className={`py-2 pr-2 text-right font-semibold tabular-nums ${
                      isUp ? 'text-red-500' : isDown ? 'text-blue-500' : 'text-slate-800'
                    }`}>
                      {Number(stock.closePrice).toLocaleString('ko-KR')}
                    </td>
                    <td className={`py-2 pr-2 text-right text-xs tabular-nums ${
                      isUp ? 'text-red-500' : isDown ? 'text-blue-500' : 'text-slate-400'
                    }`}>
                      <div>{isUp ? '+' : ''}{Number(stock.priceChange).toLocaleString()}</div>
                      <div className="font-medium">
                        {isUp ? '+' : ''}{Number(stock.changeRate).toFixed(2)}%
                      </div>
                    </td>
                    <td className="py-2 pr-2 text-right text-xs text-slate-500 tabular-nums hidden lg:table-cell">
                      {Number(stock.volume).toLocaleString()}
                    </td>
                    <td className="py-2 pr-2 text-right text-xs text-slate-600 font-medium hidden xl:table-cell">
                      {formatMarketCap(stock.marketCap)}
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
