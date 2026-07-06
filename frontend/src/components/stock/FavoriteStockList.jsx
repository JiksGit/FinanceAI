import { useState } from 'react'
import { setTargetPrice, clearTargetPrice } from '../../api/alertApi'

function HoldingEditor({ favorite, onSave, onCancel }) {
  const [quantity, setQuantity] = useState(favorite.quantity ?? '')
  const [avgPrice, setAvgPrice] = useState(favorite.avgPrice ?? '')

  const handleSubmit = (e) => {
    e.preventDefault()
    onSave(favorite.stockSymbol, Number(quantity) || 0, Number(avgPrice) || 0)
  }

  return (
    <form onSubmit={handleSubmit} className="flex items-center gap-1.5 flex-wrap">
      <input type="number" min="0" value={quantity}
        onChange={(e) => setQuantity(e.target.value)}
        placeholder="수량" className="w-16 rounded border border-slate-300 px-1.5 py-1 text-xs" />
      <input type="number" min="0" step="0.01" value={avgPrice}
        onChange={(e) => setAvgPrice(e.target.value)}
        placeholder="평단가" className="w-20 rounded border border-slate-300 px-1.5 py-1 text-xs" />
      <button type="submit" className="text-xs text-indigo-600 hover:underline">저장</button>
      <button type="button" onClick={onCancel} className="text-xs text-slate-400 hover:underline">취소</button>
    </form>
  )
}

function TargetEditor({ favorite, onSave, onCancel, onClear }) {
  const [price, setPrice] = useState(favorite.targetPrice ?? '')
  const [above, setAbove] = useState(favorite.targetAbove ?? true)

  const handleSubmit = (e) => {
    e.preventDefault()
    if (!price) return
    onSave(favorite.stockSymbol, Number(price), above)
  }

  return (
    <form onSubmit={handleSubmit} className="mt-1.5 flex items-center gap-1.5 flex-wrap">
      <select value={above} onChange={(e) => setAbove(e.target.value === 'true')}
        className="rounded border border-slate-300 px-1.5 py-1 text-xs">
        <option value="true">이상 (매도목표)</option>
        <option value="false">이하 (매수목표)</option>
      </select>
      <input type="number" min="0" value={price}
        onChange={(e) => setPrice(e.target.value)}
        placeholder="목표가 (원)" className="w-28 rounded border border-slate-300 px-1.5 py-1 text-xs" />
      <button type="submit" className="text-xs text-indigo-600 hover:underline">설정</button>
      {favorite.targetPrice && (
        <button type="button" onClick={() => onClear(favorite.stockSymbol)}
          className="text-xs text-red-400 hover:underline">해제</button>
      )}
      <button type="button" onClick={onCancel} className="text-xs text-slate-400 hover:underline">취소</button>
    </form>
  )
}

export default function FavoriteStockList({ favorites, onSelect, onRemove, onUpdateHolding, onRefresh }) {
  const [editingSymbol, setEditingSymbol] = useState(null)
  const [targetSymbol, setTargetSymbol] = useState(null)

  if (!favorites || favorites.length === 0) {
    return <p className="text-sm text-slate-400">즐겨찾기한 종목이 없습니다.</p>
  }

  const handleSave = (symbol, quantity, avgPrice) => {
    onUpdateHolding(symbol, quantity, avgPrice)
    setEditingSymbol(null)
  }

  const handleSetTarget = async (symbol, price, above) => {
    await setTargetPrice(symbol, price, above)
    setTargetSymbol(null)
    onRefresh?.()
  }

  const handleClearTarget = async (symbol) => {
    await clearTargetPrice(symbol)
    setTargetSymbol(null)
    onRefresh?.()
  }

  return (
    <ul className="divide-y divide-slate-100">
      {favorites.map((fav) => {
        const hasHolding = fav.quantity && fav.quantity > 0
        const isProfit = fav.profitLoss > 0
        const isLoss = fav.profitLoss < 0
        const hasTarget = fav.targetPrice != null

        return (
          <li key={fav.stockSymbol} className="py-2.5">
            <div className="flex items-center justify-between">
              <button onClick={() => onSelect(fav.stockSymbol)}
                className="text-sm font-medium text-slate-700 hover:text-indigo-600">
                {fav.stockSymbol} <span className="text-slate-400">{fav.stockName}</span>
              </button>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => { setTargetSymbol(targetSymbol === fav.stockSymbol ? null : fav.stockSymbol); setEditingSymbol(null) }}
                  className={`text-xs hover:text-indigo-600 ${hasTarget ? 'text-indigo-500 font-medium' : 'text-slate-400'}`}
                >
                  {hasTarget ? '🎯 목표가' : '목표가'}
                </button>
                <button
                  onClick={() => { setEditingSymbol(editingSymbol === fav.stockSymbol ? null : fav.stockSymbol); setTargetSymbol(null) }}
                  className="text-xs text-slate-400 hover:text-indigo-600"
                >
                  보유정보
                </button>
                <button onClick={() => onRemove(fav.stockSymbol)}
                  className="text-xs text-slate-400 hover:text-red-500">
                  삭제
                </button>
              </div>
            </div>

            {/* 목표가 표시 */}
            {hasTarget && targetSymbol !== fav.stockSymbol && (
              <p className="mt-0.5 text-xs text-indigo-500">
                🎯 {fav.targetAbove ? '이상' : '이하'} ₩{Number(fav.targetPrice).toLocaleString('ko-KR')} 도달 시 알림
              </p>
            )}

            {/* 목표가 편집 */}
            {targetSymbol === fav.stockSymbol && (
              <TargetEditor
                favorite={fav}
                onSave={handleSetTarget}
                onCancel={() => setTargetSymbol(null)}
                onClear={handleClearTarget}
              />
            )}

            {/* 보유정보 편집 */}
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
                <span>{fav.quantity}주 · 평단 ₩{Number(fav.avgPrice).toLocaleString('ko-KR')}</span>
                {fav.currentPrice != null && (
                  <span className={isProfit ? 'text-red-500' : isLoss ? 'text-blue-500' : 'text-slate-400'}>
                    {isProfit ? '+' : ''}{Number(fav.profitLoss).toLocaleString()}원 ({fav.profitLossRate?.toFixed(2)}%)
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
