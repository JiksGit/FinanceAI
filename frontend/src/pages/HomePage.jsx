import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useExchangeRate } from '../hooks/useExchangeRate'
import { getFavorites } from '../api/stockApi'
import { getRecentSignals } from '../api/signalApi'
import { useAuth } from '../hooks/useAuth'
import ExchangeRateChart from '../components/exchange/ExchangeRateChart'
import MarketTopTable from '../components/stock/MarketTopTable'
import MarketIndexWidget from '../components/stock/MarketIndexWidget'
import { getRateHistory } from '../api/exchangeApi'
import LoadingSpinner from '../components/common/LoadingSpinner'

// ── 요약 카드 ──────────────────────────────────────────────
function SummaryCard({ title, value, sub, accent }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5">
      <p className="text-xs font-medium text-slate-400">{title}</p>
      <p className={`mt-1 text-2xl font-bold ${accent ?? 'text-slate-900'}`}>{value}</p>
      {sub && <p className="mt-0.5 text-xs text-slate-400">{sub}</p>}
    </div>
  )
}

// ── 포트폴리오 행 ──────────────────────────────────────────
function PortfolioRow({ fav }) {
  const hasHolding = fav.quantity && fav.quantity > 0
  const isProfit = fav.profitLoss > 0
  const isLoss = fav.profitLoss < 0

  return (
    <div className="flex items-center justify-between py-2.5">
      <div>
        <span className="text-sm font-medium text-slate-800">
          {fav.stockName || fav.stockSymbol}
        </span>
        <span className="ml-2 text-xs text-slate-400 font-mono">{fav.stockSymbol}</span>
      </div>
      <div className="text-right">
        {fav.currentPrice != null && (
          <p className="text-sm font-semibold text-slate-800">
            ₩{Number(fav.currentPrice).toLocaleString('ko-KR')}
          </p>
        )}
        {hasHolding && fav.profitLoss != null && (
          <p className={`text-xs ${isProfit ? 'text-red-500' : isLoss ? 'text-blue-500' : 'text-slate-400'}`}>
            {isProfit ? '+' : ''}₩{Number(fav.profitLoss).toLocaleString('ko-KR')}
            {' '}({fav.profitLossRate >= 0 ? '+' : ''}{Number(fav.profitLossRate).toFixed(2)}%)
          </p>
        )}
        {!hasHolding && <p className="text-xs text-slate-300">보유 없음</p>}
      </div>
    </div>
  )
}

