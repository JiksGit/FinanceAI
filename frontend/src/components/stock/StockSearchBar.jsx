import { useState } from 'react'

export default function StockSearchBar({ onSearch }) {
  const [keyword, setKeyword] = useState('')

  const handleSubmit = (e) => {
    e.preventDefault()
    if (keyword.trim()) onSearch(keyword.trim())
  }

  return (
    <form onSubmit={handleSubmit} className="flex gap-2">
      <input
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        placeholder="종목명 또는 심볼 검색 (예: Apple, AAPL)"
        className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none"
      />
      <button
        type="submit"
        className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
      >
        검색
      </button>
    </form>
  )
}
