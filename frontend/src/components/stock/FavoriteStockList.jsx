import { useState } from 'react'

function HoldingEditor({ favorite, onSave, onCancel }) {
  const [quantity, setQuantity] = useState(favorite.quantity ?? '')
  const [avgPrice, setAvgPrice] = useState(favorite.avgPrice ?? '')

  const handleSubmit = (e) => {
    e.preventDefault()
    onSave(favorite.stockSymbol, Number(quantity) || 0, Number(avgPrice) || 0)
  }

  return (
    <form onSubmit={handleSubmit} className="flex items-center gap-1.5">
      <input
        type="number"
        min="0"
        value={quantity}
        onChange={(e) => setQuantity(e.target.value)}
        placeholder="수량"
        className="w-16 rounded border border-slate-300 px-1.5 py-1 text-xs"
      />
      <input
        type="number"
        min="0"
        step="0.01"
        value={avgPrice}
        onChange={(e) => setAvgPrice(e.target.value)}
        placeholder="평단가"
        className="w-20 rounded border border-slate-300 px-1.5 py-1 text-xs"
      />
      <button type="submit" className="text-xs text-indigo-600 hover:underline">
        저장
      </button>
      <button type="button" onClick={onCancel} className="text-xs text-slate-400 hover:underline">
        취소
      </button>
    </form>
  )
}

export default function FavoriteStockList({ favorites, onSelect, onRemove, onUpdateHolding }) {
  const [editingSymbol, setEditingSymbol] = useState(null)

  if (!favorites || favorites.length === 0) {
    return <p className="text-sm text-slate-400">즐겨찾기한 종목이 없습니다.</p>
  }

  const handleSave = (symbol, quantity, avgPrice) => {
    onUpdateHolding(symbol, quantity, avgPrice)
    setEditingSymbol(null)
  }

  return (
    <ul className="divide-y divide-slate-100">
      {favorites.map((fav) => {
        const hasHolding = fav.quantity && fav.quantity > 0
        const isProfit = fav.profitLoss > 0
        const isLoss = fav.profitLoss < 0

        return (
          <li key={fav.stockSymbol} className="py-2.5">
            <div className="flex items-center justify-between">
              <button
                onClick={() => onSelect(fav.stockSymbol)}
                className="text-sm font-medium text-slate-700 hover:text-indigo-600"
              >
                {fav.stockSymbol} <span className="text-slate-400">{fav.stockName}</span>
              </button>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setEditingSymbol(editingSymbol === fav.stockSymbol ? null : fav.stockSymbol)}
                  className="text-xs text-slate-400 hover:text-indigo-600"
                >
                  보유정보
                </button>
                <button
                  onClick={() => onRemove(fav.stockSymbol)}
                  className="text-xs text-slate-400 hover:text-red-500"
                >
                  삭제
                </button>
              </div>
            </div>

            {editingSymbol === fav.stockSymbol && (
              <div className="mt-2">
                <HoldingEditor
                  favorite={fav}
                  onSave={handleSave}
                  onCancel={() => setEditingSymbol(null)}
                />
              </div>
            )}

            {hasHolding && (
              <div className="mt-1 flex items-center gap-3 text-xs text-slate-500">
                <span>
                  {fav.quantity}주 · 평단 ₩{Number(fav.avgPrice).toLocaleString('ko-KR')}
                </span>
                {fav.currentPrice != null && (
                  <span className={isProfit ? 'text-red-500' : isLoss ? 'text-blue-500' : 'text-slate-400'}>
                    {isProfit ? '+' : ''}
                    {Number(fav.profitLoss).toLocaleString()}원 ({fav.profitLossRate?.toFixed(2)}%)
                  </span>
                )}
              </div>
            )}
          </li>
        )
      })}
    </ul>
  )
}
