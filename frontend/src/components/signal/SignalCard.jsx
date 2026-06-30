export default function SignalCard({ signal }) {
  const isBuy = signal.signalType === 'BUY'

  return (
    <div className="rounded-lg border border-slate-200 p-4">
      <div className="flex items-center justify-between">
        <span className="font-semibold text-slate-900">{signal.stockSymbol}</span>
        <span
          className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
            isBuy ? 'bg-red-50 text-red-600' : 'bg-blue-50 text-blue-600'
          }`}
        >
          {isBuy ? '매수 시그널' : '매도 시그널'}
        </span>
      </div>
      <p className="mt-1 text-xs text-slate-400">
        {signal.indicator} · 기준일 {signal.signalDate}
      </p>
      <p className="mt-2 text-sm text-slate-600">{signal.aiExplanation}</p>
    </div>
  )
}
