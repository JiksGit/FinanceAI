import { useEffect, useState } from 'react'
import { generateSignals, getMySignals, getRecentSignals } from '../api/signalApi'
import { useAuth } from '../hooks/useAuth'
import SignalCard from '../components/signal/SignalCard'
import LoadingSpinner from '../components/common/LoadingSpinner'
import ErrorMessage from '../components/common/ErrorMessage'

export default function SignalsPage() {
  const { isAuthenticated } = useAuth()
  const [tab, setTab] = useState('all')
  const [signals, setSignals] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [generating, setGenerating] = useState(false)

  const load = () => {
    setLoading(true)
    setError(null)
    const fetcher = tab === 'my' ? getMySignals : getRecentSignals
    fetcher()
      .then(setSignals)
      .catch(() => setError('시그널을 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }

  useEffect(load, [tab])

  const handleGenerate = async () => {
    setGenerating(true)
    try {
      await generateSignals()
      load()
    } catch {
      setError('시그널 생성 중 오류가 발생했습니다.')
    } finally {
      setGenerating(false)
    }
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-semibold text-slate-900">매매 시그널</h1>
        {isAuthenticated && (
          <button
            onClick={handleGenerate}
            disabled={generating}
            className="rounded-md bg-indigo-600 px-3 py-1.5 text-sm text-white hover:bg-indigo-700 disabled:opacity-50"
          >
            {generating ? '생성 중...' : '지금 시그널 확인'}
          </button>
        )}
      </div>

      <div className="mb-4 flex gap-2">
        <button
          onClick={() => setTab('all')}
          className={`rounded-md px-3 py-1.5 text-sm ${tab === 'all' ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-600'}`}
        >
          전체
        </button>
        {isAuthenticated && (
          <button
            onClick={() => setTab('my')}
            className={`rounded-md px-3 py-1.5 text-sm ${tab === 'my' ? 'bg-indigo-600 text-white' : 'bg-slate-100 text-slate-600'}`}
          >
            내 포트폴리오
          </button>
        )}
      </div>

      {loading && <LoadingSpinner />}
      {error && <ErrorMessage message={error} />}

      {!loading && !error && signals.length === 0 && (
        <p className="text-sm text-slate-400">
          아직 발생한 시그널이 없습니다. 5일/20일 이동평균선이 교차할 때 매수·매도 시그널이 생성됩니다.
        </p>
      )}

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {signals.map((signal, i) => (
          <SignalCard key={`${signal.stockSymbol}-${signal.signalDate}-${i}`} signal={signal} />
        ))}
      </div>
    </div>
  )
}
