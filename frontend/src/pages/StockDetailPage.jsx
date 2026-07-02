import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMarketPrices } from '../hooks/useMarketPrices'
import {
  addFavorite,
  getFavorites,
  getStockDetail,
  getStockHistory,
  getStockNews,
  removeFavorite,
  updateHolding,
} from '../api/stockApi'
import { useAuth } from '../hooks/useAuth'
import StockChart from '../components/stock/StockChart'
import LoadingSpinner from '../components/common/LoadingSpinner'
import { formatMarketCap, priceColor } from '../components/stock/MarketTopTable'

// ── 지표 카드 ─────────────────────────────────────────────
function MetricCard({ label, value, unit = '', sub }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3">
      <p className="text-xs text-slate-400">{label}</p>
      <p className="mt-1 text-base font-bold text-slate-800 tabular-nums">
        {value != null ? (
          <>{Number(value).toLocaleString('ko-KR')}<span className="ml-0.5 text-xs font-normal text-slate-500">{unit}</span></>
        ) : (
          <span className="text-slate-300">–</span>
        )}
      </p>
      {sub && <p className="mt-0.5 text-xs text-slate-400">{sub}</p>}
    </div>
  )
}

// ── 시세 행 ───────────────────────────────────────────────
function PriceRow({ label, value, color }) {
  return (
    <div className="flex items-center justify-between py-1.5 border-b border-slate-100 last:border-0">
      <span className="text-xs text-slate-400">{label}</span>
      <span className={`text-sm font-semibold tabular-nums ${color || 'text-slate-700'}`}>
        {value != null && value !== 0
          ? Number(value).toLocaleString('ko-KR') + '원'
          : <span className="text-slate-300 font-normal">–</span>}
      </span>
    </div>
  )
}

// ── 52주 레인지 바 ────────────────────────────────────────
function RangeBar({ current, low, high }) {
  if (!low || !high || low === high) return null
  const pct = Math.max(0, Math.min(100, ((current - low) / (high - low)) * 100))
  return (
    <div>
      <div className="flex justify-between text-xs text-slate-400 mb-1">
        <span>52주 최저 ₩{Number(low).toLocaleString('ko-KR')}</span>
        <span>52주 최고 ₩{Number(high).toLocaleString('ko-KR')}</span>
      </div>
      <div className="relative h-2 rounded-full bg-slate-200">
        <div className="h-full rounded-full bg-gradient-to-r from-blue-400 to-red-400"
          style={{ width: `${pct}%` }} />
        <div className="absolute top-1/2 -translate-y-1/2 -translate-x-1/2 h-3.5 w-3.5 rounded-full bg-white border-2 border-slate-500 shadow"
          style={{ left: `${pct}%` }} />
      </div>
      <p className="mt-1 text-center text-xs text-slate-500">
        현재가 위치: 하단 대비 <span className="font-medium text-slate-700">{pct.toFixed(1)}%</span>
      </p>
    </div>
  )
}

// ── 차트 기간 선택 ────────────────────────────────────────
const PERIODS = [
  { label: '1개월', days: 30 },
  { label: '3개월', days: 90 },
  { label: '6개월', days: 180 },
  { label: '1년', days: 365 },
]

