import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend } from 'recharts'

const COLORS = ['#6366f1', '#f59e0b', '#10b981', '#ef4444', '#3b82f6', '#8b5cf6']

function CustomTooltip({ active, payload }) {
  if (!active || !payload?.length) return null
  const d = payload[0].payload
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs shadow">
      <p className="font-semibold text-slate-800">{d.symbol}</p>
      <p className="text-slate-500">${Number(d.currentValue).toFixed(2)}</p>
      <p className="text-indigo-500">{d.weightPercent.toFixed(1)}%</p>
    </div>
  )
}

export default function PortfolioPieChart({ summary }) {
  if (!summary || summary.holdingCount === 0) {
    return (
      <div className="flex h-48 items-center justify-center">
        <p className="text-sm text-slate-400">보유정보를 입력하면 비중이 표시됩니다.</p>
      </div>
    )
  }

  const data = summary.weights.map((w) => ({
    name: w.symbol,
    symbol: w.symbol,
    stockName: w.name,
    currentValue: w.currentValue,
    weightPercent: w.weightPercent,
    value: w.weightPercent,
  }))

  const isProfit = summary.totalProfitLoss > 0
  const isLoss = summary.totalProfitLoss < 0

  return (
    <div>
      {/* 총합 요약 */}
      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <div className="rounded-lg bg-slate-50 px-3 py-2.5">
          <p className="text-xs text-slate-400">총 투자금</p>
          <p className="mt-0.5 text-sm font-semibold text-slate-800">
            ${Number(summary.totalInvested).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </p>
        </div>
        <div className="rounded-lg bg-slate-50 px-3 py-2.5">
          <p className="text-xs text-slate-400">총 평가금액</p>
          <p className="mt-0.5 text-sm font-semibold text-slate-800">
            ${Number(summary.totalCurrentValue).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </p>
        </div>
        <div className="rounded-lg bg-slate-50 px-3 py-2.5">
          <p className="text-xs text-slate-400">총 손익</p>
          <p className={`mt-0.5 text-sm font-semibold ${isProfit ? 'text-red-500' : isLoss ? 'text-blue-500' : 'text-slate-800'}`}>
            {isProfit ? '+' : ''}${Number(summary.totalProfitLoss).toFixed(2)}
          </p>
        </div>
        <div className="rounded-lg bg-slate-50 px-3 py-2.5">
          <p className="text-xs text-slate-400">수익률</p>
          <p className={`mt-0.5 text-sm font-semibold ${isProfit ? 'text-red-500' : isLoss ? 'text-blue-500' : 'text-slate-800'}`}>
            {isProfit ? '+' : ''}{Number(summary.totalProfitLossRate).toFixed(2)}%
          </p>
        </div>
      </div>

      {/* 파이차트 */}
      <ResponsiveContainer width="100%" height={260}>
        <PieChart>
          <Pie
            data={data}
            cx="50%"
            cy="50%"
            innerRadius={60}
            outerRadius={100}
            paddingAngle={2}
            dataKey="value"
          >
            {data.map((_, index) => (
              <Cell key={index} fill={COLORS[index % COLORS.length]} />
            ))}
          </Pie>
          <Tooltip content={<CustomTooltip />} />
          <Legend
            formatter={(value, entry) => (
              <span className="text-xs text-slate-600">
                {entry.payload.symbol} ({entry.payload.weightPercent.toFixed(1)}%)
              </span>
            )}
          />
        </PieChart>
      </ResponsiveContainer>
    </div>
  )
}
