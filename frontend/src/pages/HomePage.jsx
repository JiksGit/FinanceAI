import { useEffect, useState } from 'react'
import { useExchangeRate } from '../hooks/useExchangeRate'
import { getRateHistory } from '../api/exchangeApi'
import ExchangeRateCard from '../components/exchange/ExchangeRateCard'
import ExchangeRateChart from '../components/exchange/ExchangeRateChart'
import LoadingSpinner from '../components/common/LoadingSpinner'
import ErrorMessage from '../components/common/ErrorMessage'

export default function HomePage() {
  const { data, loading, error } = useExchangeRate()
  const [selectedCurrency, setSelectedCurrency] = useState(null)
  const [history, setHistory] = useState([])
  const [historyLoading, setHistoryLoading] = useState(false)

  useEffect(() => {
    if (data?.rates?.length && !selectedCurrency) {
      setSelectedCurrency(data.rates[0].currency)
    }
  }, [data, selectedCurrency])

  useEffect(() => {
    if (!selectedCurrency) return
    setHistoryLoading(true)
    getRateHistory(selectedCurrency, 30)
      .then((res) => setHistory(res.history))
      .finally(() => setHistoryLoading(false))
  }, [selectedCurrency])

  if (loading) return <LoadingSpinner />
  if (error) return <ErrorMessage message="환율 데이터를 불러오지 못했습니다." />

  return (
    <div className="space-y-8">
      <div>
        <h1 className="mb-1 text-xl font-semibold text-slate-900">오늘의 환율</h1>
        <p className="mb-4 text-sm text-slate-400">기준일: {data.baseDate}</p>
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          {data.rates.map((rate) => (
            <ExchangeRateCard
              key={rate.currency}
              rate={rate}
              selected={rate.currency === selectedCurrency}
              onSelect={setSelectedCurrency}
            />
          ))}
        </div>
      </div>

      <div>
        <h2 className="mb-3 text-lg font-semibold text-slate-900">
          {selectedCurrency} 30일 추이
        </h2>
        {historyLoading ? <LoadingSpinner /> : <ExchangeRateChart history={history} />}
      </div>
    </div>
  )
}
