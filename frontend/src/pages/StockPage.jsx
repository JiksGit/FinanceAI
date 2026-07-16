import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  addFavorite,
  getFavorites,
  getPortfolioSummary,
  getSectorBreakdown,
  getStock,
  getStockHistory,
  getStockNews,
  removeFavorite,
  searchStock,
  updateHolding,
  updateMemo,
} from '../api/stockApi'
import { setTargetPrice, clearTargetPrice } from '../api/alertApi'
import { useAuth } from '../hooks/useAuth'
import MarketTopTable, { formatMarketCap, priceColor } from '../components/stock/MarketTopTable'
import StockChart from '../components/stock/StockChart'
import PortfolioPieChart from '../components/stock/PortfolioPieChart'
import LoadingSpinner from '../components/common/LoadingSpinner'

// ── 주요 지표 행 ──────────────────────────────────────────
function StatRow({ label, value, colored }) {
  return (
    <div className="flex justify-between py-1 border-b border-slate-100 last:border-0">
      <span className="text-xs text-slate-400">{label}</span>
      <span className={`text-xs font-medium tabular-nums ${colored || 'text-slate-700'}`}>{value}</span>
    </div>
  )
}

// ── 검색 드롭다운 ─────────────────────────────────────────
function SearchBar({ onSelect }) {
  const [keyword, setKeyword] = useState('')
  const [results, setResults] = useState([])
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  useEffect(() => {
    const handler = (e) => { if (!ref.current?.contains(e.target)) setOpen(false) }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const handleChange = async (v) => {
    setKeyword(v)
    if (v.length < 1) { setResults([]); setOpen(false); return }
    try {
      const res = await searchStock(v)
      setResults(res)
      setOpen(true)
    } catch { setResults([]) }
  }

  const handlePick = (symbol, name) => {
    setKeyword('')
    setResults([])
    setOpen(false)
    onSelect(symbol, name)
  }

  return (
    <div className="relative" ref={ref}>
      <div className="flex items-center gap-2 rounded border border-slate-300 bg-white px-3 py-2 focus-within:border-indigo-400 focus-within:ring-1 focus-within:ring-indigo-400">
        <svg className="h-4 w-4 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-4.35-4.35M17 11A6 6 0 111 11a6 6 0 0116 0z" />
        </svg>
        <input
          value={keyword}
          onChange={(e) => handleChange(e.target.value)}
          placeholder="종목명 또는 코드 검색"
          className="w-full bg-transparent text-sm outline-none placeholder-slate-400"
        />
      </div>
      {open && results.length > 0 && (
        <div className="absolute z-20 mt-1 w-full rounded border border-slate-200 bg-white shadow-lg">
          {results.slice(0, 10).map((r) => (
            <button
              key={r.symbol}
              onClick={() => handlePick(r.symbol, r.name)}
              className="flex w-full items-center justify-between px-4 py-2.5 text-left hover:bg-slate-50"
            >
              <span className="font-medium text-slate-800 text-sm">{r.name}</span>
              <span className="font-mono text-xs text-slate-400">{r.symbol}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

// ── 즐겨찾기 보유정보 입력 ────────────────────────────────
function HoldingEditor({ symbol, favorite, onSave, onClose }) {
  const [qty, setQty] = useState(favorite?.quantity ?? '')
  const [avg, setAvg] = useState(favorite?.avgPrice ?? '')
  return (
    <div className="mt-3 rounded-lg border border-slate-200 bg-slate-50 p-3">
      <p className="mb-2 text-xs font-medium text-slate-600">보유정보 입력</p>
      <div className="flex gap-2">
        <div className="flex-1">
          <label className="text-xs text-slate-400">수량</label>
          <input type="number" value={qty} onChange={(e) => setQty(e.target.value)}
            className="mt-0.5 w-full rounded border border-slate-300 px-2 py-1 text-sm" placeholder="0" />
        </div>
        <div className="flex-1">
          <label className="text-xs text-slate-400">평균단가</label>
          <input type="number" value={avg} onChange={(e) => setAvg(e.target.value)}
            className="mt-0.5 w-full rounded border border-slate-300 px-2 py-1 text-sm" placeholder="0" />
        </div>
      </div>
      <div className="mt-2 flex gap-2">
        <button onClick={() => onSave(symbol, Number(qty), Number(avg))}
          className="flex-1 rounded bg-indigo-600 py-1 text-xs text-white hover:bg-indigo-700">저장</button>
        <button onClick={onClose}
          className="flex-1 rounded border border-slate-300 py-1 text-xs text-slate-600 hover:bg-slate-100">취소</button>
      </div>
    </div>
  )
}

// ── 관심종목 상세 팝업 ────────────────────────────────────
function FavoriteDetailModal({ fav, onClose, onSetTarget, onClearTarget, onRemove, onUpdateMemo }) {
  const hasHolding = fav.quantity > 0 && fav.avgPrice != null
  const currentPrice = Number(fav.currentPrice ?? 0)
  const avgPrice = Number(fav.avgPrice ?? 0)
  const changeRate = Number(fav.changeRate ?? 0)
  const priceChange = Number(fav.priceChange ?? 0)
  const profitLoss = Number(fav.profitLoss ?? 0)
  const profitLossRate = Number(fav.profitLossRate ?? 0)
  const isUp = changeRate > 0
  const isDown = changeRate < 0
  const isProfit = profitLoss > 0
  const isLoss = profitLoss < 0

  const [showTarget, setShowTarget] = useState(false)
  const [memo, setMemo] = useState(fav.memo ?? '')
  const [memoEditing, setMemoEditing] = useState(false)
  const [memoSaving, setMemoSaving] = useState(false)

  const handleMemoSave = async () => {
    setMemoSaving(true)
    try {
      await onUpdateMemo(fav.stockSymbol, memo)
      setMemoEditing(false)
    } finally {
      setMemoSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      onClick={onClose}>
      <div className="w-80 rounded-2xl bg-white shadow-2xl p-5"
        onClick={(e) => e.stopPropagation()}>

        {/* 헤더 */}
        <div className="flex items-start justify-between mb-4">
          <div>
            <h2 className="text-base font-bold text-slate-800">{fav.stockName}</h2>
            <div className="flex items-center gap-2 mt-0.5">
              <p className="text-xs text-slate-400 font-mono">{fav.stockSymbol}</p>
              <a
                href={`https://m.stock.naver.com/domestic/stock/${fav.stockSymbol}/overview`}
                target="_blank"
                rel="noopener noreferrer"
                className="text-xs text-indigo-500 hover:text-indigo-700 hover:underline"
              >
                네이버 →
              </a>
            </div>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600 text-lg leading-none">✕</button>
        </div>

        {/* 현재가 */}
        {fav.currentPrice != null && (
          <div className="rounded-xl bg-slate-50 p-3 mb-3">
            <p className="text-xs text-slate-400 mb-1">현재가</p>
            <p className="text-2xl font-bold text-slate-800">
              ₩{currentPrice.toLocaleString('ko-KR')}
            </p>
            <div className="flex items-center gap-2 mt-1">
              <span className={`text-sm font-semibold ${isUp ? 'text-red-500' : isDown ? 'text-blue-500' : 'text-slate-400'}`}>
                {isUp ? '▲' : isDown ? '▼' : '─'}
                {' '}{Math.abs(priceChange).toLocaleString('ko-KR')}원
              </span>
              <span className={`text-xs font-medium px-1.5 py-0.5 rounded-full ${
                isUp ? 'bg-red-50 text-red-500' : isDown ? 'bg-blue-50 text-blue-500' : 'bg-slate-100 text-slate-400'
              }`}>
                {isUp ? '+' : ''}{changeRate.toFixed(2)}%
              </span>
            </div>
          </div>
        )}

        {/* 보유 정보 */}
        {hasHolding && (
          <div className="rounded-xl border border-slate-100 p-3 mb-3">
            <p className="text-xs font-semibold text-slate-500 mb-2">보유 현황</p>
            <div className="grid grid-cols-2 gap-y-2 text-xs">
              <div>
                <p className="text-slate-400">보유 수량</p>
                <p className="font-semibold text-slate-700 mt-0.5">{fav.quantity}주</p>
              </div>
              <div>
                <p className="text-slate-400">평균 구입가</p>
                <p className="font-semibold text-slate-700 mt-0.5">₩{avgPrice.toLocaleString('ko-KR')}</p>
              </div>
              <div>
                <p className="text-slate-400">평가 손익</p>
                <p className={`font-bold mt-0.5 ${isProfit ? 'text-red-500' : isLoss ? 'text-blue-500' : 'text-slate-400'}`}>
                  {isProfit ? '+' : ''}{profitLoss.toLocaleString('ko-KR')}원
                </p>
              </div>
              <div>
                <p className="text-slate-400">수익률</p>
                <p className={`font-bold mt-0.5 ${isProfit ? 'text-red-500' : isLoss ? 'text-blue-500' : 'text-slate-400'}`}>
                  {isProfit ? '+' : ''}{profitLossRate.toFixed(2)}%
                </p>
              </div>
              {fav.currentPrice != null && (
                <>
                  <div>
                    <p className="text-slate-400">평가 금액</p>
                    <p className="font-semibold text-slate-700 mt-0.5">
                      ₩{(currentPrice * fav.quantity).toLocaleString('ko-KR')}
                    </p>
                  </div>
                  <div>
                    <p className="text-slate-400">투자 원금</p>
                    <p className="font-semibold text-slate-700 mt-0.5">
                      ₩{(avgPrice * fav.quantity).toLocaleString('ko-KR')}
                    </p>
                  </div>
                </>
              )}
            </div>
          </div>
        )}

        {/* 목표가 */}
        <div className="rounded-xl border border-slate-100 p-3 mb-4">
          <div className="flex items-center justify-between mb-1.5">
            <p className="text-xs font-semibold text-slate-500">목표가 알림</p>
            <button onClick={() => setShowTarget((v) => !v)}
              className="text-xs text-indigo-500 hover:underline">
              {fav.targetPrice ? '수정' : '설정'}
            </button>
          </div>
          {fav.targetPrice && !showTarget ? (
            <p className="text-xs text-indigo-600">
              🎯 ₩{Number(fav.targetPrice).toLocaleString('ko-KR')} {fav.targetAbove ? '이상' : '이하'} 도달 시 알림
            </p>
          ) : showTarget ? (
            <TargetEditorInline
              fav={fav}
              onSave={(sym, price, above) => { onSetTarget(sym, price, above); setShowTarget(false) }}
              onClear={(sym) => { onClearTarget(sym); setShowTarget(false) }}
              onCancel={() => setShowTarget(false)}
            />
          ) : (
            <p className="text-xs text-slate-400">설정된 목표가가 없습니다</p>
          )}
        </div>

        {/* 투자 메모 */}
        <div className="rounded-xl border border-slate-100 p-3 mb-4">
          <div className="flex items-center justify-between mb-1.5">
            <p className="text-xs font-semibold text-slate-500">투자 메모</p>
            {!memoEditing && (
              <button onClick={() => setMemoEditing(true)}
                className="text-xs text-indigo-500 hover:underline">
                {memo ? '수정' : '작성'}
              </button>
            )}
          </div>
          {memoEditing ? (
            <div className="flex flex-col gap-1.5">
              <textarea
                value={memo}
                onChange={(e) => setMemo(e.target.value)}
                maxLength={500}
                rows={3}
                placeholder="투자 이유, 목표, 참고사항 등을 메모하세요"
                className="w-full rounded border border-slate-300 px-2 py-1.5 text-xs resize-none focus:outline-none focus:border-indigo-400"
              />
              <p className="text-right text-xs text-slate-300">{memo.length}/500</p>
              <div className="flex gap-1.5">
                <button onClick={handleMemoSave} disabled={memoSaving}
                  className="flex-1 rounded bg-indigo-600 py-1 text-xs text-white hover:bg-indigo-700 disabled:opacity-50">
                  {memoSaving ? '저장 중...' : '저장'}
                </button>
                <button onClick={() => { setMemo(fav.memo ?? ''); setMemoEditing(false) }}
                  className="rounded border border-slate-200 px-2 py-1 text-xs text-slate-400 hover:bg-slate-50">
                  취소
                </button>
              </div>
            </div>
          ) : memo ? (
            <p className="text-xs text-slate-600 whitespace-pre-wrap leading-relaxed">{memo}</p>
          ) : (
            <p className="text-xs text-slate-400">메모가 없습니다</p>
          )}
        </div>

        {/* 하단 버튼 */}
        <div className="flex gap-2">
          <button
            onClick={onClose}
            className="flex-1 rounded-lg border border-slate-200 py-2 text-xs text-slate-600 hover:bg-slate-50">
            닫기
          </button>
          <button
            onClick={() => { onRemove(fav.stockSymbol); onClose() }}
            className="rounded-lg border border-red-100 px-3 py-2 text-xs text-red-400 hover:bg-red-50">
            삭제
          </button>
        </div>
      </div>
    </div>
  )
}

function TargetEditorInline({ fav, onSave, onCancel, onClear }) {
  const [price, setPrice] = useState(fav.targetPrice ?? '')
  const [above, setAbove] = useState(fav.targetAbove ?? true)
  return (
    <div className="flex flex-col gap-1.5 mt-1">
      <select value={above} onChange={(e) => setAbove(e.target.value === 'true')}
        className="rounded border border-slate-300 px-2 py-1 text-xs">
        <option value="true">이상 (매도목표)</option>
        <option value="false">이하 (매수목표)</option>
      </select>
      <input type="number" min="0" value={price} onChange={(e) => setPrice(e.target.value)}
        placeholder="목표가 입력 (원)" className="rounded border border-slate-300 px-2 py-1 text-xs" />
      <div className="flex gap-1.5">
        <button onClick={() => { if (price) onSave(fav.stockSymbol, Number(price), above) }}
          className="flex-1 rounded bg-indigo-600 py-1 text-xs text-white hover:bg-indigo-700">설정</button>
        {fav.targetPrice && (
          <button onClick={() => onClear(fav.stockSymbol)}
            className="rounded border border-red-200 px-2 py-1 text-xs text-red-400 hover:bg-red-50">해제</button>
        )}
        <button onClick={onCancel}
          className="rounded border border-slate-200 px-2 py-1 text-xs text-slate-400 hover:bg-slate-50">취소</button>
      </div>
    </div>
  )
}

// ── 목표가 설정 ───────────────────────────────────────────
function TargetEditor({ fav, onSave, onCancel, onClear }) {
  const [price, setPrice] = useState(fav.targetPrice ?? '')
  const [above, setAbove] = useState(fav.targetAbove ?? true)
  const handleSubmit = (e) => {
    e.preventDefault()
    if (!price) return
    onSave(fav.stockSymbol, Number(price), above)
  }
  return (
    <form onSubmit={handleSubmit} onClick={(e) => e.stopPropagation()}
      className="mt-2 flex items-center gap-1.5 flex-wrap">
      <select value={above} onChange={(e) => setAbove(e.target.value === 'true')}
        className="rounded border border-slate-300 px-1.5 py-1 text-xs">
        <option value="true">이상 (매도목표)</option>
        <option value="false">이하 (매수목표)</option>
      </select>
      <input type="number" min="0" value={price} onChange={(e) => setPrice(e.target.value)}
        placeholder="목표가 (원)" className="w-28 rounded border border-slate-300 px-1.5 py-1 text-xs" />
      <button type="submit" className="text-xs text-indigo-600 hover:underline">설정</button>
      {fav.targetPrice && (
        <button type="button" onClick={() => onClear(fav.stockSymbol)}
          className="text-xs text-red-400 hover:underline">해제</button>
      )}
      <button type="button" onClick={onCancel} className="text-xs text-slate-400 hover:underline">취소</button>
    </form>
  )
}

// ── 우측 종목 상세 패널 ───────────────────────────────────
function StockDetailPanel({ symbol, favorites, isAuthenticated, onFavoriteToggle, onUpdateHolding }) {
  const [stock, setStock] = useState(null)
  const [history, setHistory] = useState([])
  const [news, setNews] = useState([])
  const [loading, setLoading] = useState(false)
  const [showHolding, setShowHolding] = useState(false)

  useEffect(() => {
    if (!symbol) return
    setLoading(true)
    setStock(null)
    setHistory([])
    setNews([])
    Promise.all([
      getStock(symbol),
      getStockHistory(symbol, 30).catch(() => ({ history: [] })),
      getStockNews(symbol).catch(() => ({ news: [] })),
    ]).then(([s, h, n]) => {
      setStock(s)
      setHistory(h.history ?? [])
      setNews(n.news ?? [])
    }).catch(() => {}).finally(() => setLoading(false))
  }, [symbol])

  const isFav = favorites.some((f) => f.stockSymbol === symbol)
  const favData = favorites.find((f) => f.stockSymbol === symbol)
  const change = stock ? Number(stock.change) : 0
  const changeRate = stock ? Number(stock.changeRate) : 0

  if (!symbol) {
    return (
      <div className="flex h-64 flex-col items-center justify-center text-slate-400">
        <svg className="mb-3 h-10 w-10" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
            d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
        </svg>
        <p className="text-sm">종목을 선택하세요</p>
      </div>
    )
  }

  if (loading) return <div className="flex justify-center py-12"><LoadingSpinner /></div>
  if (!stock) return null

  return (
    <div className="space-y-4">
      {/* 헤더 */}
      <div className="flex items-start justify-between">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-bold text-slate-900">{stock.name}</h2>
            <span className={`text-xs px-1.5 py-0.5 rounded font-medium ${
              stock.market === 'KOSPI' ? 'bg-blue-50 text-blue-600' : 'bg-green-50 text-green-600'
            }`}>{stock.market}</span>
          </div>
          <p className="mt-0.5 font-mono text-xs text-slate-400">{stock.symbol}</p>
        </div>
        {isAuthenticated && (
          <button
            onClick={() => onFavoriteToggle(symbol)}
            className={`flex items-center gap-1 rounded-md px-2.5 py-1.5 text-xs font-medium ${
              isFav
                ? 'bg-yellow-50 text-yellow-600 hover:bg-yellow-100'
                : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
            }`}
          >
            <span>{isFav ? '★' : '☆'}</span>
            {isFav ? '즐겨찾기' : '추가'}
          </button>
        )}
      </div>

      {/* 현재가 */}
      <div className="rounded-xl bg-slate-50 p-4">
        <div className={`text-3xl font-bold tabular-nums ${priceColor(change)}`}>
          ₩{Number(stock.price).toLocaleString('ko-KR')}
        </div>
        <div className={`mt-1 flex items-center gap-2 text-sm ${priceColor(change)}`}>
          <span className="font-medium">
            {change > 0 ? '▲' : change < 0 ? '▼' : '—'}{' '}
            {Math.abs(change).toLocaleString('ko-KR')}
          </span>
          <span className="font-bold">
            ({change > 0 ? '+' : ''}{changeRate.toFixed(2)}%)
          </span>
        </div>
      </div>

      {/* 주요 지표 */}
      <div className="rounded-xl border border-slate-200 bg-white p-3">
        <StatRow label="시가" value={Number(stock.high || stock.price).toLocaleString('ko-KR') + '원'} />
        <StatRow label="고가" value={Number(stock.high || stock.price).toLocaleString('ko-KR') + '원'}
          colored={stock.high > stock.price ? 'text-red-500' : ''} />
        <StatRow label="저가" value={Number(stock.low || stock.price).toLocaleString('ko-KR') + '원'}
          colored={stock.low < stock.price ? 'text-blue-500' : ''} />
        <StatRow label="거래량" value={Number(stock.volume).toLocaleString('ko-KR') + '주'} />
        {stock.marketCap > 0 && (
          <StatRow label="시가총액" value={formatMarketCap(stock.marketCap)} />
        )}
      </div>

      {/* 보유정보 */}
      {isAuthenticated && isFav && (
        <div className="rounded-xl border border-slate-200 bg-white p-3">
          <div className="flex items-center justify-between">
            <p className="text-xs font-medium text-slate-600">보유정보</p>
            <button onClick={() => setShowHolding(!showHolding)}
              className="text-xs text-indigo-500 hover:underline">
              {showHolding ? '닫기' : '수정'}
            </button>
          </div>
          {favData?.quantity ? (
            <div className="mt-2 grid grid-cols-2 gap-2 text-xs">
              <div>
                <span className="text-slate-400">수량</span>
                <p className="font-semibold text-slate-700">{favData.quantity}주</p>
              </div>
              <div>
                <span className="text-slate-400">평균단가</span>
                <p className="font-semibold text-slate-700">
                  ₩{Number(favData.avgPrice).toLocaleString('ko-KR')}
                </p>
              </div>
              {favData.profitLoss !== null && (
                <div className="col-span-2">
                  <span className="text-slate-400">평가손익</span>
                  <p className={`font-bold ${favData.profitLoss >= 0 ? 'text-red-500' : 'text-blue-500'}`}>
                    {favData.profitLoss >= 0 ? '+' : ''}
                    ₩{Number(favData.profitLoss).toLocaleString('ko-KR')}
                    {' '}({favData.profitLossRate >= 0 ? '+' : ''}
                    {Number(favData.profitLossRate).toFixed(2)}%)
                  </p>
                </div>
              )}
            </div>
          ) : (
            !showHolding && (
              <p className="mt-1 text-xs text-slate-400">보유수량을 입력하면 수익률을 계산합니다.</p>
            )
          )}
          {showHolding && (
            <HoldingEditor
              symbol={symbol}
              favorite={favData}
              onSave={(sym, qty, avg) => { onUpdateHolding(sym, qty, avg); setShowHolding(false) }}
              onClose={() => setShowHolding(false)}
            />
          )}
        </div>
      )}

      {/* 차트 */}
      {history.length > 1 && (
        <div className="rounded-xl border border-slate-200 bg-white p-3">
          <p className="mb-2 text-xs font-medium text-slate-500">30일 시세 추이</p>
          <StockChart history={history} />
        </div>
      )}

      {/* 뉴스 */}
      {news.length > 0 && (
        <div className="rounded-xl border border-slate-200 bg-white p-3">
          <p className="mb-2 text-xs font-medium text-slate-500">관련 뉴스</p>
          <div className="space-y-2">
            {news.map((item, i) => (
              <a key={i} href={item.url} target="_blank" rel="noopener noreferrer"
                className="block rounded-lg border border-slate-100 p-2.5 hover:bg-slate-50">
                <div className="flex items-start justify-between gap-2">
                  <p className="text-xs font-medium text-slate-800 leading-snug">{item.title}</p>
                  <span className={`shrink-0 rounded px-1.5 py-0.5 text-xs font-medium ${
                    item.sentiment === 'Bullish'
                      ? 'bg-red-50 text-red-500'
                      : item.sentiment === 'Bearish'
                      ? 'bg-blue-50 text-blue-500'
                      : 'bg-slate-100 text-slate-500'
                  }`}>
                    {item.sentiment === 'Bullish' ? '긍정' : item.sentiment === 'Bearish' ? '부정' : '중립'}
                  </span>
                </div>
                <p className="mt-1 text-xs text-slate-400">{item.source} · {item.publishedAt}</p>
              </a>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

// ── 메인 페이지 ───────────────────────────────────────────
export default function StockPage() {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const [selectedCode, setSelectedCode] = useState(null)
  const [favorites, setFavorites] = useState([])
  const [portfolioSummary, setPortfolioSummary] = useState(null)
  const [activeTab, setActiveTab] = useState('market') // 'market' | 'portfolio'
  const [targetSymbol, setTargetSymbol] = useState(null)
  const [detailFav, setDetailFav] = useState(null)
  const [sectorBreakdown, setSectorBreakdown] = useState(null)
  const [recentStocks, setRecentStocks] = useState(() => {
    try { return JSON.parse(localStorage.getItem('recentStocks') || '[]') } catch { return [] }
  })

  const loadFavorites = () => {
    if (!isAuthenticated) return
    getFavorites().then(setFavorites).catch(() => {})
    getPortfolioSummary().then(setPortfolioSummary).catch(() => {})
    getSectorBreakdown().then(setSectorBreakdown).catch(() => {})
  }

  useEffect(loadFavorites, [isAuthenticated])

  const addToRecent = (symbol, name) => {
    setRecentStocks((prev) => {
      const filtered = prev.filter((s) => s.symbol !== symbol)
      const next = [{ symbol, name, viewedAt: Date.now() }, ...filtered].slice(0, 10)
      localStorage.setItem('recentStocks', JSON.stringify(next))
      return next
    })
  }

  const removeRecent = (symbol) => {
    setRecentStocks((prev) => {
      const next = prev.filter((s) => s.symbol !== symbol)
      localStorage.setItem('recentStocks', JSON.stringify(next))
      return next
    })
  }

  const handleSelectStock = (symbol, name) => {
    setSelectedCode(symbol)
    if (symbol && name) addToRecent(symbol, name)
  }

  const handleFavoriteToggle = async (symbol) => {
    const isFav = favorites.some((f) => f.stockSymbol === symbol)
    try {
      if (isFav) await removeFavorite(symbol)
      else await addFavorite(symbol, null)
      loadFavorites()
    } catch {}
  }

  const handleUpdateHolding = async (symbol, qty, avg) => {
    try { await updateHolding(symbol, qty, avg); loadFavorites() } catch {}
  }

  const handleSetTarget = async (symbol, price, above) => {
    try { await setTargetPrice(symbol, price, above); loadFavorites(); setTargetSymbol(null) } catch {}
  }

  const handleClearTarget = async (symbol) => {
    try { await clearTargetPrice(symbol); loadFavorites(); setTargetSymbol(null) } catch {}
  }

  return (
    <>
    <div className="flex h-[calc(100vh-120px)] min-h-[600px] gap-4">
      {/* ── 왼쪽 패널 ── */}
      <div className="flex w-80 shrink-0 flex-col gap-3 lg:w-96">
        {/* 검색 */}
        <SearchBar onSelect={(symbol, name) => handleSelectStock(symbol, name)} />

        {/* 탭 */}
        <div className="flex rounded-lg border border-slate-200 bg-slate-50 p-0.5">
          <button
            onClick={() => setActiveTab('market')}
            className={`flex-1 rounded-md py-1.5 text-xs font-medium transition-colors ${
              activeTab === 'market' ? 'bg-white shadow-sm text-slate-800' : 'text-slate-500 hover:text-slate-700'
            }`}
          >
            시가총액 TOP
          </button>
          {isAuthenticated && (
            <button
              onClick={() => setActiveTab('portfolio')}
              className={`flex-1 rounded-md py-1.5 text-xs font-medium transition-colors ${
                activeTab === 'portfolio' ? 'bg-white shadow-sm text-slate-800' : 'text-slate-500 hover:text-slate-700'
              }`}
            >
              관심종목
            </button>
          )}
        </div>

        {/* 리스트 패널 */}
        <div className="flex-1 overflow-hidden rounded-xl border border-slate-200 bg-white p-3">
          {activeTab === 'market' ? (
            <MarketTopTable
              limit={100}
              onSelect={(code) => navigate(`/stocks/${code}`)}
              selectedCode={selectedCode}
            />
          ) : (
            <div className="h-full overflow-y-auto">
              {favorites.length === 0 ? (
                <div className="flex h-40 flex-col items-center justify-center text-slate-400">
                  <p className="text-sm">관심종목이 없습니다</p>
                </div>
              ) : (
                <div className="space-y-0">
                  {favorites.map((fav) => {
                    const isSelected = selectedCode === fav.stockSymbol
                    const hasTarget = fav.targetPrice != null
                    const changeRate = Number(fav.changeRate ?? 0)
                    return (
                      <div
                        key={fav.stockSymbol}
                        className={`rounded-lg p-2.5 transition-colors ${
                          isSelected ? 'bg-indigo-50' : 'hover:bg-slate-50'
                        }`}
                      >
                        <div className="flex items-center justify-between">
                          {/* 종목명 클릭 → 상세 팝업 */}
                          <button
                            className="flex-1 text-left"
                            onClick={() => setDetailFav(fav)}
                          >
                            <p className="text-sm font-medium text-slate-800 hover:text-indigo-600">
                              {fav.stockName}
                              {hasTarget && <span className="ml-1 text-xs text-indigo-400">🎯</span>}
                            </p>
                            <p className="text-xs text-slate-400 font-mono">{fav.stockSymbol}</p>
                          </button>

                          <div className="flex items-center gap-2">
                            {fav.currentPrice && (
                              <div className="text-right">
                                <p className="text-sm font-semibold text-slate-800">
                                  ₩{Number(fav.currentPrice).toLocaleString('ko-KR')}
                                </p>
                                {fav.changeRate != null && (
                                  <p className={`text-xs font-medium ${
                                    changeRate > 0 ? 'text-red-500' : changeRate < 0 ? 'text-blue-500' : 'text-slate-400'
                                  }`}>
                                    {changeRate > 0 ? '+' : ''}{changeRate.toFixed(2)}%
                                  </p>
                                )}
                              </div>
                            )}
                            {/* 차트 보기 버튼 */}
                            <button
                              onClick={() => handleSelectStock(fav.stockSymbol, fav.stockName)}
                              className="text-xs text-slate-300 hover:text-indigo-500 px-1"
                              title="차트 보기"
                            >
                              📈
                            </button>
                          </div>
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>
          )}
        </div>

        {/* 최근 본 종목 */}
        {recentStocks.length > 0 && (
          <div className="mt-3 rounded-xl border border-slate-200 bg-white p-3">
            <div className="flex items-center justify-between mb-2">
              <p className="text-xs font-semibold text-slate-500">최근 본 종목</p>
              <button
                onClick={() => { localStorage.removeItem('recentStocks'); setRecentStocks([]) }}
                className="text-xs text-slate-300 hover:text-slate-500"
              >
                전체 삭제
              </button>
            </div>
            <div className="space-y-1">
              {recentStocks.map((s) => (
                <div key={s.symbol} className="flex items-center justify-between rounded-lg px-2 py-1.5 hover:bg-slate-50">
                  <button
                    className="flex-1 text-left"
                    onClick={() => handleSelectStock(s.symbol, s.name)}
                  >
                    <span className="text-xs font-medium text-slate-700">{s.name}</span>
                    <span className="ml-2 font-mono text-xs text-slate-400">{s.symbol}</span>
                  </button>
                  <button
                    onClick={() => removeRecent(s.symbol)}
                    className="ml-2 text-slate-200 hover:text-slate-400 text-xs"
                  >
                    ✕
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* ── 오른쪽 상세 패널 ── */}
      <div className="flex-1 overflow-y-auto rounded-xl border border-slate-200 bg-white p-4">
        {/* 관심종목 탭일 때 포트폴리오 요약 항상 상단 표시 */}
        {activeTab === 'portfolio' && isAuthenticated && portfolioSummary?.totalCurrentValue > 0 && (
          <div className="mb-4">
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-sm font-semibold text-slate-700">포트폴리오 비중</h3>
              {selectedCode && (
                <button onClick={() => setSelectedCode(null)}
                  className="text-xs text-slate-400 hover:text-slate-600">← 차트 닫기</button>
              )}
            </div>
            {!selectedCode && <PortfolioPieChart summary={portfolioSummary} />}
            {!selectedCode && (
              <p className="mt-3 text-center text-xs text-slate-400">
                종목 옆 📈 버튼을 클릭하면 차트를 볼 수 있습니다
              </p>
            )}
          </div>
        )}

        {/* 섹터 분석 */}
        {activeTab === 'portfolio' && isAuthenticated && !selectedCode && sectorBreakdown?.sectors?.length > 0 && (
          <div className="mb-4">
            <h3 className="text-sm font-semibold text-slate-700 mb-2">섹터 분석</h3>
            <div className="rounded-xl border border-slate-100 p-3 space-y-2">
              {sectorBreakdown.sectors.map((item) => (
                <div key={item.sector}>
                  <div className="flex justify-between text-xs mb-1">
                    <span className="text-slate-600 font-medium">{item.sector}</span>
                    <span className="text-slate-400">{item.stockCount}종목 · {item.percentage}%</span>
                  </div>
                  <div className="h-1.5 w-full rounded-full bg-slate-100">
                    <div
                      className="h-1.5 rounded-full bg-indigo-400 transition-all"
                      style={{ width: `${item.percentage}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* 종목 선택 시 차트 패널 */}
        {(activeTab === 'market' || selectedCode) && (
          <StockDetailPanel
            symbol={selectedCode}
            favorites={favorites}
            isAuthenticated={isAuthenticated}
            onFavoriteToggle={handleFavoriteToggle}
            onUpdateHolding={handleUpdateHolding}
          />
        )}

        {/* 관심종목 탭 + 포트폴리오 없음 + 종목 미선택 */}
        {activeTab === 'portfolio' && !selectedCode && !(portfolioSummary?.totalCurrentValue > 0) && (
          <div className="flex h-full flex-col items-center justify-center text-slate-400">
            <p className="text-sm">종목을 선택하면 차트가 표시됩니다</p>
            <p className="text-xs mt-1">종목 옆 📈 버튼을 클릭해보세요</p>
          </div>
        )}
      </div>
    </div>

    {/* ── 관심종목 상세 팝업 ── */}
    {detailFav && (
      <FavoriteDetailModal
        fav={detailFav}
        onClose={() => setDetailFav(null)}
        onSetTarget={async (sym, price, above) => {
          await handleSetTarget(sym, price, above)
          setDetailFav((prev) => prev ? { ...prev, targetPrice: price, targetAbove: above } : null)
        }}
        onClearTarget={async (sym) => {
          await handleClearTarget(sym)
          setDetailFav((prev) => prev ? { ...prev, targetPrice: null, targetAbove: null } : null)
        }}
        onRemove={(sym) => { removeFavorite(sym).then(loadFavorites).catch(() => {}); setDetailFav(null) }}
        onUpdateMemo={async (sym, memo) => {
          await updateMemo(sym, memo)
          setDetailFav((prev) => prev ? { ...prev, memo } : null)
          loadFavorites()
        }}
      />
    )}
    </>
  )
}
