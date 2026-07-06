import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import { usePriceAlerts } from '../../hooks/usePriceAlerts'
import AlertPanel from './AlertPanel'

export default function Navbar() {
  const { isAuthenticated, nickname, logout } = useAuth()
  const navigate = useNavigate()
  const { unreadCount, refresh } = usePriceAlerts()
  const [showAlerts, setShowAlerts] = useState(false)

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <nav className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3">
      <div className="flex items-center gap-6">
        <Link to="/" className="text-lg font-semibold text-indigo-600">
          AI Finance Dashboard
        </Link>
        <Link to="/" className="text-sm text-slate-600 hover:text-slate-900">환율</Link>
        <Link to="/stocks" className="text-sm text-slate-600 hover:text-slate-900">주식</Link>
        <Link to="/signals" className="text-sm text-slate-600 hover:text-slate-900">시그널</Link>
        <Link to="/correlation" className="text-sm text-slate-600 hover:text-slate-900">상관관계</Link>
        <Link to="/ai" className="text-sm text-slate-600 hover:text-slate-900">AI 분석</Link>
      </div>

      <div className="flex items-center gap-3">
        {isAuthenticated && (
          <div className="relative">
            <button
              onClick={() => setShowAlerts((v) => !v)}
              className="relative rounded-md p-1.5 text-slate-500 hover:bg-slate-100 transition-colors"
              title="목표가 알림"
            >
              <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" fill="none"
                viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
              </svg>
              {unreadCount > 0 && (
                <span className="absolute -right-0.5 -top-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-red-500 text-xs font-bold text-white">
                  {unreadCount > 9 ? '9+' : unreadCount}
                </span>
              )}
            </button>
            {showAlerts && (
              <AlertPanel
                onClose={() => setShowAlerts(false)}
                onReadAll={() => { refresh(); setShowAlerts(false) }}
              />
            )}
          </div>
        )}

        {isAuthenticated ? (
          <>
            <span className="text-sm text-slate-600">{nickname}님</span>
            <button
              onClick={handleLogout}
              className="rounded-md bg-slate-100 px-3 py-1.5 text-sm text-slate-700 hover:bg-slate-200"
            >
              로그아웃
            </button>
          </>
        ) : (
          <Link to="/login"
            className="rounded-md bg-indigo-600 px-3 py-1.5 text-sm text-white hover:bg-indigo-700">
            로그인
          </Link>
        )}
      </div>
    </nav>
  )
}