// ── 시그널 뱃지 행 ─────────────────────────────────────────
function SignalRow({ signal }) {
  const isBuy = signal.signalType === 'BUY'
  return (
    <div className="flex items-center justify-between py-2.5">
      <div>
        <span className="text-sm font-medium text-slate-800">
          {signal.stockName || signal.stockSymbol}
        </span>
        <span className="ml-2 text-xs text-slate-400 font-mono">{signal.stockSymbol}</span>
        <span className="ml-2 text-xs text-slate-400">{signal.signalDate}</span>
      </div>
      <span
        className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
          isBuy ? 'bg-red-50 text-red-600' : 'bg-blue-50 text-blue-600'
        }`}
      >
        {isBuy ? '매수' : '매도'}
      </span>
    </div>
  )
}

// ── 환율 뱃지 행 ──────────────────────────────────────────
function ExchangeRow({ rate }) {
  const isUp = rate.change > 0
  const isDown = rate.change < 0
  return (
    <div className="flex items-center justify-between py-2.5">
      <span className="text-sm font-medium text-slate-800">{rate.currency}</span>
      <div className="text-right">
        <span className="text-sm font-semibold text-slate-800">
          {Number(rate.rate).toLocaleString()}
        </span>
        <span
          className={`ml-1.5 text-xs ${
            isUp ? 'text-red-500' : isDown ? 'text-blue-500' : 'text-slate-400'
          }`}
        >
          {isUp ? '▲' : isDown ? '▼' : '─'} {Math.abs(rate.change ?? 0).toFixed(2)}
        </span>
      </div>
    </div>
  )
}

// ── 메인 대시보드 ──────────────────────────────────────────
export default function HomePage() {
  const { isAuthenticated, nickname } = useAuth()
  const { data: exchangeData, loading: exchangeLoading } = useExchangeRate()

  const [favorites, setFavorites] = useState([])
  const [signals, setSignals] = useState([])
  const [chartHistory, setChartHistory] = useState([])
  const [chartCurrency, setChartCurrency] = useState('USD')

  useEffect(() => {
    if (isAuthenticated) {
      getFavorites().then(setFavorites).catch(() => {})
    }
  }, [isAuthenticated])

  useEffect(() => {
    getRecentSignals().then(setSignals).catch(() => {})
  }, [])

  useEffect(() => {
    getRateHistory(chartCurrency, 30)
      .then((res) => setChartHistory(res.history))
      .catch(() => {})
  }, [chartCurrency])

  // 포트폴리오 총 손익 계산
  const portfolioStats = favorites.reduce(
    (acc, fav) => {
      if (fav.quantity && fav.quantity > 0 && fav.profitLoss != null) {
        acc.totalPnL += fav.profitLoss
        acc.totalInvest += fav.avgPrice * fav.quantity
        acc.count++
      }
      return acc
    },
    { totalPnL: 0, totalInvest: 0, count: 0 }
  )

  const pnLRate =
    portfolioStats.totalInvest > 0
      ? (portfolioStats.totalPnL / portfolioStats.totalInvest) * 100
      : null

  const latestBuySignals = signals.filter((s) => s.signalType === 'BUY').length
  const latestSellSignals = signals.filter((s) => s.signalType === 'SELL').length

  const usdRate = exchangeData?.rates?.find((r) => r.currency === 'USD')

  const DISPLAY_CURRENCIES = ['USD', 'EUR', 'JPY', 'CNY']
  const topRates = exchangeData?.rates?.filter((r) => DISPLAY_CURRENCIES.includes(r.currency)) ?? []

  return (
    <div className="space-y-8">

      {/* 인사 */}
      <div>
        <h1 className="text-xl font-semibold text-slate-900">
          {isAuthenticated ? `안녕하세요, ${nickname}님 👋` : '오늘의 금융 대시보드'}
        </h1>
        <p className="mt-0.5 text-sm text-slate-400">
          {new Date().toLocaleDateString('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }).replace(/\. /g, '-').replace('.', '')} 기준
        </p>
      </div>

      {/* 국내 증시 지수 위젯 */}
      <MarketIndexWidget />

      {/* 요약 카드 3개 */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        {/* 포트폴리오 손익 */}
        {isAuthenticated ? (
          portfolioStats.count > 0 ? (
            <SummaryCard
              title="포트폴리오 총 손익"
              value={`${portfolioStats.totalPnL >= 0 ? '+' : ''}$${portfolioStats.totalPnL.toFixed(2)}`}
              sub={pnLRate != null ? `수익률 ${pnLRate >= 0 ? '+' : ''}${pnLRate.toFixed(2)}%` : undefined}
              accent={portfolioStats.totalPnL > 0 ? 'text-red-500' : portfolioStats.totalPnL < 0 ? 'text-blue-500' : 'text-slate-900'}
            />
          ) : (
            <SummaryCard
              title="포트폴리오 총 손익"
              value="–"
              sub="주식 페이지에서 보유정보를 입력하세요"
            />
          )
        ) : (
          <SummaryCard
            title="포트폴리오 총 손익"
            value="로그인 필요"
            sub="로그인하면 내 포트폴리오를 볼 수 있어요"
          />
        )}

        {/* 오늘 USD 환율 */}
        <SummaryCard
          title="오늘 USD/KRW"
          value={usdRate ? `₩${Number(usdRate.rate).toLocaleString()}` : '–'}
          sub={
            usdRate?.change != null
              ? `전일 대비 ${usdRate.change >= 0 ? '+' : ''}${usdRate.change.toFixed(2)}`
              : undefined
          }
          accent={
            usdRate?.change > 0
              ? 'text-red-500'
              : usdRate?.change < 0
              ? 'text-blue-500'
              : 'text-slate-900'
          }
        />

        {/* 최신 시그널 */}
        <SummaryCard
          title="최신 시그널"
          value={signals.length > 0 ? `${signals.length}건` : '없음'}
          sub={
            signals.length > 0
              ? `매수 ${latestBuySignals}건 · 매도 ${latestSellSignals}건`
              : '아직 발생한 시그널이 없습니다'
          }
        />
      </div>

      {/* 중단: 포트폴리오 + 시그널 */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">

        {/* 관심종목 */}
        <div className="rounded-xl border border-slate-200 bg-white p-5">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-slate-700">관심종목 포트폴리오</h2>
            <Link to="/stocks" className="text-xs text-indigo-500 hover:underline">
              전체 보기 →
            </Link>
          </div>
          {!isAuthenticated ? (
            <p className="py-4 text-center text-sm text-slate-400">
              <Link to="/login" className="text-indigo-500 hover:underline">
                로그인
              </Link>
              하면 관심종목을 볼 수 있어요.
            </p>
          ) : favorites.length === 0 ? (
            <p className="py-4 text-center text-sm text-slate-400">
              관심종목이 없습니다.{' '}
              <Link to="/stocks" className="text-indigo-500 hover:underline">
                종목 추가하기
              </Link>
            </p>
          ) : (
            <div className="divide-y divide-slate-100">
              {favorites.slice(0, 5).map((fav) => (
                <PortfolioRow key={fav.stockSymbol} fav={fav} />
              ))}
              {favorites.length > 5 && (
                <p className="pt-2 text-center text-xs text-slate-400">
                  외 {favorites.length - 5}개 종목
                </p>
              )}
            </div>
          )}
        </div>

        {/* 최신 시그널 */}
        <div className="rounded-xl border border-slate-200 bg-white p-5">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-slate-700">최신 매매 시그널</h2>
            <Link to="/signals" className="text-xs text-indigo-500 hover:underline">
              전체 보기 →
            </Link>
          </div>
          {signals.length === 0 ? (
            <p className="py-4 text-center text-sm text-slate-400">발생한 시그널이 없습니다.</p>
          ) : (
            <div className="divide-y divide-slate-100">
              {signals.slice(0, 5).map((signal, i) => (
                <SignalRow key={`${signal.stockSymbol}-${signal.signalDate}-${i}`} signal={signal} />
              ))}
            </div>
          )}
        </div>
      </div>

      {/* 시가총액 TOP 50 */}
      <div className="rounded-xl border border-slate-200 bg-white p-5">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-700">시가총액 TOP 50</h2>
          <Link to="/stocks" className="text-xs text-indigo-500 hover:underline">
            종목 검색 →
          </Link>
        </div>
        <MarketTopTable limit={50} linkToDetail />
      </div>

      {/* 하단: 환율 */}
      <div className="rounded-xl border border-slate-200 bg-white p-5">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-700">환율 현황</h2>
          <div className="flex gap-1">
            {DISPLAY_CURRENCIES.map((cur) => (
              <button
                key={cur}
                onClick={() => setChartCurrency(cur)}
                className={`rounded px-2.5 py-1 text-xs ${
                  chartCurrency === cur
                    ? 'bg-indigo-600 text-white'
                    : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                }`}
              >
                {cur}
              </button>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4 mb-5">
          {exchangeLoading
            ? Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="h-12 animate-pulse rounded-lg bg-slate-100" />
              ))
            : topRates.map((rate) => (
                <ExchangeRow key={rate.currency} rate={rate} />
              ))}
        </div>

        {chartHistory.length > 0 && (
          <>
            <p className="mb-2 text-xs text-slate-400">{chartCurrency} 30일 추이</p>
            <ExchangeRateChart history={chartHistory} />
          </>
        )}
      </div>
    </div>
  )
}
