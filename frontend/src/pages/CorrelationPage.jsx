import { useEffect, useState } from 'react'
import {
  CartesianGrid, Line, LineChart, Legend,
  ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import LoadingSpinner from '../components/common/LoadingSpinner'
import api from '../api/axios'

const RANGES = [
  { label: '1개월', value: '1mo' },
  { label: '3개월', value: '3mo' },
  { label: '6개월', value: '6mo' },
  { label: '1년', value: '1y' },
]

const COLORS = [
  '#6366f1', '#f59e0b', '#10b981', '#ef4444',
  '#3b82f6', '#8b5cf6', '#ec4899', '#14b8a6',
]

// 상관계수 → 배경색
function corrColor(r) {
  const abs = Math.abs(r)
  if (r >= 0.7)  return 'bg-red-100 text-red-700'
  if (r >= 0.4)  return 'bg-red-50 text-red-500'
  if (r >= 0.1)  return 'bg-slate-50 text-slate-500'
  if (r > -0.1)  return 'bg-white text-slate-400'
  if (r > -0.4)  return 'bg-blue-50 text-blue-500'
  if (r > -0.7)  return 'bg-blue-100 text-blue-600'
  return                'bg-blue-200 text-blue-800'
}

function corrLabel(r) {
  const abs = Math.abs(r)
  if (abs >= 0.7) return r > 0 ? '강한 양(+)' : '강한 음(-)'
  if (abs >= 0.4) return r > 0 ? '보통 양(+)' : '보통 음(-)'
  if (abs >= 0.1) return r > 0 ? '약한 양(+)' : '약한 음(-)'
  return '무상관'
}

export default function CorrelationPage() {
  const [range, setRange] = useState('3mo')
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [hoveredCell, setHoveredCell] = useState(null)

  useEffect(() => {
    setLoading(true)
    api.get('/correlation', { params: { range } })
      .then((res) => setData(res.data))
      .catch(() => setData(null))
      .finally(() => setLoading(false))
  }, [range])

  const labels = data?.labels ?? []
  const matrix = data?.matrix ?? []
  const series = data?.series ?? []

  // 차트 데이터: 날짜별로 { date, 금: 100.2, 은: 99.8, ... }
  const chartData = (() => {
    if (!series.length) return []
    const byDate = {}
    series.forEach(({ label, data: points }) => {
      points.forEach(({ date, normalized }) => {
        if (!byDate[date]) byDate[date] = { date: date.slice(5) }
        byDate[date][label] = normalized
      })
    })
    return Object.values(byDate).sort((a, b) => a.date.localeCompare(b.date))
  })()

  // 주목할 상관관계 (대각선 제외, 절댓값 ≥ 0.5)
  const notable = matrix
    .filter((c) => c.assetA !== c.assetB && Math.abs(c.r) >= 0.5)
    .sort((a, b) => Math.abs(b.r) - Math.abs(a.r))
    .filter((c, i, arr) =>
      arr.findIndex((x) => (x.assetA === c.assetB && x.assetB === c.assetA)) > i
    )

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-800">자산 상관관계 분석</h1>
          <p className="text-sm text-slate-400 mt-0.5">
            환율·귀금속·지수의 가격 연동성을 분석합니다
          </p>
        </div>
        <div className="flex gap-1">
          {RANGES.map((r) => (
            <button key={r.value} onClick={() => setRange(r.value)}
              className={`rounded px-3 py-1.5 text-xs font-medium transition-colors ${
                range === r.value ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
              }`}>
              {r.label}
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center py-24"><LoadingSpinner /></div>
      ) : !data || labels.length === 0 ? (
        <p className="py-24 text-center text-slate-400">데이터를 불러올 수 없습니다.</p>
      ) : (
        <>
          {/* 정규화 가격 차트 (첫날=100 기준) */}
          <div className="rounded-xl border border-slate-200 bg-white p-5">
            <h2 className="text-sm font-semibold text-slate-700 mb-1">정규화 가격 추이</h2>
            <p className="text-xs text-slate-400 mb-4">첫 거래일 = 100 기준으로 통일해 비교</p>
            <ResponsiveContainer width="100%" height={320}>
              <LineChart data={chartData} margin={{ top: 4, right: 16, left: 0, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                <XAxis dataKey="date" tick={{ fontSize: 10, fill: '#94a3b8' }}
                  interval={Math.max(1, Math.floor(chartData.length / 8))} tickLine={false} />
                <YAxis tick={{ fontSize: 10, fill: '#94a3b8' }} tickLine={false} axisLine={false}
                  tickFormatter={(v) => `${v.toFixed(0)}`} width={40} />
                <Tooltip
                  content={({ active, payload, label }) => {
                    if (!active || !payload?.length) return null
                    return (
                      <div className="rounded-lg border border-slate-200 bg-white p-3 shadow-lg text-xs max-w-[200px]">
                        <p className="font-semibold text-slate-500 mb-2">{label}</p>
                        {payload.map((p) => (
                          <div key={p.name} className="flex justify-between gap-3">
                            <span style={{ color: p.color }}>{p.name}</span>
                            <span className="font-medium text-slate-700">{Number(p.value).toFixed(1)}</span>
                          </div>
                        ))}
                      </div>
                    )
                  }}
                />
                <Legend wrapperStyle={{ fontSize: 11 }} />
                {labels.map((label, i) => (
                  <Line key={label} type="monotone" dataKey={label}
                    stroke={COLORS[i % COLORS.length]} strokeWidth={1.5}
                    dot={false} activeDot={{ r: 3 }} />
                ))}
              </LineChart>
            </ResponsiveContainer>
          </div>

          {/* 상관계수 매트릭스 */}
          <div className="rounded-xl border border-slate-200 bg-white p-5">
            <h2 className="text-sm font-semibold text-slate-700 mb-1">상관계수 매트릭스</h2>
            <p className="text-xs text-slate-400 mb-4">
              +1.0 = 완전 동조 (빨강) · 0 = 무관 · -1.0 = 완전 역행 (파랑)
            </p>
            <div className="overflow-x-auto">
              <table className="text-xs border-collapse">
                <thead>
                  <tr>
                    <th className="w-20 p-1" />
                    {labels.map((l) => (
                      <th key={l} className="p-1 text-center font-medium text-slate-500 whitespace-nowrap min-w-[72px]">
                        {l}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {labels.map((rowLabel) => (
                    <tr key={rowLabel}>
                      <td className="p-1 pr-2 font-medium text-slate-600 whitespace-nowrap text-right">
                        {rowLabel}
                      </td>
                      {labels.map((colLabel) => {
                        const cell = matrix.find(
                          (c) => c.assetA === rowLabel && c.assetB === colLabel
                        )
                        const r = cell?.r ?? 0
                        const isSelf = rowLabel === colLabel
                        const isHovered = hoveredCell &&
                          ((hoveredCell[0] === rowLabel && hoveredCell[1] === colLabel) ||
                           (hoveredCell[0] === colLabel && hoveredCell[1] === rowLabel))
                        return (
                          <td key={colLabel}
                            onMouseEnter={() => !isSelf && setHoveredCell([rowLabel, colLabel])}
                            onMouseLeave={() => setHoveredCell(null)}
                            className={`p-1 text-center rounded font-mono transition-all cursor-default
                              ${isSelf ? 'bg-slate-100 text-slate-400' : corrColor(r)}
                              ${isHovered ? 'ring-2 ring-slate-400 ring-inset' : ''}
                            `}>
                            {isSelf ? '—' : r.toFixed(2)}
                          </td>
                        )
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* 색상 범례 */}
            <div className="mt-4 flex flex-wrap gap-2 text-xs">
              {[
                { label: '강한 양(+0.7↑)', cls: 'bg-red-100 text-red-700' },
                { label: '보통 양(+0.4↑)', cls: 'bg-red-50 text-red-500' },
                { label: '약한 양', cls: 'bg-slate-50 text-slate-500' },
                { label: '무상관', cls: 'bg-white text-slate-400 border border-slate-200' },
                { label: '약한 음', cls: 'bg-blue-50 text-blue-500' },
                { label: '보통 음(-0.4↓)', cls: 'bg-blue-100 text-blue-600' },
                { label: '강한 음(-0.7↓)', cls: 'bg-blue-200 text-blue-800' },
              ].map(({ label, cls }) => (
                <span key={label} className={`rounded px-2 py-0.5 ${cls}`}>{label}</span>
              ))}
            </div>
          </div>

          {/* 주목할 상관관계 */}
          {notable.length > 0 && (
            <div className="rounded-xl border border-slate-200 bg-white p-5">
              <h2 className="text-sm font-semibold text-slate-700 mb-3">주목할 상관관계</h2>
              <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                {notable.slice(0, 6).map((c) => {
                  const isPos = c.r > 0
                  return (
                    <div key={`${c.assetA}-${c.assetB}`}
                      className="flex items-center justify-between rounded-lg border border-slate-100 bg-slate-50 px-4 py-3">
                      <div>
                        <span className="font-medium text-slate-700">
                          {c.assetA} <span className="text-slate-400">·</span> {c.assetB}
                        </span>
                        <p className="text-xs text-slate-400 mt-0.5">{corrLabel(c.r)}</p>
                      </div>
                      <span className={`text-lg font-bold tabular-nums ${
                        isPos ? 'text-red-500' : 'text-blue-500'
                      }`}>
                        {c.r >= 0 ? '+' : ''}{c.r.toFixed(3)}
                      </span>
                    </div>
                  )
                })}
              </div>
              <p className="mt-3 text-xs text-slate-400">
                * 상관계수는 과거 데이터 기반이며 미래 수익을 보장하지 않습니다.
              </p>
            </div>
          )}
        </>
      )}
    </div>
  )
}
