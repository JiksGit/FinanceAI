import { useEffect, useState } from 'react'
import { getTopStocks } from '../../api/stockApi'
import LoadingSpinner from '../common/LoadingSpinner'

const MARKETS = [
  { label: '전체', value: '' },
  { label: 'KOSPI', value: 'KOSPI' },
  { label: 'KOSDAQ', value: 'KOSDAQ' },
]

function formatKRW(value) {
  if (!value) return '-'
  return Number(value).toLocaleString('ko-KR') + '원'
}

function formatMarketCap(value) {
  if (!value) return '-'
  const trillion = value / 1_000_000_000_000
  if (trillion >= 1) return `${trillion.toFixed(1)}조`
  const billion = value / 100_000_000
  return `${billion.toFixed(0)}억`
}

export default function MarketTopTable({ limit = 50 }) {
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
    <div>
      {/* 탭 */}
      <div className="mb-4 flex gap-2">
        {MARKETS.map((m) => (
          <button
            key={m.value}
            onClick={() => setMarket(m.value)}
            className={`rounded-md px-3 py-1.5 text-sm ${
              market === m.value
                ? 'bg-indigo-600 text-white'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            }`}
          >
            {m.label}
          </button>
        ))}
      </div>

      {loading ? (
        <LoadingSpinner />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 text-xs text-slate-400">
                <th className="py-2 text-left font-medium">순위</th>
                <th className="py-2 text-left font-medium">종목코드</th>
                <th className="py-2 text-left font-medium">종목명</th>
                <th className="py-2 text-left font-medium">시장</th>
                <th className="py-2 text-right font-medium">현재가</th>
                <th className="py-2 text-right font-medium">등락</th>
                <th className="py-2 text-right font-medium">거래량</th>
                <th className="py-2 text-right font-medium">시가총액</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {stocks.map((stock, i) => {
                const isUp = stock.priceChange > 0
                const isDown = stock.priceChange < 0
                const changeColor = isUp ? 'text-red-500' : isDown ? 'text-blue-500' : 'text-slate-400'
                return (
                  <tr key={stock.stockCode} className="hover:bg-slate-50">
                    <td className="py-2.5 text-slate-400">{i + 1}</td>
                    <td className="py-2.5 font-mono text-slate-500">{stock.stockCode}</td>
                    <td className="py-2.5 font-medium text-slate-800">{stock.stockName}</td>
                    <td className="py-2.5">
                      <span className={`rounded px-1.5 py-0.5 text-xs ${
                        stock.market === 'KOSPI'
                          ? 'bg-blue-50 text-blue-600'
                          : 'bg-green-50 text-green-600'
                      }`}>
                        {stock.market}
                      </span>
                    </td>
                    <td className="py-2.5 text-right font-semibold text-slate-800">
                      {formatKRW(stock.closePrice)}
                    </td>
                    <td className={`py-2.5 text-right ${changeColor}`}>
                      {isUp ? '+' : ''}{Number(stock.priceChange).toLocaleString()}
                      <span className="ml-1 text-xs">
                        ({isUp ? '+' : ''}{Number(stock.changeRate).toFixed(2)}%)
                      </span>
                    </td>
                    <td className="py-2.5 text-right text-slate-500">
                      {Number(stock.volume).toLocaleString()}
                    </td>
                    <td className="py-2.5 text-right font-medium text-slate-700">
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