// ── 메인 페이지 ───────────────────────────────────────────
export default function StockDetailPage() {
  const { code } = useParams()
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()

  const [detail, setDetail] = useState(null)
  const [history, setHistory] = useState([])
  const [news, setNews] = useState([])
  const [favorites, setFavorites] = useState([])
  const [period, setPeriod] = useState(30)
  const [loading, setLoading] = useState(true)
  const [histLoading, setHistLoading] = useState(false)
  const [error, setError] = useState(null)
  const { prices: livePrices, connected } = useMarketPrices()

  useEffect(() => {
    if (isAuthenticated) getFavorites().then(setFavorites).catch(() => {})
  }, [isAuthenticated])

  useEffect(() => {
    setLoading(true)
    setError(null)
    Promise.all([
      getStockDetail(code),
      getStockHistory(code, 30).catch(() => ({ history: [] })),
      getStockNews(code).catch(() => ({ news: [] })),
    ])
      .then(([d, h, n]) => {
        setDetail(d)
        setHistory(h.history ?? [])
        setNews(n.news ?? [])
      })
      .catch(() => setError('종목 정보를 불러올 수 없습니다.'))
      .finally(() => setLoading(false))
  }, [code])

  const handlePeriodChange = async (days) => {
    setPeriod(days)
    setHistLoading(true)
    try {
      const h = await getStockHistory(code, days)
      setHistory(h.history ?? [])
    } catch {}
    setHistLoading(false)
  }

  const isFav = favorites.some((f) => f.stockSymbol === code)

  const handleFavToggle = async () => {
    try {
      if (isFav) await removeFavorite(code)
      else await addFavorite(code, detail?.stockName)
      if (isAuthenticated) getFavorites().then(setFavorites).catch(() => {})
    } catch {}
  }

  if (loading) return (
    <div className="flex items-center justify-center py-32">
      <LoadingSpinner />
    </div>
  )

  if (error || !detail) return (
    <div className="py-20 text-center">
      <p className="text-slate-400">{error || '종목을 찾을 수 없습니다.'}</p>
      <button onClick={() => navigate('/stocks')} className="mt-4 text-indigo-500 hover:underline text-sm">
        ← 목록으로
      </button>
    </div>
  )

  // 실시간 가격 오버레이
  const live = livePrices.get(code)
  const currentPrice = live?.closePrice ?? detail.closePrice
  const change = live?.priceChange ?? detail.priceChange
  const changeRate = Number(live?.changeRate ?? detail.changeRate)
  const isUp = change > 0
  const isDown = change < 0

  return (
    <div className="mx-auto max-w-4xl space-y-5">

      {/* ── 상단 네비 ── */}
      <div className="flex items-center gap-2">
        <button onClick={() => navigate('/stocks')}
          className="flex items-center gap-1 text-sm text-slate-400 hover:text-slate-700">
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
          목록
        </button>
        <span className="text-slate-200">/</span>
        <span className="text-sm text-slate-500">{detail.stockName}</span>
      </div>

      {/* ── 헤더: 종목명 + 현재가 ── */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6">
        <div className="flex items-start justify-between gap-4">
          <div className="flex-1">
            {/* 종목 정보 */}
            <div className="flex flex-wrap items-center gap-2 mb-1">
              <h1 className="text-2xl font-bold text-slate-900">{detail.stockName}</h1>
              <span className={`rounded px-2 py-0.5 text-xs font-medium ${
                detail.market === 'KOSPI' ? 'bg-blue-50 text-blue-600' : 'bg-green-50 text-green-600'
              }`}>{detail.market}</span>
              {detail.sector && (
                <span className="rounded px-2 py-0.5 text-xs bg-slate-100 text-slate-500">
                  {detail.sector}
                </span>
              )}
            </div>
            <p className="font-mono text-sm text-slate-400">{detail.stockCode}</p>

            {/* 현재가 */}
            <div className="mt-4">
              <div className="flex items-center gap-2">
                <p className={`text-4xl font-bold tabular-nums ${priceColor(change)}`}>
                  ₩{Number(currentPrice).toLocaleString('ko-KR')}
                </p>
                <span className={`flex items-center gap-1 text-xs ${connected ? 'text-green-500' : 'text-slate-300'}`}>
                  <span className={`inline-block h-1.5 w-1.5 rounded-full ${connected ? 'bg-green-500 animate-pulse' : 'bg-slate-300'}`} />
                  {connected ? '실시간' : '연결 중'}
                </span>
              </div>
              <div className={`mt-1.5 flex items-center gap-3 text-base ${priceColor(change)}`}>
                <span className="font-semibold">
                  {isUp ? '▲' : isDown ? '▼' : '–'}{' '}
                  {Math.abs(change).toLocaleString('ko-KR')}원
                </span>
                <span className={`rounded-md px-2 py-0.5 text-sm font-bold ${
                  isUp ? 'bg-red-50' : isDown ? 'bg-blue-50' : 'bg-slate-100'
                }`}>
                  {isUp ? '+' : ''}{changeRate.toFixed(2)}%
                </span>
              </div>
            </div>
            </div>
          </div>

          {/* 즐겨찾기 + 시가총액 */}
          <div className="flex flex-col items-end gap-3">
            {isAuthenticated && (
              <button onClick={handleFavToggle}
                className={`flex items-center gap-1.5 rounded-xl px-4 py-2 text-sm font-medium transition-colors ${
                  isFav
                    ? 'bg-yellow-400 text-white hover:bg-yellow-500'
                    : 'border border-slate-300 text-slate-600 hover:bg-slate-50'
                }`}>
                <span className="text-base">{isFav ? '★' : '☆'}</span>
                {isFav ? '관심종목' : '추가'}
              </button>
            )}
            {detail.marketCap > 0 && (
              <div className="text-right">
                <p className="text-xs text-slate-400">시가총액</p>
                <p className="text-lg font-bold text-slate-700">{formatMarketCap(detail.marketCap)}</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* ── 오늘 시세 + 투자지표 2단 ── */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">

        {/* 오늘 시세 */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5">
          <h2 className="mb-3 text-sm font-semibold text-slate-600">오늘 시세</h2>
          <PriceRow label="시가" value={detail.openPrice} color={detail.openPrice > detail.closePrice ? 'text-red-500' : 'text-blue-500'} />
          <PriceRow label="고가" value={detail.highPrice} color="text-red-500" />
          <PriceRow label="저가" value={detail.lowPrice} color="text-blue-500" />
          <div className="flex items-center justify-between py-1.5">
            <span className="text-xs text-slate-400">거래량</span>
            <span className="text-sm font-semibold text-slate-700 tabular-nums">
              {Number(detail.volume).toLocaleString('ko-KR')}주
            </span>
          </div>
          {detail.sharesOutstanding > 0 && (
            <div className="flex items-center justify-between py-1.5 border-t border-slate-100">
              <span className="text-xs text-slate-400">상장주식수</span>
              <span className="text-sm font-semibold text-slate-700 tabular-nums">
                {Number(detail.sharesOutstanding).toLocaleString('ko-KR')}주
              </span>
            </div>
          )}
          {detail.foreignRatio != null && (
            <div className="flex items-center justify-between py-1.5 border-t border-slate-100">
              <span className="text-xs text-slate-400">외국인소진률</span>
              <span className="text-sm font-semibold text-slate-700 tabular-nums">
                {Number(detail.foreignRatio).toFixed(2)}%
              </span>
            </div>
          )}
        </div>

        {/* 투자지표 */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5">
          <h2 className="mb-3 text-sm font-semibold text-slate-600">투자지표</h2>
          <div className="grid grid-cols-2 gap-2">
            <MetricCard label="PER" value={detail.per} unit="배" sub="주가수익비율" />
            <MetricCard label="EPS" value={detail.eps} unit="원" sub="주당순이익" />
            <MetricCard label="PBR" value={detail.pbr} unit="배" sub="주가순자산비율" />
            <MetricCard label="BPS" value={detail.bps} unit="원" sub="주당순자산" />
            <MetricCard label="ROE" value={detail.roe} unit="%" sub="자기자본이익률" />
            <MetricCard label="배당수익률" value={detail.dividendYield} unit="%" sub="연간 배당" />
          </div>
        </div>
      </div>

      {/* ── 52주 레인지 ── */}
      {detail.high52w > 0 && detail.low52w > 0 && (
        <div className="rounded-2xl border border-slate-200 bg-white p-5">
          <h2 className="mb-4 text-sm font-semibold text-slate-600">52주 가격 범위</h2>
          <RangeBar current={detail.closePrice} low={detail.low52w} high={detail.high52w} />
        </div>
      )}

      {/* ── 차트 ── */}
      <div className="rounded-2xl border border-slate-200 bg-white p-5">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-600">시세 추이</h2>
          <div className="flex gap-1">
            {PERIODS.map((p) => (
              <button key={p.days} onClick={() => handlePeriodChange(p.days)}
                className={`rounded-md px-3 py-1 text-xs font-medium transition-colors ${
                  period === p.days
                    ? 'bg-slate-800 text-white'
                    : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                }`}>
                {p.label}
              </button>
            ))}
          </div>
        </div>
        {histLoading ? (
          <div className="flex justify-center py-8"><LoadingSpinner /></div>
        ) : history.length > 1 ? (
          <StockChart history={history} />
        ) : (
          <p className="py-8 text-center text-sm text-slate-400">차트 데이터를 불러오는 중...</p>
        )}
      </div>

      {/* ── 뉴스 ── */}
      {news.length > 0 && (
        <div className="rounded-2xl border border-slate-200 bg-white p-5">
          <h2 className="mb-4 text-sm font-semibold text-slate-600">관련 뉴스</h2>
          <div className="space-y-3">
            {news.map((item, i) => (
              <a key={i} href={item.url} target="_blank" rel="noopener noreferrer"
                className="flex items-start justify-between gap-3 rounded-xl border border-slate-100 p-3.5 hover:bg-slate-50 transition-colors">
                <div className="flex-1">
                  <p className="text-sm font-medium text-slate-800 leading-snug">{item.title}</p>
                  <p className="mt-1 text-xs text-slate-500 line-clamp-2">{item.summary}</p>
                  <p className="mt-1.5 text-xs text-slate-400">{item.source} · {item.publishedAt}</p>
                </div>
                <span className={`shrink-0 rounded-lg px-2.5 py-1 text-xs font-bold ${
                  item.sentiment === 'Bullish'
                    ? 'bg-red-50 text-red-500'
                    : item.sentiment === 'Bearish'
                    ? 'bg-blue-50 text-blue-500'
                    : 'bg-slate-100 text-slate-500'
                }`}>
                  {item.sentiment === 'Bullish' ? '긍정' : item.sentiment === 'Bearish' ? '부정' : '중립'}
                </span>
              </a>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
