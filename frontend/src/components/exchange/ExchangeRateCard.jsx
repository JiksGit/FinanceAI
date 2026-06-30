export default function ExchangeRateCard({ rate, onSelect, selected }) {
  const isUp = rate.change > 0
  const isDown = rate.change < 0

  return (
    <button
      onClick={() => onSelect?.(rate.currency)}
      className={`flex w-full flex-col gap-1 rounded-lg border p-4 text-left transition ${
        selected ? 'border-indigo-500 ring-1 ring-indigo-500' : 'border-slate-200 hover:border-slate-300'
      }`}
    >
      <span className="text-sm font-medium text-slate-500">{rate.currency}</span>
      <span className="text-2xl font-semibold text-slate-900">
        {rate.rate.toLocaleString()}원
      </span>
      <span
        className={`text-sm font-medium ${
          isUp ? 'text-red-600' : isDown ? 'text-blue-600' : 'text-slate-400'
        }`}
      >
        {isUp ? '▲' : isDown ? '▼' : '-'} {Math.abs(rate.change).toFixed(2)} (
        {Math.abs(rate.changeRate).toFixed(2)}%)
      </span>
    </button>
  )
}
