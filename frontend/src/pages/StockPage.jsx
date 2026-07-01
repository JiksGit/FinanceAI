import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  addFavorite,
  getFavorites,
  getPortfolioSummary,
  getStock,
  getStockHistory,
  getStockNews,
  removeFavorite,
  searchStock,
  updateHolding,
} from '../api/stockApi'
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

  const handlePick = (symbol) => {
    setKeyword('')
    setResults([])
    setOpen(false)
    onSelect(symbol)
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
              onClick={() => handlePick(r.symbol)}
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

  const loadFavorites = () => {
    if (!isAuthenticated) return
    getFavorites().then(setFavorites).catch(() => {})
    getPortfolioSummary().then(setPortfolioSummary).catch(() => {})
  }

  useEffect(loadFavorites, [isAuthenticated])

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

  return (
    <div className="flex h-[calc(100vh-120px)] min-h-[600px] gap-4">
      {/* ── 왼쪽 패널 ── */}
      <div className="flex w-80 shrink-0 flex-col gap-3 lg:w-96">
        {/* 검색 */}
        <SearchBar onSelect={setSelectedCode} />

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
                    const change = fav.profitLoss
                    const isSelected = selectedCode === fav.stockSymbol
                    return (
                      <div
                        key={fav.stockSymbol}
                        onClick={() => setSelectedCode(fav.stockSymbol)}
                        className={`cursor-pointer rounded-lg p-2.5 transition-colors ${
                          isSelected ? 'bg-indigo-50' : 'hover:bg-slate-50'
                        }`}
                      >
                        <div className="flex items-center justify-between">
                          <div>
                            <p className="text-sm font-medium text-slate-800">{fav.stockName}</p>
                            <p className="text-xs text-slate-400 font-mono">{fav.stockSymbol}</p>
                          </div>
                          {fav.currentPrice && (
                            <div className="text-right">
                              <p className="text-sm font-semibold text-slate-800">
                                ₩{Number(fav.currentPrice).toLocaleString('ko-KR')}
                              </p>
                              {fav.profitLossRate != null && (
                                <p className={`text-xs font-medium ${
                                  fav.profitLossRate >= 0 ? 'text-red-500' : 'text-blue-500'
                                }`}>
                                  {fav.profitLossRate >= 0 ? '+' : ''}
                                  {Number(fav.profitLossRate).toFixed(2)}%
                                </p>
                              )}
                            </div>
                          )}
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* ── 오른쪽 상세 패널 ── */}
      <div className="flex-1 overflow-y-auto rounded-xl border border-slate-200 bg-white p-4">
        {activeTab === 'portfolio' && isAuthenticated && portfolioSummary?.totalCurrentValue > 0 && !selectedCode ? (
          <div>
            <h3 className="mb-4 text-sm font-semibold text-slate-700">포트폴리오 비중</h3>
            <PortfolioPieChart summary={portfolioSummary} />
          </div>
        ) : (
          <StockDetailPanel
            symbol={selectedCode}
            favorites={favorites}
            isAuthenticated={isAuthenticated}
            onFavoriteToggle={handleFavoriteToggle}
            onUpdateHolding={handleUpdateHolding}
          />
        )}
      </div>
    </div>
  )
}
