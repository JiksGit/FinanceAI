import { useEffect, useState } from 'react'
import {
  addFavorite,
  getFavorites,
  getPortfolioSummary,
  getStock,
  getStockHistory,
  removeFavorite,
  searchStock,
  updateHolding,
} from '../api/stockApi'
import { useAuth } from '../hooks/useAuth'
import StockSearchBar from '../components/stock/StockSearchBar'
import StockChart from '../components/stock/StockChart'
import FavoriteStockList from '../components/stock/FavoriteStockList'
import PortfolioPieChart from '../components/stock/PortfolioPieChart'
import LoadingSpinner from '../components/common/LoadingSpinner'
import ErrorMessage from '../components/common/ErrorMessage'

function SearchResultRow({ result, onClick }) {
  return (
    <button
      onClick={() => onClick(result.symbol)}
      className="flex w-full items-center justify-between rounded-lg border border-slate-200 px-4 py-3 text-left hover:border-slate-300"
    >
      <span className="font-semibold text-slate-900">{result.symbol}</span>
      <span className="text-sm text-slate-500">{result.name}</span>
    </button>
  )
}

export default function StockPage() {
  const { isAuthenticated } = useAuth()
  const [results, setResults] = useState([])
  const [selected, setSelected] = useState(null)
  const [history, setHistory] = useState([])
  const [favorites, setFavorites] = useState([])
  const [portfolioSummary, setPortfolioSummary] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const loadFavorites = () => {
    if (!isAuthenticated) return
    getFavorites().then(setFavorites).catch(() => {})
    getPortfolioSummary().then(setPortfolioSummary).catch(() => {})
  }

  useEffect(loadFavorites, [isAuthenticated])

  const handleSearch = async (keyword) => {
    setError(null)
    try {
      const res = await searchStock(keyword)
      setResults(res)
    } catch {
      setError('종목 검색 중 오류가 발생했습니다.')
    }
  }

  const handleSelect = async (symbol) => {
    setError(null)
    setLoading(true)
    try {
      const [stock, hist] = await Promise.all([getStock(symbol), getStockHistory(symbol, 30)])
      setSelected(stock)
      setHistory(hist.history)
    } catch {
      setError('종목 정보를 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }

  const handleToggleFavorite = async (symbol) => {
    if (!isAuthenticated) {
      setError('로그인이 필요합니다.')
      return
    }
    const isFav = favorites.some((f) => f.stockSymbol === symbol)
    try {
      if (isFav) {
        await removeFavorite(symbol)
      } else {
        await addFavorite(symbol, selected?.name)
      }
      loadFavorites()
    } catch {
      setError('즐겨찾기 처리 중 오류가 발생했습니다.')
    }
  }

  const handleUpdateHolding = async (symbol, quantity, avgPrice) => {
    try {
      await updateHolding(symbol, quantity, avgPrice)
      loadFavorites()
    } catch {
      setError('보유정보 저장 중 오류가 발생했습니다.')
    }
  }

  return (
    <div className="grid grid-cols-1 gap-8 lg:grid-cols-3">
      <div className="space-y-4 lg:col-span-2">
        <StockSearchBar onSearch={handleSearch} />
        {error && <ErrorMessage message={error} />}

        <div className="space-y-2">
          {results.map((result) => (
            <SearchResultRow key={result.symbol} result={result} onClick={handleSelect} />
          ))}
        </div>

        {loading && <LoadingSpinner />}

        {selected && !loading && (
          <div>
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-lg font-semibold text-slate-900">{selected.symbol} 시세 추이</h2>
              <button
                onClick={() => handleToggleFavorite(selected.symbol)}
                className="text-sm text-indigo-600 hover:underline"
              >
                {favorites.some((f) => f.stockSymbol === selected.symbol) ? '즐겨찾기 해제' : '즐겨찾기 추가'}
              </button>
            </div>
            <p className="mb-3 text-sm text-slate-500">
              ${selected.price?.toFixed(2)}{' '}
              <span className={selected.change > 0 ? 'text-red-600' : selected.change < 0 ? 'text-blue-600' : ''}>
                {selected.change > 0 ? '+' : ''}
                {selected.change?.toFixed(2)} ({selected.changeRate?.toFixed(2)}%)
              </span>
            </p>
            <StockChart history={history} />
          </div>
        )}
      </div>

      <div className="space-y-6">
        <div>
          <h2 className="mb-3 text-lg font-semibold text-slate-900">포트폴리오</h2>
          <FavoriteStockList
            favorites={favorites}
            onSelect={handleSelect}
            onRemove={handleToggleFavorite}
            onUpdateHolding={handleUpdateHolding}
          />
        </div>
        {isAuthenticated && (
          <div>
            <h2 className="mb-3 text-lg font-semibold text-slate-900">종목 비중</h2>
            <PortfolioPieChart summary={portfolioSummary} />
          </div>
        )}
      </div>
    </div>
  )
}
