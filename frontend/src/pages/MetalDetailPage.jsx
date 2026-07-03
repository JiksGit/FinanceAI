import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { getMetalHistory, getMetalPrices } from '../api/metalsApi'
import {
  CartesianGrid, Line, LineChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis, ReferenceLine,
} from 'recharts'
import LoadingSpinner from '../components/common/LoadingSpinner'

const RANGES = [
  { label: '1개월', value: '1mo' },
  { label: '3개월', value: '3mo' },
  { label: '6개월', value: '6mo' },
  { label: '1년', value: '1y' },
  { label: '2년', value: '2y' },
]

const META = {
  XAU: { nameKr: '금', icon: '🥇', unit: 'oz', desc: '국제 금 현물 선물 (GC=F)' },
  XAG: { nameKr: '은', icon: '🥈', unit: 'oz', desc: '국제 은 현물 선물 (SI=F)' },
}

function InfoCard({ label, value, sub, color }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <p className="text-xs text-slate-400 mb-1">{label}</p>
      <p className={`text-lg font-bold tabular-nums ${color || 'text-slate-800'}`}>{value}</p>
      {sub && <p className="text-xs text-slate-400 mt-0.5">{sub}</p>}
    </div>
  )
}

function MetalChart({ data, range }) {
  if (!data || data.length === 0) {
    return <p className="py-16 text-center text-sm text-slate-400">차트 데이터가 없습니다.</p>
  }

  const prices = data.map((d) => d.close)
  const minPrice = Math.min(...prices)
  const maxPrice = Math.max(...prices)
  const firstPrice = prices[0]

  const chartData = data.map((d) => ({
    date: d.date.slice(5), // MM-DD
    fullDate: d.date,
    close: d.close,
    open: d.open,
    high: d.high,
    low: d.low,
  }))

  const isUp = prices[prices.length - 1] >= firstPrice
  const lineColor = isUp ? '#ef4444' : '#3b82f6'

  // X축 레이블 간격 (데이터 양에 따라)
  const tickInterval = Math.max(1, Math.floor(data.length / 8))

  return (
    <ResponsiveContainer width="100%" height={320}>
      <LineChart data={chartData} margin={{ top: 8, right: 16, left: 8, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
        <XAxis
          dataKey="date"
          tick={{ fontSize: 11, fill: '#94a3b8' }}
          interval={tickInterval}
          tickLine={false}
        />
        <YAxis
          domain={[minPrice * 0.995, maxPrice * 1.005]}
          tick={{ fontSize: 11, fill: '#94a3b8' }}
          tickLine={false}
          axisLine={false}
          tickFormatter={(v) => `$${v.toLocaleString('en-US', { maximumFractionDigits: 0 })}`}
          width={72}
        />
        <Tooltip
          content={({ active, payload }) => {
            if (!active || !payload?.length) return null
            const d = payload[0].payload
            return (
              <div className="rounded-lg border border-slate-200 bg-white p-3 shadow-lg text-xs">
                <p className="font-semibold text-slate-600 mb-1">{d.fullDate}</p>
                <p className="text-slate-800 font-bold">종가 ${d.close.toLocaleString('en-US', { minimumFractionDigits: 2 })}</p>
                <p className="text-slate-500">고가 ${d.high.toLocaleString('en-US', { minimumFractionDigits: 2 })}</p>
                <p className="text-slate-500">저가 ${d.low.toLocaleString('en-US', { minimumFractionDigits: 2 })}</p>
              </div>
            )
          }}
        />
        <ReferenceLine y={firstPrice} stroke="#cbd5e1" strokeDasharray="4 2" />
        <Line
          type="monotone"
          dataKey="close"
          stroke={lineColor}
          strokeWidth={1.5}
          dot={false}
          activeDot={{ r: 4, fill: lineColor }}
        />
      </LineChart>
    </ResponsiveContainer>
  )
}

export default function MetalDetailPage() {
  const { symbol } = useParams()           // XAU or XAG
  const navigate = useNavigate()
  const symbolUpper = symbol?.toUpperCase()
  const meta = META[symbolUpper] || META.XAU

  const [range, setRange] = useState('3mo')
  const [history, setHistory] = useState(null)
  const [currentMetal, setCurrentMetal] = useState(null)
  const [historyLoading, setHistoryLoading] = useState(true)

  // 현재가 조회
  useEffect(() => {
    getMetalPrices()
      .then((data) => {
        const found = data.find((m) => m.name === symbolUpper)
        if (found) setCurrentMetal(found)
      })
      .catch(() => {})
  }, [symbolUpper])

  // 히스토리 조회
  useEffect(() => {
    setHistoryLoading(true)
    getMetalHistory(symbolUpper, range)
      .then((data) => setHistory(data))
      .catch(() => setHistory(null))
      .finally(() => setHistoryLoading(false))
  }, [symbolUpper, range])

  const isUp = currentMetal && Number(currentMetal.changeRate) > 0
  const isDown = currentMetal && Number(currentMetal.changeRate) < 0
  const changeColor = isUp ? 'text-red-500' : isDown ? 'text-blue-500' : 'text-slate-500'

  // 차트 기간 수익률
  const histItems = history?.history ?? []
  const periodReturn = histItems.length >= 2
    ? ((histItems[histItems.length - 1].close - histItems[0].close) / histItems[0].close * 100)
    : null

  return (
    <div className="space-y-6">
      {/* 뒤로가기 */}
      <button
        onClick={() => navigate(-1)}
        className="flex items-center gap-1 text-sm text-slate-400 hover:text-slate-700 transition-colors"
      >
        ← 뒤로
      </button>

      {/* 헤더 */}
      <div className="rounded-xl border border-slate-200 bg-white p-6">
        <div className="flex items-start justify-between">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="text-2xl">{meta.icon}</span>
              <h1 className="text-2xl font-bold text-slate-800">{meta.nameKr}</h1>
              <span className="text-sm text-slate-400 border border-slate-200 rounded px-2 py-0.5">
                {meta.unit}
              </span>
            </div>
            <p className="text-xs text-slate-400">{meta.desc}</p>
          </div>
        </div>

        {currentMetal ? (
          <div className="mt-4">
            <p className={`text-3xl font-bold tabular-nums ${changeColor}`}>
              ${Number(currentMetal.priceUsd).toLocaleString('en-US', { minimumFractionDigits: 2 })}
            </p>
            <div className="flex items-center gap-3 mt-1">
              <span className={`text-sm font-medium ${changeColor}`}>
                {isUp ? '▲' : isDown ? '▼' : '─'}
                {isUp ? '+' : ''}{Number(currentMetal.changeUsd).toFixed(2)} USD
                ({isUp ? '+' : ''}{Number(currentMetal.changeRate).toFixed(2)}%)
              </span>
              <span className="text-xs text-slate-400">전일 대비</span>
            </div>
          </div>
        ) : (
          <div className="mt-4 h-12 flex items-center">
            <LoadingSpinner />
          </div>
        )}
      </div>

      {/* 요약 카드 */}
      {currentMetal && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <InfoCard
            label="USD 현재가"
            value={`$${Number(currentMetal.priceUsd).toLocaleString('en-US', { minimumFractionDigits: 2 })}`}
          />
          <InfoCard
            label="KRW 환산"
            value={`₩${Number(currentMetal.priceKrw).toLocaleString('ko-KR')}`}
            sub="USD/KRW 적용"
          />
          {symbolUpper === 'XAU' && Number(currentMetal.priceGram24k) > 0 && (
            <InfoCard
              label="24K 1g"
              value={`$${Number(currentMetal.priceGram24k).toFixed(2)}`}
              sub="그램당 가격"
            />
          )}
          {periodReturn !== null && (
            <InfoCard
              label={`${RANGES.find((r) => r.value === range)?.label ?? ''} 수익률`}
              value={`${periodReturn >= 0 ? '+' : ''}${periodReturn.toFixed(2)}%`}
              color={periodReturn >= 0 ? 'text-red-500' : 'text-blue-500'}
            />
          )}
        </div>
      )}

      {/* 차트 */}
      <div className="rounded-xl border border-slate-200 bg-white p-5">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-sm font-semibold text-slate-700">가격 추이 (USD/oz)</h2>
          <div className="flex gap-1">
            {RANGES.map((r) => (
              <button
                key={r.value}
                onClick={() => setRange(r.value)}
                className={`rounded px-3 py-1 text-xs font-medium transition-colors ${
                  range === r.value
                    ? 'bg-slate-800 text-white'
                    : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                }`}
              >
                {r.label}
              </button>
            ))}
          </div>
        </div>

        {historyLoading ? (
          <div className="flex justify-center py-16">
            <LoadingSpinner />
          </div>
        ) : (
          <MetalChart data={histItems} range={range} />
        )}

        <p className="mt-2 text-right text-xs text-slate-300">출처: Yahoo Finance (GC=F / SI=F)</p>
      </div>
    </div>
  )
}
