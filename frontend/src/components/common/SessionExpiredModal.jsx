import { useNavigate } from 'react-router-dom'

export default function SessionExpiredModal({ onClose }) {
  const navigate = useNavigate()

  const handleLogin = () => {
    onClose()
    navigate('/login')
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="w-80 rounded-xl bg-white p-6 shadow-xl">
        <h2 className="mb-2 text-lg font-semibold text-slate-900">로그인이 필요합니다</h2>
        <p className="mb-5 text-sm text-slate-500">
          세션이 만료되었거나 로그인되지 않았습니다.
        </p>
        <div className="flex gap-2">
          <button
            onClick={handleLogin}
            className="flex-1 rounded-lg bg-indigo-600 py-2 text-sm font-medium text-white hover:bg-indigo-700"
          >
            로그인
          </button>
          <button
            onClick={onClose}
            className="flex-1 rounded-lg bg-slate-100 py-2 text-sm text-slate-600 hover:bg-slate-200"
          >
            닫기
          </button>
        </div>
      </div>
    </div>
  )
}
