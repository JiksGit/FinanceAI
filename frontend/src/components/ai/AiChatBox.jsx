import { useState } from 'react'
import { analyze } from '../../api/aiApi'
import AiChatMessage from './AiChatMessage'
import AiContextToggle from './AiContextToggle'
import LoadingSpinner from '../common/LoadingSpinner'
import ErrorMessage from '../common/ErrorMessage'

export default function AiChatBox() {
  const [messages, setMessages] = useState([])
  const [question, setQuestion] = useState('')
  const [context, setContext] = useState({ includeExchangeRate: true, includeStock: false })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!question.trim() || loading) return

    const userMessage = { role: 'user', content: question }
    setMessages((prev) => [...prev, userMessage])
    setQuestion('')
    setLoading(true)
    setError(null)

    try {
      const res = await analyze(userMessage.content, context.includeExchangeRate, context.includeStock)
      setMessages((prev) => [...prev, { role: 'assistant', content: res.answer }])
    } catch (err) {
      setError(err.response?.data?.message || 'AI 분석 중 오류가 발생했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex h-full flex-col gap-4">
      <AiContextToggle
        includeExchangeRate={context.includeExchangeRate}
        includeStock={context.includeStock}
        onChange={setContext}
      />

      <div className="flex-1 space-y-3 overflow-y-auto rounded-lg border border-slate-200 p-4">
        {messages.length === 0 && (
          <p className="text-sm text-slate-400">환율/주식 데이터를 바탕으로 질문해보세요.</p>
        )}
        {messages.map((m, i) => (
          <AiChatMessage key={i} role={m.role} content={m.content} />
        ))}
        {loading && <LoadingSpinner />}
        {error && <ErrorMessage message={error} />}
      </div>

      <form onSubmit={handleSubmit} className="flex gap-2">
        <input
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="예: 요즘 달러 강세인 이유가 뭐야?"
          className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none"
        />
        <button
          type="submit"
          disabled={loading}
          className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
        >
          질문
        </button>
      </form>
    </div>
  )
}
