export default function AiContextToggle({ includeExchangeRate, includeStock, onChange }) {
  return (
    <div className="flex gap-4 text-sm text-slate-600">
      <label className="flex items-center gap-1.5">
        <input
          type="checkbox"
          checked={includeExchangeRate}
          onChange={(e) => onChange({ includeExchangeRate: e.target.checked, includeStock })}
        />
        환율 데이터 포함
      </label>
      <label className="flex items-center gap-1.5">
        <input
          type="checkbox"
          checked={includeStock}
          onChange={(e) => onChange({ includeExchangeRate, includeStock: e.target.checked })}
        />
        주식 데이터 포함
      </label>
    </div>
  )
}
