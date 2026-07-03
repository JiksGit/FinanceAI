import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  CartesianGrid, Line, LineChart,
  ResponsiveContainer, Tooltip, XAxis, YAxis, ReferenceLine,
} from 'recharts'
import LoadingSpinner from '../components/common/LoadingSpinner'
import api from '../api/axios'

const RANGES = [
  { label: '1개월', value: '1mo' },
  { label: '3개월', value: '3mo' },
  { label: '6개월', value: '6mo' },
  { label: '1년', value: '1y' },
  { label: '2년', value: '2y' },
]

const CURRENCY_META = {
  USD: { name: '미국 달러', flag: '🇺🇸' },
  JPY: { name: '일본 엔', flag: '🇯🇵', note: '100엔 기준' },
  EUR: { name: '유로', flag: '🇪🇺' },
  CNY: { name: '중국 위안', flag: '🇨🇳' },
}

export default function ExchangeDetailPage() {
  const { currency } = useParams()
  const navigate = useNavigate()
  const cur = currency?.toUpperCase()
  const meta = CURRENCY_META[cur] || { name: cur, flag: '🌐' }

  const [range, setRange] = useState('3mo')
  const [history, setHistory] = useState([])
  const [loading, setLoading] = useState(true)
  const [todayRate, setTodayRate] = useState(null)

  // 오늘 환율
  useEffect(() => {
    api.get('/exchange/today').then((res) => {
      const found = res.data.rates?.find((r) => r.currency === cur)
      if (found) setTodayRate(found)
    }).catch(() => {})
  }, [cur])

  // 히스토리 (Yahoo Finance 기반)
  useEffect(() => {
    setLoading(true)
    api.get(`/correlation/exchange/${cur}/history`, { params: { range } })
      .then((res) => setHistory(res.data.history ?? []))
      .catch(() => setHistory([]))
      .finally(() => setLoading(false))
  }, [cur, range])

  const prices = history.map((d) => d.close)
  const minP = Math.min(...prices)
  const maxP = Math.max(...prices)
  const firstP = prices[0]
  const lastP = prices[prices.length - 1]
  const periodReturn = firstP ? ((lastP - firstP) / firstP * 100) : null
  const isUp = todayRate ? todayRate.change > 0 : false
  const isDown = todayRate ? todayRate.change < 0 : false
  const changeColor = isUp ? 'text-red-500' : isDown ? 'text-blue-500' : 'text-slate-500'
  const tickInterval = Math.max(1, Math.floor(history.length / 8))

  return (
    <div className="space-y-6">
      <button onClick={() => navigate(-1)}
        className="flex items-center gap-1 text-sm text-slate-400 hover:text-slate-700 transition-colors">
        ← 뒤로
      </button>

      {/* 헤더 */}
      <div className="rounded-xl border border-slate-200 bg-white p-6">
        <div className="flex items-start gap-3 mb-4">
          <span className="text-3xl">{meta.flag}</span>
          <div>
            <h1 className="text-2xl font-bold text-slate-800">{cur}/KRW</h1>
            <p className="text-sm text-slate-400">{meta.name}{meta.note ? ` · ${meta.note}` : ''}</p>
          </div>
        </div>
        {todayRate ? (
          <div>
            <p className={`text-3xl font-bold tabular-nums ${changeColor}`}>
              ₩{Number(todayRate.rate).toLocaleString('ko-KR', { minimumFractionDigits: 2 })}
            </p>
            <span className={`text-sm font-medium mt-1 inline-block ${changeColor}`}>
              {isUp ? '▲' : isDown ? '▼' : '─'}
              {isUp ? '+' : ''}{Number(todayRate.change).toFixed(2)}원
              ({isUp ? '+' : ''}{Number(todayRate.changeRate).toFixed(2)}%) 전일 대비
            </span>
          </div>
        ) : <LoadingSpinner />}
      </div>

      {/* 요약 카드 */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        {[
          { label: '현재 환율', value: todayRate ? `₩${Number(todayRate.rate).toLocaleString()}` : '-' },
          { label: '기간 최고', value: maxP ? `₩${maxP.toLocaleString()}` : '-' },
          { label: '기간 최저', value: minP ? `₩${minP.toLocaleString()}` : '-' },
          {
            label: `${RANGES.find(r => r.value === range)?.label ?? ''} 변동`,
            value: periodReturn != null ? `${periodReturn >= 0 ? '+' : ''}${periodReturn.toFixed(2)}%` : '-',
            color: periodReturn != null ? (periodReturn >= 0 ? 'text-red-500' : 'text-blue-500') : 'text-slate-800'
          },
        ].map(({ label, value, color }) => (
          <div key={label} className="rounded-lg border border-slate-200 bg-white p-4">
            <p className="text-xs text-slate-400 mb-1">{label}</p>
            <p className={`text-lg font-bold tabular-nums ${color ?? 'text-slate-800'}`}>{value}</p>
          </div>
        ))}
      </div>

      {/* 차트 */}
      <div className="rounded-xl border border-slate-200 bg-white p-5">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-sm font-semibold text-slate-700">환율 추이 (KRW)</h2>
          <div className="flex gap-1">
            {RANGES.map((r) => (
              <button key={r.value} onClick={() => setRange(r.value)}
                className={`rounded px-3 py-1 text-xs font-medium transition-colors ${
                  range === r.value ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                }`}>
                {r.label}
              </button>
            ))}
          </div>
        </div>

        {loading ? (
          <div className="flex justify-center py-16"><LoadingSpinner /></div>
        ) : history.length === 0 ? (
          <p className="py-16 text-center text-sm text-slate-400">데이터가 없습니다.</p>
        ) : (
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={history} margin={{ top: 8, right: 16, left: 8, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
              <XAxis dataKey="date" tick={{ fontSize: 11, fill: '#94a3b8' }}
                interval={tickInterval} tickLine={false}
                tickFormatter={(v) => v.slice(5)} />
              <YAxis tick={{ fontSize: 11, fill: '#94a3b8' }} tickLine={false} axisLine={false}
                domain={[minP * 0.995, maxP * 1.005]} width={64}
                tickFormatter={(v) => `₩${v.toLocaleString()}`} />
              <Tooltip content={({ active, payload }) => {
                if (!active || !payload?.length) return null
                const d = payload[0].payload
                return (
                  <div className="rounded-lg border border-slate-200 bg-white p-3 shadow-lg text-xs">
                    <p className="font-semibold text-slate-600 mb-1">{d.date}</p>
                    <p className="text-slate-800 font-bold">₩{d.close.toLocaleString('ko-KR', { minimumFractionDigits: 2 })}</p>
                  </div>
                )
              }} />
              <ReferenceLine y={firstP} stroke="#cbd5e1" strokeDasharray="4 2" />
              <Line type="monotone" dataKey="close" stroke="#6366f1"
                strokeWidth={1.5} dot={false} activeDot={{ r: 4 }} />
            </LineChart>
          </ResponsiveContainer>
        )}
        <p className="mt-2 text-right text-xs text-slate-300">출처: Yahoo Finance ({cur}KRW=X)</p>
      </div>
    </div>
  )
}
