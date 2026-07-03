import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'

export default function Navbar() {
  const { isAuthenticated, nickname, logout } = useAuth()
  const navigate = useNavigate()

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
        <Link to="/" className="text-sm text-slate-600 hover:text-slate-900">
          환율
        </Link>
        <Link to="/stocks" className="text-sm text-slate-600 hover:text-slate-900">
          주식
        </Link>
        <Link to="/signals" className="text-sm text-slate-600 hover:text-slate-900">
          시그널
        </Link>
        <Link to="/correlation" className="text-sm text-slate-600 hover:text-slate-900">
          상관관계
        </Link>
        <Link to="/ai" className="text-sm text-slate-600 hover:text-slate-900">
          AI 분석
        </Link>
      </div>

      <div className="flex items-center gap-3">
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
          <Link
            to="/login"
            className="rounded-md bg-indigo-600 px-3 py-1.5 text-sm text-white hover:bg-indigo-700"
          >
            로그인
          </Link>
        )}
      </div>
    </nav>
  )
}
