export default function NewsCard({ item }) {
  const sentimentStyle =
    item.sentiment === 'Bullish'
      ? 'bg-red-50 text-red-500'
      : item.sentiment === 'Bearish'
      ? 'bg-blue-50 text-blue-500'
      : 'bg-slate-100 text-slate-500'

  return (
    <a
      href={item.url}
      target="_blank"
      rel="noopener noreferrer"
      className="block rounded-lg border border-slate-200 p-4 hover:border-indigo-300 hover:shadow-sm transition-all"
    >
      <div className="flex items-start justify-between gap-2">
        <p className="text-sm font-medium text-slate-800 leading-snug">{item.title}</p>
        <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${sentimentStyle}`}>
          {item.sentiment === 'Bullish' ? '긍정' : item.sentiment === 'Bearish' ? '부정' : '중립'}
        </span>
      </div>
      <p className="mt-1.5 text-xs text-slate-400 line-clamp-2">{item.summary}</p>
      <div className="mt-2 flex items-center gap-2 text-xs text-slate-300">
        <span>{item.source}</span>
        <span>·</span>
        <span>{item.timePublished}</span>
      </div>
    </a>
  )
}
